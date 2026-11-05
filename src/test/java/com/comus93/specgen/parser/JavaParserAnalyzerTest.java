package com.comus93.specgen.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.facts.JavaFacts;
import com.comus93.specgen.facts.JavaFacts.ImportFact;
import com.comus93.specgen.facts.JavaFacts.MethodFact;
import com.comus93.specgen.facts.JavaFacts.StaticInitFact;
import com.comus93.specgen.facts.JavaFacts.TypeRefFact;
import com.comus93.specgen.facts.JavaFacts.VariableFact;

class JavaParserAnalyzerTest {

    @TempDir
    Path tempDir;

    private final SourceAnalyzer<JavaFacts> analyzer = new JavaParserAnalyzer();

    @Test
    void extractsTodoControllerDeclarationsMethodsAndImports() throws Exception {
        Path source = fixture("TodoController.java");

        JavaFacts facts = analyzer.analyze(source);

        assertEquals(source, facts.sourceFile());
        assertEquals("Class", facts.typeKind());
        assertEquals(9, facts.methods().size());

        MethodFact addTodo = method(facts, "addTodo(ModelMap,Todo,BindingResult)");
        assertEquals("addTodo", addTodo.name());
        assertEquals("public", addTodo.visibility());
        assertEquals("String", addTodo.returnType());
        assertEquals(List.of("model", "todo", "result"), parameterNames(addTodo));
        assertEquals(List.of("ModelMap", "Todo", "BindingResult"), parameterTypes(addTodo));
        assertTrue(addTodo.startLine() > 0);
        assertTrue(addTodo.endLine() >= addTodo.startLine());
        assertTrue(addTodo.steps().isEmpty());
        assertTrue(addTodo.javadoc().isEmpty());

        assertEquals("protected", method(facts, "initBinder(WebDataBinder)").visibility());
        assertEquals("private", method(facts, "retrieveLoggedinUserName()").visibility());
        assertEquals("public", method(facts, "handleException(HttpServletRequest,Exception)").visibility());

        VariableFact logger = variable(facts, "Field", "logger");
        assertEquals("Log", logger.type());
        assertEquals("private", logger.modifiers());
        assertEquals("instance", logger.lifetime());
        assertEquals("LogFactory.getLog(ExceptionController.class)", logger.initializer());
        assertEquals("TodoController", logger.owner());
        assertEquals(32, logger.line());

        VariableFact service = variable(facts, "Field", "service");
        assertEquals("TodoService", service.type());
        assertEquals("", service.modifiers());
        assertEquals("instance", service.lifetime());
        assertEquals("", service.initializer());
        assertEquals("TodoController", service.owner());
        assertEquals(35, service.line());

        VariableFact classFact = variable(facts, "Class", "TodoController");
        assertEquals("class", classFact.type());
        assertEquals("public", classFact.modifiers());
        assertEquals("", classFact.lifetime());

        assertEquals(21, facts.imports().size());
        ImportFact simpleDateFormat = importFor(facts, "SimpleDateFormat");
        assertEquals("java.text.SimpleDateFormat", simpleDateFormat.importPath());
        assertEquals(3, simpleDateFormat.line());
        ImportFact exceptionController = importFor(facts, "ExceptionController");
        assertEquals("com.in28minutes.exception.ExceptionController", exceptionController.importPath());
        assertEquals(26, exceptionController.line());

        assertTrue(facts.calls().isEmpty());
        assertTrue(facts.staticInitializations().isEmpty());
    }

    @Test
    void retainsExplicitStaticFieldAndBlockInitializationFacts() throws Exception {
        JavaFacts facts = analyzer.analyze(fixture("TodoService.java"));

        assertEquals(5, facts.methods().size());
        assertEquals(5, facts.staticInitializations().size());

        List<StaticInitFact> staticBlock = facts.staticInitializations().stream()
                .filter(fact -> fact.sourceKind().equals("static initializer"))
                .toList();
        assertEquals(3, staticBlock.size());
        for (StaticInitFact fact : staticBlock) {
            assertEquals("todos", fact.targetField());
            assertTrue(fact.initializer().startsWith("new Todo("));
            assertTrue(fact.line() >= 16 && fact.line() <= 20);
        }

        StaticInitFact todoCount = facts.staticInitializations().stream()
                .filter(fact -> fact.targetField().equals("todoCount"))
                .findFirst()
                .orElseThrow();
        assertEquals("field initializer", todoCount.sourceKind());
        assertEquals("3", todoCount.initializer());

        VariableFact todos = variable(facts, "Field", "todos");
        assertEquals("List<Todo>", todos.type());
        assertEquals("private static", todos.modifiers());
        assertEquals("class", todos.lifetime());
        assertEquals("new ArrayList<Todo>()", todos.initializer());

        VariableFact todoCountField = variable(facts, "Field", "todoCount");
        assertEquals("int", todoCountField.type());
        assertEquals("private static", todoCountField.modifiers());
        assertEquals("class", todoCountField.lifetime());
        assertEquals("3", todoCountField.initializer());
    }

    @Test
    void extractsGenericsInnerClassConstructorAndPackageLessSource() throws Exception {
        Path source = tempDir.resolve("Outer.java");
        Files.writeString(source, """
                import java.util.List;
                import java.util.Map;

                class Outer<T> {
                    private List<T> items;
                    static int created = 0;
                    static {
                        created = 1;
                    }

                    Outer() {
                    }

                    Map<String, T> convert(List<T> input, int count) {
                        return null;
                    }

                    class Inner {
                        T value;
                    }
                }
                """);

        JavaFacts facts = analyzer.analyze(source);

        assertEquals("Class", facts.typeKind());
        assertEquals(2, facts.imports().size());

        VariableFact outer = variable(facts, "Class", "Outer");
        assertEquals("class", outer.type());
        assertEquals("", outer.owner());

        VariableFact items = variable(facts, "Field", "items");
        assertEquals("List<T>", items.type());
        assertEquals("private", items.modifiers());
        assertEquals("instance", items.lifetime());
        assertEquals("Outer", items.owner());

        VariableFact created = variable(facts, "Field", "created");
        assertEquals("static", created.modifiers());
        assertEquals("class", created.lifetime());

        VariableFact inner = variable(facts, "Class", "Inner");
        assertEquals("class", inner.type());
        assertEquals("Outer", inner.owner());

        VariableFact value = variable(facts, "Field", "value");
        assertEquals("T", value.type());
        assertEquals("Inner", value.owner());

        MethodFact constructor = method(facts, "Outer()");
        assertEquals("Outer", constructor.name());
        assertEquals("", constructor.returnType());
        assertTrue(constructor.parameters().isEmpty());

        MethodFact convert = method(facts, "convert(List<T>,int)");
        assertEquals(List.of("input", "count"), parameterNames(convert));
        assertEquals(List.of("List<T>", "int"), parameterTypes(convert));
        assertEquals("Map<String,T>", compact(convert.returnType()));

        assertTrue(facts.typeRefs().stream()
                .anyMatch(ref -> ref.kind().equals("type") && ref.name().equals("List")));
        assertTrue(facts.typeRefs().stream()
                .anyMatch(ref -> ref.kind().equals("type") && ref.name().equals("Map")));
        assertTrue(facts.typeRefs().stream()
                .anyMatch(ref -> ref.kind().equals("type") && ref.name().equals("T")));
        assertTrue(facts.typeRefs().stream()
                .anyMatch(ref -> ref.kind().equals("import") && ref.name().equals("List")));
        assertTrue(facts.typeRefs().stream().allMatch(ref -> ref.resolvedClassId() == null));

        assertEquals(2, facts.staticInitializations().size());
    }

    @Test
    void parsesJava17SyntaxWithoutCompilingUnresolvedTypes() throws Exception {
        Path source = tempDir.resolve("Point.java");
        Files.writeString(source, """
                record Point(int x, int y) {
                    String label() {
                        MissingType dependency = null;
                        return \"\"\"
                                point
                                \"\"\";
                    }
                }
                """);

        JavaFacts facts = analyzer.analyze(source);

        assertEquals("Record", facts.typeKind());
        assertEquals(1, facts.methods().size());
        assertEquals("label", facts.methods().get(0).name());
        assertEquals("String", facts.methods().get(0).returnType());

        VariableFact point = variable(facts, "Record", "Point");
        assertEquals("record", point.type());
        assertTrue(point.owner().isEmpty());

        VariableFact dependency = variable(facts, "Local", "dependency");
        assertEquals("MissingType", dependency.type());
        assertEquals("null", dependency.initializer());
        assertEquals("label()", dependency.owner());

        assertTrue(facts.typeRefs().stream()
                .anyMatch(ref -> ref.kind().equals("type") && ref.name().equals("MissingType")));
    }

    @Test
    void reportsParseFailureWithoutSourceText() throws Exception {
        Path source = tempDir.resolve("Broken.java");
        Files.writeString(source, "class Broken { void x( { String secret = \"do-not-leak\"; }");

        SourceAnalysisException error = assertThrows(SourceAnalysisException.class,
                () -> analyzer.analyze(source));

        assertEquals(source, error.sourceFile());
        assertEquals("PARSE_ERROR", error.errorCode());
        assertTrue(error.location().matches("\\d+:\\d+"));
        assertTrue(error.getMessage().contains("Broken.java"));
        assertTrue(error.getMessage().contains("PARSE_ERROR"));
        assertTrue(error.getMessage().contains(error.location()));
        assertFalse(error.getMessage().contains("do-not-leak"));
        assertFalse(error.getMessage().contains("class Broken"));
    }

    private static Path fixture(String name) throws Exception {
        return Path.of(JavaParserAnalyzerTest.class.getResource("/fixtures/java/" + name).toURI());
    }

    private static MethodFact method(JavaFacts facts, String signature) {
        return facts.methods().stream()
                .filter(method -> method.signature().equals(signature))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing method " + signature));
    }

    private static VariableFact variable(JavaFacts facts, String kind, String name) {
        return facts.variables().stream()
                .filter(variable -> variable.kind().equals(kind) && variable.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + kind + " " + name));
    }

    private static ImportFact importFor(JavaFacts facts, String shortName) {
        return facts.imports().stream()
                .filter(importFact -> importFact.shortName().equals(shortName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing import " + shortName));
    }

    private static List<String> parameterNames(MethodFact method) {
        return method.parameters().stream().map(JavaFacts.ParameterFact::name).toList();
    }

    private static List<String> parameterTypes(MethodFact method) {
        return method.parameters().stream().map(JavaFacts.ParameterFact::type).toList();
    }

    private static String compact(String value) {
        return value.replace(", ", ",");
    }
}
