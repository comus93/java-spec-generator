package com.comus93.specgen.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comus93.specgen.facts.JavaFacts.CallFact;
import com.comus93.specgen.facts.JavaFacts.StepFact;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

class JavaStepVisitorTest {

    private static final ParserConfiguration CONFIGURATION = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

    @Test
    void ordersNestedCallsAndIfBranchesFromSourceStructure() {
        MethodDeclaration method = method("""
                class Sample {
                    void run(boolean flag, Service service, int id) {
                        if (flag) { service.save(load(id)); } else { service.delete(id); }
                    }
                }
                """, "run");

        assertEquals(List.of(
                new StepFact(1, "branch", 3, "",
                        "if (flag) { service.save(load(id)); } else { service.delete(id); }", ""),
                new StepFact(2, "call", 3, "if:true", "load(id)", ""),
                new StepFact(3, "call", 3, "if:true", "service.save(load(id))", ""),
                new StepFact(4, "call", 3, "if:false", "service.delete(id)", "")),
                JavaStepVisitor.extract(method));
    }

    @Test
    void ordersLoopConditionsBodiesAndNestedIfBranches() {
        MethodDeclaration method = method("""
                class Sample {
                    void run(Service service) {
                        while (service.hasNext()) { if (service.ready()) { service.next(); } }
                        for (Item item : service.items()) { service.consume(item); }
                    }
                }
                """, "run");

        assertEquals(List.of(
                new StepFact(1, "call", 3, "", "service.hasNext()", ""),
                new StepFact(2, "loop", 3, "",
                        "while (service.hasNext()) { if (service.ready()) { service.next(); } }", ""),
                new StepFact(3, "call", 3, "while:body", "service.ready()", ""),
                new StepFact(4, "branch", 3, "while:body",
                        "if (service.ready()) { service.next(); }", ""),
                new StepFact(5, "call", 3, "while:body>if:true", "service.next()", ""),
                new StepFact(6, "call", 4, "", "service.items()", ""),
                new StepFact(7, "loop", 4, "",
                        "for (Item item : service.items()) { service.consume(item); }", ""),
                new StepFact(8, "call", 4, "for-each:body", "service.consume(item)", "")),
                JavaStepVisitor.extract(method));
    }

    @Test
    void ordersTryCatchAndThrowWithBranchPaths() {
        MethodDeclaration method = method("""
                class Sample {
                    void run(Service service) throws Exception {
                        try { service.flush(); } catch (Exception ex) { throw new IllegalStateException(ex); } finally { service.close(); }
                    }
                }
                """, "run");

        assertEquals(List.of(
                new StepFact(1, "try", 3, "",
                        "try { service.flush(); } catch (Exception ex) { throw new IllegalStateException(ex); } finally { service.close(); }",
                        ""),
                new StepFact(2, "call", 3, "try:body", "service.flush()", ""),
                new StepFact(3, "catch", 3, "",
                        "catch (Exception ex) { throw new IllegalStateException(ex); }", ""),
                new StepFact(4, "instantiate", 3, "catch:body", "new IllegalStateException(ex)", ""),
                new StepFact(5, "throw", 3, "catch:body",
                        "throw new IllegalStateException(ex);", ""),
                new StepFact(6, "call", 3, "finally:body", "service.close()", "")),
                JavaStepVisitor.extract(method));
    }

    @Test
    void ordersAssignmentsObjectCreationAndReturnAfterTheirExpressions() {
        MethodDeclaration method = method("""
                class Sample {
                    void run(Service service) {
                        int total = service.initial();
                        total = service.add(total);
                        Item item = new Item(total);
                        return total;
                    }
                }
                """, "run");

        assertEquals(List.of(
                new StepFact(1, "call", 3, "", "service.initial()", ""),
                new StepFact(2, "assignment", 3, "", "total = service.initial()", ""),
                new StepFact(3, "call", 4, "", "service.add(total)", ""),
                new StepFact(4, "assignment", 4, "", "total = service.add(total)", ""),
                new StepFact(5, "instantiate", 5, "", "new Item(total)", ""),
                new StepFact(6, "assignment", 5, "", "item = new Item(total)", ""),
                new StepFact(7, "return", 6, "", "return total;", "")),
                JavaStepVisitor.extract(method));
    }

    @Test
    void extractsCallsInStructuralSourceOrderWithBlankTargets() {
        MethodDeclaration method = method("""
                class Sample {
                    void run(Service service) {
                        service.first(); service.second();
                        service.save(load());
                    }
                }
                """, "run");

        assertEquals(List.of(
                new CallFact(3, "run(Service)", "first", ""),
                new CallFact(3, "run(Service)", "second", ""),
                new CallFact(4, "run(Service)", "load", ""),
                new CallFact(4, "run(Service)", "save", "")),
                JavaStepVisitor.calls(method));
    }

    @Test
    void repeatsIdenticalResultsForDeepEquality() {
        String source = """
                class Sample {
                    void run(Service service) {
                        if (service.ready()) {
                            service.save(load());
                        }
                        return service.finish();
                    }
                }
                """;
        MethodDeclaration first = method(source, "run");
        MethodDeclaration second = method(source, "run");

        List<StepFact> steps = JavaStepVisitor.extract(first);
        List<CallFact> calls = JavaStepVisitor.calls(first);

        assertEquals(steps, JavaStepVisitor.extract(first));
        assertEquals(calls, JavaStepVisitor.calls(first));
        assertEquals(steps, JavaStepVisitor.extract(second));
        assertEquals(calls, JavaStepVisitor.calls(second));
    }

    private static MethodDeclaration method(String source, String name) {
        ParseResult<CompilationUnit> result = new JavaParser(CONFIGURATION).parse(source);
        assertTrue(result.isSuccessful(), () -> "parse failed: " + result.getProblems());
        CompilationUnit unit = result.getResult().orElseThrow();
        return unit.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing method " + name));
    }
}
