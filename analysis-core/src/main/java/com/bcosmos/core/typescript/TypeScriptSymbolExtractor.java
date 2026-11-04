package com.bcosmos.core.typescript;

import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.Symbol;
import com.bcosmos.core.model.SymbolKind;
import com.bcosmos.core.quality.Fingerprint;
import org.treesitter.TSNode;
import org.treesitter.TSTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts TypeScript classes, interfaces, enums, methods, constructors and fields from a Tree-sitter AST
 * (support level 2, spec v2 section 3.1).
 */
public final class TypeScriptSymbolExtractor {

    private TypeScriptSymbolExtractor() {
    }

    public static List<Symbol> extract(TSTree tree, String source) {
        if (tree == null || source == null) {
            return List.of();
        }
        List<Symbol> symbols = new ArrayList<>();
        TSNode root = tree.getRootNode();
        visitNode(root, null, source, symbols);
        return List.copyOf(symbols);
    }

    private static void visitNode(TSNode node, String parentQualifiedName, String source, List<Symbol> symbols) {
        if (node == null) {
            return;
        }

        String type = node.getType();
        switch (type) {
            case "class_declaration" -> {
                String className = findChildName(node, source, "type_identifier", "identifier");
                if (className != null && !className.isBlank()) {
                    String qualifiedName = parentQualifiedName == null ? className : parentQualifiedName + "." + className;
                    symbols.add(new Symbol(
                            SymbolKind.CLASS,
                            className,
                            qualifiedName,
                            parentQualifiedName,
                            spanOf(node),
                            signatureHash("CLASS " + qualifiedName)));

                    TSNode body = findChildByType(node, "class_body");
                    if (body != null) {
                        for (int i = 0; i < body.getNamedChildCount(); i++) {
                            visitClassMember(body.getNamedChild(i), qualifiedName, source, symbols);
                        }
                    }
                }
                return;
            }
            case "interface_declaration" -> {
                String interfaceName = findChildName(node, source, "type_identifier", "identifier");
                if (interfaceName != null && !interfaceName.isBlank()) {
                    String qualifiedName = parentQualifiedName == null ? interfaceName : parentQualifiedName + "." + interfaceName;
                    symbols.add(new Symbol(
                            SymbolKind.INTERFACE,
                            interfaceName,
                            qualifiedName,
                            parentQualifiedName,
                            spanOf(node),
                            signatureHash("INTERFACE " + qualifiedName)));

                    TSNode body = findChildByType(node, "interface_body");
                    if (body != null) {
                        for (int i = 0; i < body.getNamedChildCount(); i++) {
                            visitInterfaceMember(body.getNamedChild(i), qualifiedName, source, symbols);
                        }
                    }
                }
                return;
            }
            case "enum_declaration" -> {
                String enumName = findChildName(node, source, "identifier");
                if (enumName != null && !enumName.isBlank()) {
                    String qualifiedName = parentQualifiedName == null ? enumName : parentQualifiedName + "." + enumName;
                    symbols.add(new Symbol(
                            SymbolKind.ENUM,
                            enumName,
                            qualifiedName,
                            parentQualifiedName,
                            spanOf(node),
                            signatureHash("ENUM " + qualifiedName)));
                }
                return;
            }
            case "function_declaration" -> {
                String funcName = findChildName(node, source, "identifier");
                if (funcName != null && !funcName.isBlank()) {
                    String qualifiedName = parentQualifiedName == null ? funcName : parentQualifiedName + "." + funcName;
                    symbols.add(new Symbol(
                            SymbolKind.METHOD,
                            funcName,
                            qualifiedName,
                            parentQualifiedName,
                            spanOf(node),
                            signatureHash("METHOD " + qualifiedName)));
                }
                return;
            }
            default -> {
                // Traverse children for export_statement, program, statement_block, etc.
                for (int i = 0; i < node.getNamedChildCount(); i++) {
                    visitNode(node.getNamedChild(i), parentQualifiedName, source, symbols);
                }
            }
        }
    }

    private static void visitClassMember(TSNode member, String parentQualifiedName, String source, List<Symbol> symbols) {
        String type = member.getType();
        if ("method_definition".equals(type)) {
            String methodName = findChildName(member, source, "property_identifier");
            if (methodName != null) {
                boolean isConstructor = "constructor".equals(methodName);
                SymbolKind kind = isConstructor ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
                String qualifiedName = parentQualifiedName + "." + methodName;
                symbols.add(new Symbol(
                        kind,
                        methodName,
                        qualifiedName,
                        parentQualifiedName,
                        spanOf(member),
                        signatureHash(kind.name() + " " + qualifiedName)));
            }
        } else if ("public_field_definition".equals(type) || "field_definition".equals(type) || "property_definition".equals(type)) {
            String fieldName = findChildName(member, source, "property_identifier", "identifier");
            if (fieldName != null) {
                String qualifiedName = parentQualifiedName + "." + fieldName;
                symbols.add(new Symbol(
                        SymbolKind.FIELD,
                        fieldName,
                        qualifiedName,
                        parentQualifiedName,
                        spanOf(member),
                        signatureHash("FIELD " + qualifiedName)));
            }
        }
    }

    private static void visitInterfaceMember(TSNode member, String parentQualifiedName, String source, List<Symbol> symbols) {
        String type = member.getType();
        if ("property_signature".equals(type)) {
            String propName = findChildName(member, source, "property_identifier", "identifier");
            if (propName != null) {
                String qualifiedName = parentQualifiedName + "." + propName;
                symbols.add(new Symbol(
                        SymbolKind.FIELD,
                        propName,
                        qualifiedName,
                        parentQualifiedName,
                        spanOf(member),
                        signatureHash("FIELD " + qualifiedName)));
            }
        } else if ("method_signature".equals(type)) {
            String methodName = findChildName(member, source, "property_identifier", "identifier");
            if (methodName != null) {
                String qualifiedName = parentQualifiedName + "." + methodName;
                symbols.add(new Symbol(
                        SymbolKind.METHOD,
                        methodName,
                        qualifiedName,
                        parentQualifiedName,
                        spanOf(member),
                        signatureHash("METHOD " + qualifiedName)));
            }
        }
    }

    private static String findChildName(TSNode parent, String source, String... expectedTypes) {
        for (int i = 0; i < parent.getNamedChildCount(); i++) {
            TSNode child = parent.getNamedChild(i);
            for (String expectedType : expectedTypes) {
                if (expectedType.equals(child.getType())) {
                    return textOf(child, source);
                }
            }
        }
        return null;
    }

    private static TSNode findChildByType(TSNode parent, String type) {
        for (int i = 0; i < parent.getNamedChildCount(); i++) {
            TSNode child = parent.getNamedChild(i);
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    static Span spanOf(TSNode node) {
        int startLine = node.getStartPoint().getRow() + 1;
        int startCol = node.getStartPoint().getColumn() + 1;
        int endLine = Math.max(node.getEndPoint().getRow() + 1, startLine);
        int endCol = Math.max(1, node.getEndPoint().getColumn());
        return new Span(startLine, startCol, endLine, endCol);
    }

    static String textOf(TSNode node, String source) {
        int start = node.getStartByte();
        int end = node.getEndByte();
        if (start < 0 || end > source.length() || start > end) {
            return "";
        }
        return source.substring(start, end).trim();
    }

    private static String signatureHash(String text) {
        return Fingerprint.sha256Hex(text);
    }
}
