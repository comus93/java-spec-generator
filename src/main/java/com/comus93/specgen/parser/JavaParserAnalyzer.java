package com.comus93.specgen.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.comus93.specgen.facts.JavaFacts;
import com.comus93.specgen.facts.JavaFacts.CallFact;
import com.comus93.specgen.facts.JavaFacts.ImportFact;
import com.comus93.specgen.facts.JavaFacts.MethodFact;
import com.comus93.specgen.facts.JavaFacts.ParameterFact;
import com.comus93.specgen.facts.JavaFacts.StaticInitFact;
import com.comus93.specgen.facts.JavaFacts.TypeRefFact;
import com.comus93.specgen.facts.JavaFacts.VariableFact;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public final class JavaParserAnalyzer implements SourceAnalyzer<JavaFacts> {

    private final ParserConfiguration configuration;

    public JavaParserAnalyzer() {
        this.configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Override
    public JavaFacts analyze(Path sourceFile) throws SourceAnalysisException {
        Objects.requireNonNull(sourceFile, "sourceFile");

        ParseResult<CompilationUnit> result;
        try {
            result = new JavaParser(configuration).parse(sourceFile);
        } catch (IOException error) {
            throw new SourceAnalysisException(sourceFile, SourceAnalysisException.READ_ERROR, "", error);
        }

        if (!result.isSuccessful()) {
            throw new SourceAnalysisException(sourceFile, SourceAnalysisException.PARSE_ERROR,
                    problemLocation(result.getProblems()));
        }

        CompilationUnit unit = result.getResult()
                .orElseThrow(() -> new SourceAnalysisException(sourceFile,
                        SourceAnalysisException.PARSE_ERROR, ""));
        return extract(sourceFile, unit);
    }

    private static JavaFacts extract(Path sourceFile, CompilationUnit unit) {
        List<VariableFact> variables = new ArrayList<>();
        List<MethodFact> methods = new ArrayList<>();
        List<StaticInitFact> staticInitializations = new ArrayList<>();
        new DeclarationCollector(variables, methods, staticInitializations).visit(unit, null);

        List<ImportFact> imports = extractImports(unit);
        List<TypeRefFact> typeRefs = extractTypeRefs(unit, imports);
        List<CallFact> calls = extractCalls(unit);

        return new JavaFacts(sourceFile, typeKind(unit), variables, methods, calls,
                typeRefs, imports, staticInitializations);
    }

    private static List<CallFact> extractCalls(CompilationUnit unit) {
        List<CallFact> calls = new ArrayList<>();
        new CallCollector(calls).visit(unit, null);
        return calls;
    }

    private static String problemLocation(List<Problem> problems) {
        if (problems.isEmpty()) {
            return "";
        }
        return problems.get(0).getLocation()
                .flatMap(TokenRange::toRange)
                .map(range -> range.begin.line + ":" + range.begin.column)
                .orElse("");
    }

    private static List<ImportFact> extractImports(CompilationUnit unit) {
        List<ImportFact> imports = new ArrayList<>();
        unit.getImports().forEach(importDeclaration -> {
            String importPath = importDeclaration.getNameAsString();
            if (importDeclaration.isAsterisk()) {
                importPath += ".*";
            }
            String shortName = importPath.substring(importPath.lastIndexOf('.') + 1);
            imports.add(new ImportFact(shortName, importPath, line(importDeclaration)));
        });
        return imports;
    }

    @SuppressWarnings("unchecked")
    private static TypeDeclaration<?> enclosingDeclaredType(InitializerDeclaration initializer) {
        return initializer.findAncestor(TypeDeclaration.class).orElse(null);
    }

    private static Set<String> staticFieldNames(TypeDeclaration<?> type) {
        Set<String> names = new LinkedHashSet<>();
        if (type == null) {
            return names;
        }
        for (FieldDeclaration field : type.getFields()) {
            if (field.hasModifier(Modifier.Keyword.STATIC)) {
                field.getVariables().forEach(variable -> names.add(variable.getNameAsString()));
            }
        }
        return names;
    }

    private static List<TypeRefFact> extractTypeRefs(CompilationUnit unit, List<ImportFact> imports) {
        List<TypeRefFact> typeRefs = new ArrayList<>();
        for (ImportFact importFact : imports) {
            typeRefs.add(new TypeRefFact(importFact.line(), "import", importFact.shortName(), null));
        }
        new TypeRefCollector(typeRefs).visit(unit, null);
        return typeRefs;
    }

    private static String typeKind(CompilationUnit unit) {
        return unit.getTypes().stream()
                .findFirst()
                .map(JavaParserAnalyzer::typeKind)
                .orElse("");
    }

    private static String typeKind(TypeDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
            return classOrInterface.isInterface() ? "Interface" : "Class";
        }
        if (declaration instanceof EnumDeclaration) {
            return "Enum";
        }
        if (declaration instanceof RecordDeclaration) {
            return "Record";
        }
        if (declaration instanceof AnnotationDeclaration) {
            return "Annotation";
        }
        return "";
    }

    private static String declaredType(TypeDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
            return classOrInterface.isInterface() ? "interface" : "class";
        }
        if (declaration instanceof EnumDeclaration) {
            return "enum";
        }
        if (declaration instanceof RecordDeclaration) {
            return "record";
        }
        if (declaration instanceof AnnotationDeclaration) {
            return "annotation";
        }
        return "";
    }

    private static VariableFact typeFact(TypeDeclaration<?> declaration, NodeWithModifiers<?> modifiers) {
        return new VariableFact(typeKind(declaration), declaration.getNameAsString(),
                declaredType(declaration), modifiers(modifiers), "", "", line(declaration),
                enclosingTypeName(declaration));
    }

    private static String signature(String name, NodeList<Parameter> parameters) {
        String joinedParameters = parameters.stream()
                .map(parameter -> compactType(parameter.getType().asString()))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return name + "(" + joinedParameters + ")";
    }

    private static String compactType(String type) {
        return type.replace(", ", ",");
    }

    private static List<ParameterFact> parameters(NodeList<Parameter> parameters) {
        List<ParameterFact> facts = new ArrayList<>();
        for (Parameter parameter : parameters) {
            facts.add(new ParameterFact(parameter.getNameAsString(), parameter.getType().asString()));
        }
        return facts;
    }

    private static String modifiers(NodeWithModifiers<?> node) {
        StringBuilder value = new StringBuilder();
        for (Modifier modifier : node.getModifiers()) {
            if (!value.isEmpty()) {
                value.append(' ');
            }
            value.append(modifier.getKeyword().asString());
        }
        return value.toString();
    }

    private static String visibility(NodeWithModifiers<?> node) {
        for (Modifier modifier : node.getModifiers()) {
            switch (modifier.getKeyword()) {
                case PUBLIC:
                    return "public";
                case PROTECTED:
                    return "protected";
                case PRIVATE:
                    return "private";
                default:
                    break;
            }
        }
        return "";
    }

    private static String javadoc(NodeWithJavadoc<?> node) {
        return node.getJavadocComment()
                .map(comment -> comment.getContent().strip())
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private static String enclosingTypeName(Node node) {
        Optional<ClassOrInterfaceDeclaration> classOrInterface = node.findAncestor(ClassOrInterfaceDeclaration.class);
        if (classOrInterface.isPresent()) {
            return classOrInterface.get().getNameAsString();
        }
        Optional<EnumDeclaration> enumDeclaration = node.findAncestor(EnumDeclaration.class);
        if (enumDeclaration.isPresent()) {
            return enumDeclaration.get().getNameAsString();
        }
        Optional<RecordDeclaration> recordDeclaration = node.findAncestor(RecordDeclaration.class);
        if (recordDeclaration.isPresent()) {
            return recordDeclaration.get().getNameAsString();
        }
        Optional<AnnotationDeclaration> annotationDeclaration = node.findAncestor(AnnotationDeclaration.class);
        if (annotationDeclaration.isPresent()) {
            return annotationDeclaration.get().getNameAsString();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String methodOwner(Node node) {
        Optional<MethodDeclaration> method = node.findAncestor(MethodDeclaration.class);
        if (method.isPresent()) {
            return signature(method.get().getNameAsString(), method.get().getParameters());
        }
        Optional<ConstructorDeclaration> constructor = node.findAncestor(ConstructorDeclaration.class);
        if (constructor.isPresent()) {
            return signature(constructor.get().getNameAsString(), constructor.get().getParameters());
        }
        return enclosingTypeName(node);
    }

    private static int line(Node node) {
        return node.getBegin().map(position -> position.line).orElse(0);
    }

    private static int endLine(Node node) {
        return node.getEnd().map(position -> position.line).orElse(0);
    }

    private static StaticInitFact staticBlockFact(Statement statement, Set<String> staticFieldNames,
            String enclosingTypeName) {
        int line = line(statement);
        if (statement instanceof ExpressionStmt expressionStatement) {
            Expression expression = expressionStatement.getExpression();
            if (expression.isMethodCallExpr()) {
                MethodCallExpr call = expression.asMethodCallExpr();
                String target = call.getScope()
                        .map(scope -> fieldNameCandidate(scope, enclosingTypeName))
                        .filter(staticFieldNames::contains)
                        .orElse("");
                if (target.isEmpty()) {
                    return new StaticInitFact("", "static initializer", expression.toString(), line);
                }
                String initializer = call.getArguments().size() == 1
                        ? call.getArgument(0).toString()
                        : expression.toString();
                return new StaticInitFact(target, "static initializer", initializer, line);
            }
            if (expression.isAssignExpr()) {
                AssignExpr assignment = expression.asAssignExpr();
                String target = Optional.of(assignment.getTarget())
                        .map(scope -> fieldNameCandidate(scope, enclosingTypeName))
                        .filter(staticFieldNames::contains)
                        .orElse("");
                if (target.isEmpty()) {
                    return new StaticInitFact("", "static initializer", expression.toString(), line);
                }
                return new StaticInitFact(target, "static initializer",
                        assignment.getValue().toString(), line);
            }
            return new StaticInitFact("", "static initializer", expression.toString(), line);
        }
        return new StaticInitFact("", "static initializer", statement.toString(), line);
    }

    private static String fieldNameCandidate(Expression expression, String enclosingTypeName) {
        if (expression.isNameExpr()) {
            return expression.asNameExpr().getNameAsString();
        }
        if (expression.isFieldAccessExpr()) {
            FieldAccessExpr access = expression.asFieldAccessExpr();
            String qualifier = access.getScope().toString();
            if (qualifier.equals("this") || (!enclosingTypeName.isEmpty() && qualifier.equals(enclosingTypeName))) {
                return access.getNameAsString();
            }
        }
        return "";
    }

    private static final class DeclarationCollector extends VoidVisitorAdapter<Void> {

        private final List<VariableFact> variables;
        private final List<MethodFact> methods;
        private final List<StaticInitFact> staticInitializations;

        private DeclarationCollector(List<VariableFact> variables, List<MethodFact> methods,
                List<StaticInitFact> staticInitializations) {
            this.variables = variables;
            this.methods = methods;
            this.staticInitializations = staticInitializations;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration declaration, Void arg) {
            variables.add(typeFact(declaration, declaration));
            super.visit(declaration, arg);
        }

        @Override
        public void visit(EnumDeclaration declaration, Void arg) {
            variables.add(typeFact(declaration, declaration));
            super.visit(declaration, arg);
        }

        @Override
        public void visit(RecordDeclaration declaration, Void arg) {
            variables.add(typeFact(declaration, declaration));
            super.visit(declaration, arg);
        }

        @Override
        public void visit(AnnotationDeclaration declaration, Void arg) {
            variables.add(typeFact(declaration, declaration));
            super.visit(declaration, arg);
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            String owner = enclosingTypeName(field);
            String modifiers = modifiers(field);
            String lifetime = field.hasModifier(Modifier.Keyword.STATIC) ? "class" : "instance";
            for (VariableDeclarator variable : field.getVariables()) {
                String initializer = variable.getInitializer().map(Expression::toString).orElse("");
                variables.add(new VariableFact("Field", variable.getNameAsString(),
                        variable.getType().asString(), modifiers, lifetime, initializer,
                        line(variable), owner));
                if (field.hasModifier(Modifier.Keyword.STATIC) && variable.getInitializer().isPresent()) {
                    staticInitializations.add(new StaticInitFact(variable.getNameAsString(),
                            "field initializer", initializer, line(variable)));
                }
            }
            super.visit(field, arg);
        }

        @Override
        public void visit(VariableDeclarationExpr declaration, Void arg) {
            String owner = methodOwner(declaration);
            String modifiers = modifiers(declaration);
            for (VariableDeclarator variable : declaration.getVariables()) {
                String initializer = variable.getInitializer().map(Expression::toString).orElse("");
                variables.add(new VariableFact("Local", variable.getNameAsString(),
                        variable.getType().asString(), modifiers, "", initializer,
                        line(variable), owner));
            }
            super.visit(declaration, arg);
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            methods.add(new MethodFact(method.getNameAsString(),
                    signature(method.getNameAsString(), method.getParameters()),
                    parameters(method.getParameters()), method.getType().asString(),
                    visibility(method), line(method), endLine(method), javadoc(method),
                    JavaStepVisitor.extract(method)));
            super.visit(method, arg);
        }

        @Override
        public void visit(ConstructorDeclaration constructor, Void arg) {
            methods.add(new MethodFact(constructor.getNameAsString(),
                    signature(constructor.getNameAsString(), constructor.getParameters()),
                    parameters(constructor.getParameters()), "", visibility(constructor),
                    line(constructor), endLine(constructor), javadoc(constructor), List.of()));
            super.visit(constructor, arg);
        }

        @Override
        public void visit(InitializerDeclaration initializer, Void arg) {
            if (initializer.isStatic()) {
                TypeDeclaration<?> enclosingType = enclosingDeclaredType(initializer);
                Set<String> staticFieldNames = staticFieldNames(enclosingType);
                String enclosingTypeName = enclosingType == null ? "" : enclosingType.getNameAsString();
                for (Statement statement : initializer.getBody().getStatements()) {
                    staticInitializations.add(staticBlockFact(statement, staticFieldNames, enclosingTypeName));
                }
            }
            super.visit(initializer, arg);
        }
    }

    private static final class CallCollector extends VoidVisitorAdapter<Void> {

        private final List<CallFact> calls;

        private CallCollector(List<CallFact> calls) {
            this.calls = calls;
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            for (VariableDeclarator variable : field.getVariables()) {
                variable.getInitializer().ifPresent(initializer ->
                        calls.addAll(JavaStepVisitor.calls(initializer, "[field-initializer]")));
            }
            super.visit(field, arg);
        }

        @Override
        public void visit(InitializerDeclaration initializer, Void arg) {
            String callerName = initializer.isStatic()
                    ? "[static initializer]"
                    : "[instance initializer]";
            calls.addAll(JavaStepVisitor.calls(initializer, callerName));
            super.visit(initializer, arg);
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            calls.addAll(JavaStepVisitor.calls(method));
            super.visit(method, arg);
        }

        @Override
        public void visit(ConstructorDeclaration constructor, Void arg) {
            calls.addAll(JavaStepVisitor.calls(constructor,
                    signature(constructor.getNameAsString(), constructor.getParameters())));
            super.visit(constructor, arg);
        }
    }

    private static final class TypeRefCollector extends VoidVisitorAdapter<Void> {

        private final List<TypeRefFact> typeRefs;

        private TypeRefCollector(List<TypeRefFact> typeRefs) {
            this.typeRefs = typeRefs;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration declaration, Void arg) {
            declaration.getExtendedTypes().forEach(type -> addType(type, line(declaration)));
            declaration.getImplementedTypes().forEach(type -> addType(type, line(declaration)));
            super.visit(declaration, arg);
        }

        @Override
        public void visit(EnumDeclaration declaration, Void arg) {
            declaration.getImplementedTypes().forEach(type -> addType(type, line(declaration)));
            super.visit(declaration, arg);
        }

        @Override
        public void visit(RecordDeclaration declaration, Void arg) {
            declaration.getImplementedTypes().forEach(type -> addType(type, line(declaration)));
            declaration.getParameters().forEach(parameter -> addType(parameter.getType(), line(parameter)));
            super.visit(declaration, arg);
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            field.getVariables().forEach(variable -> addType(variable.getType(), line(variable)));
            super.visit(field, arg);
        }

        @Override
        public void visit(VariableDeclarationExpr declaration, Void arg) {
            declaration.getVariables().forEach(variable -> addType(variable.getType(), line(variable)));
            super.visit(declaration, arg);
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            addType(method.getType(), line(method));
            method.getParameters().forEach(parameter -> addType(parameter.getType(), line(parameter)));
            method.getThrownExceptions().forEach(type -> addType(type, line(method)));
            super.visit(method, arg);
        }

        @Override
        public void visit(ConstructorDeclaration constructor, Void arg) {
            constructor.getParameters().forEach(parameter -> addType(parameter.getType(), line(parameter)));
            constructor.getThrownExceptions().forEach(type -> addType(type, line(constructor)));
            super.visit(constructor, arg);
        }

        @Override
        public void visit(ObjectCreationExpr creation, Void arg) {
            ClassOrInterfaceType createdType = creation.getType();
            typeRefs.add(new TypeRefFact(line(creation), "new", createdType.getNameWithScope(), null));
            createdType.getTypeArguments().ifPresent(arguments ->
                    arguments.forEach(argument -> addType(argument, line(creation))));
            super.visit(creation, arg);
        }

        @Override
        public void visit(CastExpr cast, Void arg) {
            addType(cast.getType(), line(cast));
            super.visit(cast, arg);
        }

        @Override
        public void visit(InstanceOfExpr instanceOf, Void arg) {
            addType(instanceOf.getType(), line(instanceOf));
            super.visit(instanceOf, arg);
        }

        @Override
        public void visit(ClassExpr classExpression, Void arg) {
            addType(classExpression.getType(), line(classExpression));
            super.visit(classExpression, arg);
        }

        @Override
        public void visit(MethodCallExpr call, Void arg) {
            call.getTypeArguments().ifPresent(arguments ->
                    arguments.forEach(type -> addType(type, line(call))));
            super.visit(call, arg);
        }

        private void addType(Type type, int line) {
            if (type.isArrayType()) {
                addType(type.asArrayType().getComponentType(), line);
                return;
            }
            if (type.isClassOrInterfaceType()) {
                ClassOrInterfaceType classOrInterface = type.asClassOrInterfaceType();
                typeRefs.add(new TypeRefFact(line, "type", classOrInterface.getNameWithScope(), null));
                classOrInterface.getTypeArguments().ifPresent(arguments ->
                        arguments.forEach(argument -> addType(argument, line)));
                return;
            }
            if (type.isWildcardType()) {
                type.asWildcardType().getExtendedType().ifPresent(extended -> addType(extended, line));
                type.asWildcardType().getSuperType().ifPresent(superType -> addType(superType, line));
                return;
            }
            if (type.isUnionType()) {
                type.asUnionType().getElements().forEach(element -> addType(element, line));
                return;
            }
            if (type.isIntersectionType()) {
                type.asIntersectionType().getElements().forEach(element -> addType(element, line));
            }
        }
    }
}
