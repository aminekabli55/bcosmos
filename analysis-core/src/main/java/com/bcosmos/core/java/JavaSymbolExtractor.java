package com.bcosmos.core.java;

import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.Symbol;
import com.bcosmos.core.model.SymbolKind;
import com.bcosmos.core.quality.Fingerprint;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts classes, interfaces, enums, records, annotations, methods, constructors and fields from a parsed
 * compilation unit, with JavaParser only — no symbol solver.
 *
 * <p>Honest limit (spec v2 section 14, "JavaParser symbol resolution gaps"): names and nesting are exact, but
 * types are not resolved, so this cannot say <em>which</em> method a call refers to. That is the relationship
 * layer of phase 8 and is deliberately not guessed here.</p>
 */
final class JavaSymbolExtractor {

    private JavaSymbolExtractor() {
    }

    static List<Symbol> extract(CompilationUnit ast) {
        String packageName = ast.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString())
                .orElse("");
        List<Symbol> symbols = new ArrayList<>();
        for (TypeDeclaration<?> type : ast.getTypes()) {
            collectType(type, packageName, null, symbols);
        }
        return List.copyOf(symbols);
    }

    private static void collectType(
            TypeDeclaration<?> type, String prefix, String parentQualifiedName, List<Symbol> symbols) {
        String simpleName = type.getNameAsString();
        String qualifiedName = prefix.isEmpty() ? simpleName : prefix + '.' + simpleName;
        SymbolKind kind = kindOf(type);
        symbols.add(new Symbol(kind, simpleName, qualifiedName, parentQualifiedName, spanOf(type),
                signatureHash(kind.name() + ' ' + qualifiedName)));

        for (var member : type.getMembers()) {
            if (member instanceof ClassOrInterfaceDeclaration nested) {
                collectType(nested, qualifiedName, qualifiedName, symbols);
            } else if (member instanceof EnumDeclaration nestedEnum) {
                collectType(nestedEnum, qualifiedName, qualifiedName, symbols);
            } else if (member instanceof RecordDeclaration nestedRecord) {
                collectType(nestedRecord, qualifiedName, qualifiedName, symbols);
            } else if (member instanceof AnnotationDeclaration nestedAnnotation) {
                collectType(nestedAnnotation, qualifiedName, qualifiedName, symbols);
            } else if (member instanceof MethodDeclaration method) {
                symbols.add(new Symbol(SymbolKind.METHOD, method.getNameAsString(),
                        qualifiedName + '.' + method.getNameAsString(), qualifiedName, spanOf(method),
                        signatureHash(methodSignature(method))));
            } else if (member instanceof ConstructorDeclaration constructor) {
                symbols.add(new Symbol(SymbolKind.CONSTRUCTOR, constructor.getNameAsString(),
                        qualifiedName + '.' + constructor.getNameAsString(), qualifiedName, spanOf(constructor),
                        signatureHash(constructorSignature(constructor))));
            } else if (member instanceof FieldDeclaration field) {
                for (VariableDeclarator variable : field.getVariables()) {
                    symbols.add(new Symbol(SymbolKind.FIELD, variable.getNameAsString(),
                            qualifiedName + '.' + variable.getNameAsString(), qualifiedName, spanOf(variable),
                            signatureHash(field.getModifiers().toString() + ' ' + variable.getType().asString()
                                    + ' ' + variable.getNameAsString())));
                }
            }
        }
    }

    private static SymbolKind kindOf(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration declaration && declaration.isInterface()) {
            return SymbolKind.INTERFACE;
        }
        if (type instanceof EnumDeclaration) {
            return SymbolKind.ENUM;
        }
        if (type instanceof RecordDeclaration) {
            return SymbolKind.RECORD;
        }
        if (type instanceof AnnotationDeclaration) {
            return SymbolKind.ANNOTATION;
        }
        return SymbolKind.CLASS;
    }

    private static String methodSignature(MethodDeclaration method) {
        return method.getType().asString() + ' ' + method.getNameAsString() + '(' + parameterTypes(method) + ')';
    }

    private static String constructorSignature(ConstructorDeclaration constructor) {
        return constructor.getNameAsString() + '(' + parameterTypes(constructor) + ')';
    }

    private static String parameterTypes(Node node) {
        List<String> types = new ArrayList<>();
        if (node instanceof MethodDeclaration method) {
            method.getParameters().forEach(parameter -> types.add(parameter.getType().asString()));
        } else if (node instanceof ConstructorDeclaration constructor) {
            constructor.getParameters().forEach(parameter -> types.add(parameter.getType().asString()));
        }
        return String.join(", ", types);
    }

    private static String signatureHash(String signatureText) {
        return Fingerprint.sha256Hex(collapseWhitespace(signatureText));
    }

    private static String collapseWhitespace(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    private static Span spanOf(Node node) {
        Optional<Range> range = node.getRange();
        if (range.isEmpty()) {
            return Span.ofLine(1);
        }
        Range value = range.get();
        return new Span(value.begin.line, value.begin.column, value.end.line, value.end.column);
    }
}
