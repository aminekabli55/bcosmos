package com.bcosmos.core.java;

import com.bcosmos.core.model.Relationship;
import com.bcosmos.core.model.RelationshipType;
import com.bcosmos.core.model.ResolutionStatus;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts method call, inheritance, implementation, and type-usage relationships from JavaParser AST
 * (Phase 8, spec section 3.1 & 4.1).
 *
 * <p>Honest limit: without a full external classpath symbol solver, external library and JDK references
 * are recorded as {@link ResolutionStatus#UNRESOLVED}. Intra-package and imported types are resolved.</p>
 */
final class JavaRelationshipExtractor {

    private JavaRelationshipExtractor() {
    }

    static List<Relationship> extract(JavaParsedFile file) {
        if (file.ast().isEmpty()) {
            return Collections.emptyList();
        }

        CompilationUnit ast = file.ast().get();
        String filePath = file.filePath();

        String packageName = ast.getPackageDeclaration()
                .map(p -> p.getNameAsString())
                .orElse("");

        // Build simple name -> qualified name import map
        Map<String, String> imports = new HashMap<>();
        for (ImportDeclaration imp : ast.getImports()) {
            if (!imp.isAsterisk()) {
                String qName = imp.getNameAsString();
                String simpleName = qName.substring(qName.lastIndexOf('.') + 1);
                imports.put(simpleName, qName);
            }
        }

        List<Relationship> relationships = new ArrayList<>();

        for (TypeDeclaration<?> type : ast.getTypes()) {
            if (type instanceof ClassOrInterfaceDeclaration classOrInterface) {
                collectClassRelationships(classOrInterface, packageName, imports, filePath, relationships);
            }
        }

        return List.copyOf(relationships);
    }

    private static void collectClassRelationships(
            ClassOrInterfaceDeclaration decl,
            String packageName,
            Map<String, String> imports,
            String filePath,
            List<Relationship> out) {

        String classQName = packageName.isEmpty() ? decl.getNameAsString() : packageName + '.' + decl.getNameAsString();

        // Extends
        for (ClassOrInterfaceType extended : decl.getExtendedTypes()) {
            String targetRef = extended.getNameAsString();
            String targetQName = resolveTypeName(targetRef, packageName, imports);
            ResolutionStatus status = targetQName != null ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
            int line = extended.getRange().map(r -> r.begin.line).orElse(1);
            out.add(new Relationship(
                    classQName,
                    targetQName,
                    targetRef,
                    RelationshipType.EXTENDS,
                    status,
                    targetQName != null ? 1.0 : 0.7,
                    filePath,
                    line));
        }

        // Implements
        for (ClassOrInterfaceType implemented : decl.getImplementedTypes()) {
            String targetRef = implemented.getNameAsString();
            String targetQName = resolveTypeName(targetRef, packageName, imports);
            ResolutionStatus status = targetQName != null ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
            int line = implemented.getRange().map(r -> r.begin.line).orElse(1);
            out.add(new Relationship(
                    classQName,
                    targetQName,
                    targetRef,
                    RelationshipType.IMPLEMENTS,
                    status,
                    targetQName != null ? 1.0 : 0.7,
                    filePath,
                    line));
        }

        // Fields
        for (FieldDeclaration field : decl.getFields()) {
            String typeName = field.getElementType().asString();
            if (!isPrimitive(typeName)) {
                String targetQName = resolveTypeName(typeName, packageName, imports);
                ResolutionStatus status = targetQName != null ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
                int line = field.getRange().map(r -> r.begin.line).orElse(1);
                out.add(new Relationship(
                        classQName,
                        targetQName,
                        typeName,
                        RelationshipType.USES_TYPE,
                        status,
                        targetQName != null ? 1.0 : 0.7,
                        filePath,
                        line));
            }
        }

        // Methods & Constructors
        for (MethodDeclaration method : decl.getMethods()) {
            String methodQName = classQName + '.' + method.getNameAsString();
            collectMethodBodyRelationships(method, methodQName, packageName, imports, filePath, out);
        }

        for (ConstructorDeclaration ctor : decl.getConstructors()) {
            String ctorQName = classQName + '.' + ctor.getNameAsString();
            collectConstructorRelationships(ctor, ctorQName, packageName, imports, filePath, out);
        }
    }

    private static void collectMethodBodyRelationships(
            MethodDeclaration method,
            String methodQName,
            String packageName,
            Map<String, String> imports,
            String filePath,
            List<Relationship> out) {

        int methodLine = method.getRange().map(r -> r.begin.line).orElse(1);

        // Parameters
        for (Parameter param : method.getParameters()) {
            String typeName = param.getType().asString();
            if (!isPrimitive(typeName)) {
                String targetQName = resolveTypeName(typeName, packageName, imports);
                ResolutionStatus status = targetQName != null ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
                out.add(new Relationship(
                        methodQName,
                        targetQName,
                        typeName,
                        RelationshipType.USES_TYPE,
                        status,
                        targetQName != null ? 1.0 : 0.7,
                        filePath,
                        methodLine));
            }
        }

        // Method calls in body
        method.findAll(MethodCallExpr.class).forEach(call -> {
            String fullCallRef = callReference(call);
            int line = call.getRange().map(r -> r.begin.line).orElse(methodLine);

            out.add(new Relationship(
                    methodQName,
                    null, // Target symbol resolved at repository level by GraphService
                    fullCallRef,
                    RelationshipType.CALLS,
                    ResolutionStatus.UNRESOLVED,
                    0.7,
                    filePath,
                    line));
        });

        // Object instantiations
        method.findAll(ObjectCreationExpr.class).forEach(creation -> {
            String typeName = creation.getTypeAsString();
            if (!isPrimitive(typeName)) {
                String targetQName = resolveTypeName(typeName, packageName, imports);
                ResolutionStatus status = targetQName != null ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
                int line = creation.getRange().map(r -> r.begin.line).orElse(methodLine);

                out.add(new Relationship(
                        methodQName,
                        targetQName,
                        typeName,
                        RelationshipType.CALLS,
                        status,
                        targetQName != null ? 1.0 : 0.7,
                        filePath,
                        line));
            }
        });
    }

    private static void collectConstructorRelationships(
            ConstructorDeclaration ctor,
            String ctorQName,
            String packageName,
            Map<String, String> imports,
            String filePath,
            List<Relationship> out) {

        int ctorLine = ctor.getRange().map(r -> r.begin.line).orElse(1);

        ctor.findAll(MethodCallExpr.class).forEach(call -> {
            String fullCallRef = callReference(call);
            int line = call.getRange().map(r -> r.begin.line).orElse(ctorLine);

            out.add(new Relationship(
                    ctorQName,
                    null,
                    fullCallRef,
                    RelationshipType.CALLS,
                    ResolutionStatus.UNRESOLVED,
                    0.7,
                    filePath,
                    line));
        });
    }

    /**
     * Name of a call as written in code: the callee name, qualified by its receiver only when the receiver has a
     * name of its own ({@code repository.save}, {@code System.out.println}).
     *
     * <p>A receiver that is itself an expression (a fluent chain, a lambda, a cast) has no name, so the call is
     * recorded by method name alone. Using the printed receiver expression instead made
     * {@code targetReferenceName} grow with the whole fluent chain - 800+ characters on a Spring Security
     * configuration - which is neither a name nor guaranteed to fit the column.</p>
     */
    private static String callReference(MethodCallExpr call) {
        String methodName = call.getNameAsString();
        String receiver = call.getScope().map(JavaRelationshipExtractor::receiverName).orElse("");
        return receiver.isEmpty() ? methodName : receiver + '.' + methodName;
    }

    /** Simple, bounded name of a receiver expression; empty when the expression has no name. */
    private static String receiverName(Expression scope) {
        if (scope.isNameExpr()) {
            return scope.asNameExpr().getNameAsString();
        }
        if (scope.isThisExpr()) {
            return "this";
        }
        if (scope.isSuperExpr()) {
            return "super";
        }
        if (scope.isFieldAccessExpr()) {
            FieldAccessExpr field = scope.asFieldAccessExpr();
            String inner = receiverName(field.getScope());
            return inner.isEmpty() ? field.getNameAsString() : inner + '.' + field.getNameAsString();
        }
        return "";
    }

    private static String resolveTypeName(String typeName, String packageName, Map<String, String> imports) {
        if (imports.containsKey(typeName)) {
            return imports.get(typeName);
        }
        if (!packageName.isEmpty() && !typeName.contains(".")) {
            // Intra-package candidate
            return packageName + '.' + typeName;
        }
        return null;
    }

    private static boolean isPrimitive(String type) {
        return switch (type) {
            case "void", "int", "long", "boolean", "double", "float", "byte", "short", "char",
                 "String", "Integer", "Long", "Boolean", "Double", "Float" -> true;
            default -> false;
        };
    }
}
