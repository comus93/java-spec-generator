package com.comus93.specgen.parser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.comus93.specgen.facts.JavaFacts.CallFact;
import com.comus93.specgen.facts.JavaFacts.StepFact;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public final class JavaStepVisitor {

    private JavaStepVisitor() {
    }

    public static List<StepFact> extract(MethodDeclaration method) {
        Objects.requireNonNull(method, "method");
        StepCollector collector = new StepCollector();
        method.getBody().ifPresent(body -> collector.visitStatement(body, ""));
        collector.candidates.sort(Comparator
                .comparingInt((Candidate candidate) -> candidate.sortLine)
                .thenComparingInt(candidate -> candidate.sortColumn));

        List<StepFact> steps = new ArrayList<>(collector.candidates.size());
        int sequence = 1;
        for (Candidate candidate : collector.candidates) {
            steps.add(new StepFact(sequence++, candidate.kind, candidate.line,
                    candidate.branchPath, candidate.snippet, ""));
        }
        return List.copyOf(steps);
    }

    public static List<CallFact> calls(MethodDeclaration method) {
        Objects.requireNonNull(method, "method");
        List<CallFact> calls = new ArrayList<>();
        method.getBody().ifPresent(body -> collectCalls(body, signature(method), calls));
        return List.copyOf(calls);
    }

    static List<CallFact> calls(Node node, String callerName) {
        Objects.requireNonNull(callerName, "callerName");
        if (node == null) {
            return List.of();
        }
        List<CallFact> calls = new ArrayList<>();
        collectCalls(node, callerName, calls);
        return List.copyOf(calls);
    }

    private static void collectCalls(Node node, String callerName, List<CallFact> calls) {
        node.accept(new CallVisitor(callerName, calls), null);
    }

    private static String signature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder(method.getNameAsString()).append('(');
        for (Parameter parameter : method.getParameters()) {
            if (signature.charAt(signature.length() - 1) != '(') {
                signature.append(',');
            }
            signature.append(parameter.getType().asString().replace(", ", ","));
        }
        return signature.append(')').toString();
    }

    private static String nested(String branchPath, String segment) {
        return branchPath.isEmpty() ? segment : branchPath + ">" + segment;
    }

    private static String normalizeSnippet(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean inString = false;
        boolean inChar = false;
        boolean inTextBlock = false;
        boolean escaped = false;
        boolean pendingSpace = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inTextBlock) {
                if (text.startsWith("\"\"\"", index)) {
                    result.append("\"\"\"");
                    index += 2;
                    inTextBlock = false;
                } else {
                    result.append(current);
                }
                continue;
            }
            if (inString || inChar) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (inString && current == '"') {
                    inString = false;
                } else if (inChar && current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"' && index + 2 < text.length()
                    && text.charAt(index + 1) == '"' && text.charAt(index + 2) == '"') {
                appendPendingSpace(result, pendingSpace);
                pendingSpace = false;
                result.append("\"\"\"");
                index += 2;
                inTextBlock = true;
                continue;
            }
            if (current == '"') {
                appendPendingSpace(result, pendingSpace);
                pendingSpace = false;
                result.append(current);
                inString = true;
                continue;
            }
            if (current == '\'') {
                appendPendingSpace(result, pendingSpace);
                pendingSpace = false;
                result.append(current);
                inChar = true;
                continue;
            }
            if (Character.isWhitespace(current)) {
                pendingSpace = true;
                continue;
            }
            appendPendingSpace(result, pendingSpace);
            pendingSpace = false;
            result.append(current);
        }
        return result.toString().strip();
    }

    private static void appendPendingSpace(StringBuilder result, boolean pendingSpace) {
        if (pendingSpace && !result.isEmpty()) {
            result.append(' ');
        }
    }

    private static int beginLine(Node node) {
        return node.getBegin().map(position -> position.line).orElse(0);
    }

    private static int beginColumn(Node node) {
        return node.getBegin().map(position -> position.column).orElse(0);
    }

    private static int endLine(Node node) {
        return node.getEnd().map(position -> position.line).orElse(beginLine(node));
    }

    private static int endColumn(Node node) {
        return node.getEnd().map(position -> position.column).orElse(beginColumn(node));
    }

    private record Candidate(String kind, int line, String branchPath, String snippet,
            int sortLine, int sortColumn) {
    }

    private static final class StepCollector {

        private final List<Candidate> candidates = new ArrayList<>();

        private void visitStatement(Statement statement, String branchPath) {
            if (statement == null) {
                return;
            }
            if (statement instanceof BlockStmt block) {
                block.getStatements().forEach(child -> visitStatement(child, branchPath));
                return;
            }
            if (statement instanceof ExpressionStmt expressionStatement) {
                visitExpression(expressionStatement.getExpression(), branchPath);
                return;
            }
            if (statement instanceof IfStmt ifStatement) {
                visitIf(ifStatement, branchPath);
                return;
            }
            if (statement instanceof WhileStmt whileStatement) {
                visitExpression(whileStatement.getCondition(), branchPath);
                add("loop", whileStatement, beginLine(whileStatement), branchPath,
                        whileStatement.toString(), endLine(whileStatement.getCondition()),
                        endColumn(whileStatement.getCondition()));
                visitStatement(whileStatement.getBody(), nested(branchPath, "while:body"));
                return;
            }
            if (statement instanceof DoStmt doStatement) {
                add("loop", doStatement, beginLine(doStatement), branchPath,
                        doStatement.toString(), beginLine(doStatement), beginColumn(doStatement));
                visitStatement(doStatement.getBody(), nested(branchPath, "do:body"));
                visitExpression(doStatement.getCondition(), branchPath);
                return;
            }
            if (statement instanceof ForStmt forStatement) {
                forStatement.getInitialization().forEach(expression ->
                        visitExpression(expression, branchPath));
                forStatement.getCompare().ifPresent(expression ->
                        visitExpression(expression, branchPath));
                forStatement.getUpdate().forEach(expression ->
                        visitExpression(expression, branchPath));
                Node headerAnchor;
                if (!forStatement.getUpdate().isEmpty()) {
                    headerAnchor = forStatement.getUpdate().get(forStatement.getUpdate().size() - 1);
                } else if (forStatement.getCompare().isPresent()) {
                    headerAnchor = forStatement.getCompare().get();
                } else if (!forStatement.getInitialization().isEmpty()) {
                    headerAnchor = forStatement.getInitialization()
                            .get(forStatement.getInitialization().size() - 1);
                } else {
                    headerAnchor = forStatement;
                }
                add("loop", forStatement, beginLine(forStatement), branchPath,
                        forStatement.toString(), endLine(headerAnchor), endColumn(headerAnchor));
                visitStatement(forStatement.getBody(), nested(branchPath, "for:body"));
                return;
            }
            if (statement instanceof ForEachStmt forEachStatement) {
                visitExpression(forEachStatement.getIterable(), branchPath);
                add("loop", forEachStatement, beginLine(forEachStatement), branchPath,
                        forEachStatement.toString(), endLine(forEachStatement.getIterable()),
                        endColumn(forEachStatement.getIterable()));
                visitStatement(forEachStatement.getBody(), nested(branchPath, "for-each:body"));
                return;
            }
            if (statement instanceof TryStmt tryStatement) {
                visitTry(tryStatement, branchPath);
                return;
            }
            if (statement instanceof ReturnStmt returnStatement) {
                returnStatement.getExpression().ifPresent(expression ->
                        visitExpression(expression, branchPath));
                add("return", returnStatement, beginLine(returnStatement), branchPath,
                        returnStatement.toString(), endLine(returnStatement),
                        endColumn(returnStatement));
                return;
            }
            if (statement instanceof ThrowStmt throwStatement) {
                visitExpression(throwStatement.getExpression(), branchPath);
                add("throw", throwStatement, beginLine(throwStatement), branchPath,
                        throwStatement.toString(), endLine(throwStatement),
                        endColumn(throwStatement));
                return;
            }
            if (statement instanceof LabeledStmt labeledStatement) {
                visitStatement(labeledStatement.getStatement(), branchPath);
                return;
            }
            if (statement instanceof SynchronizedStmt synchronizedStatement) {
                visitExpression(synchronizedStatement.getExpression(), branchPath);
                visitStatement(synchronizedStatement.getBody(), branchPath);
                return;
            }
            statement.getChildNodes().forEach(child -> visitNode(child, branchPath));
        }

        private void visitIf(IfStmt ifStatement, String branchPath) {
            visitExpression(ifStatement.getCondition(), branchPath);
            add("branch", ifStatement, beginLine(ifStatement), branchPath,
                    ifStatement.toString(), endLine(ifStatement.getCondition()),
                    endColumn(ifStatement.getCondition()));
            visitStatement(ifStatement.getThenStmt(), nested(branchPath, "if:true"));
            ifStatement.getElseStmt().ifPresent(elseStatement ->
                    visitStatement(elseStatement, nested(branchPath, "if:false")));
        }

        private void visitTry(TryStmt tryStatement, String branchPath) {
            add("try", tryStatement, beginLine(tryStatement), branchPath,
                    tryStatement.toString(), beginLine(tryStatement), beginColumn(tryStatement));
            tryStatement.getResources().forEach(expression ->
                    visitExpression(expression, branchPath));
            visitStatement(tryStatement.getTryBlock(), nested(branchPath, "try:body"));
            for (CatchClause catchClause : tryStatement.getCatchClauses()) {
                add("catch", catchClause, beginLine(catchClause), branchPath,
                        catchClause.toString(), beginLine(catchClause), beginColumn(catchClause));
                visitStatement(catchClause.getBody(), nested(branchPath, "catch:body"));
            }
            tryStatement.getFinallyBlock().ifPresent(block ->
                    visitStatement(block, nested(branchPath, "finally:body")));
        }

        private void visitExpression(Expression expression, String branchPath) {
            if (expression == null) {
                return;
            }
            if (expression instanceof MethodCallExpr call) {
                call.getScope().ifPresent(scope -> visitExpression(scope, branchPath));
                call.getArguments().forEach(argument -> visitExpression(argument, branchPath));
                add("call", call, beginLine(call), branchPath, call.toString(),
                        endLine(call), endColumn(call));
                return;
            }
            if (expression instanceof ObjectCreationExpr creation) {
                creation.getScope().ifPresent(scope -> visitExpression(scope, branchPath));
                creation.getArguments().forEach(argument ->
                        visitExpression(argument, branchPath));
                add("instantiate", creation, beginLine(creation), branchPath,
                        creation.toString(), endLine(creation), endColumn(creation));
                return;
            }
            if (expression instanceof AssignExpr assignment) {
                visitExpression(assignment.getTarget(), branchPath);
                visitExpression(assignment.getValue(), branchPath);
                add("assignment", assignment, beginLine(assignment), branchPath,
                        assignment.toString(), endLine(assignment), endColumn(assignment));
                return;
            }
            if (expression instanceof VariableDeclarationExpr declaration) {
                for (VariableDeclarator variable : declaration.getVariables()) {
                    Optional<Expression> initializer = variable.getInitializer();
                    if (initializer.isPresent()) {
                        visitExpression(initializer.get(), branchPath);
                        add("assignment", variable, beginLine(variable), branchPath,
                                variable.getNameAsString() + " = " + initializer.get(),
                                endLine(variable), endColumn(variable));
                    }
                }
                return;
            }
            if (expression instanceof LambdaExpr lambda) {
                visitStatement(lambda.getBody(), branchPath);
                return;
            }
            expression.getChildNodes().forEach(child -> visitNode(child, branchPath));
        }

        private void visitNode(Node node, String branchPath) {
            if (node instanceof Expression expression) {
                visitExpression(expression, branchPath);
                return;
            }
            if (node instanceof Statement statement) {
                visitStatement(statement, branchPath);
                return;
            }
            if (node instanceof TypeDeclaration<?> || node instanceof BodyDeclaration<?>) {
                return;
            }
            node.getChildNodes().forEach(child -> visitNode(child, branchPath));
        }

        private void add(String kind, Node node, int line, String branchPath, String snippet,
                int sortLine, int sortColumn) {
            candidates.add(new Candidate(kind, line, branchPath, normalizeSnippet(snippet),
                    sortLine, sortColumn));
        }
    }

    private static final class CallVisitor extends VoidVisitorAdapter<Void> {

        private final String callerName;
        private final List<CallFact> calls;

        private CallVisitor(String callerName, List<CallFact> calls) {
            this.callerName = callerName;
            this.calls = calls;
        }

        @Override
        public void visit(MethodCallExpr call, Void arg) {
            super.visit(call, arg);
            calls.add(new CallFact(beginLine(call), callerName, call.getNameAsString(), ""));
        }

        @Override
        public void visit(ObjectCreationExpr creation, Void arg) {
            creation.getScope().ifPresent(scope -> scope.accept(this, arg));
            creation.getArguments().forEach(argument -> argument.accept(this, arg));
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration declaration, Void arg) {
        }

        @Override
        public void visit(EnumDeclaration declaration, Void arg) {
        }

        @Override
        public void visit(RecordDeclaration declaration, Void arg) {
        }

        @Override
        public void visit(AnnotationDeclaration declaration, Void arg) {
        }
    }
}
