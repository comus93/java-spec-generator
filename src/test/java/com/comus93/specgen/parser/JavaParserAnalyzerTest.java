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
import com.comus93.specgen.facts.JavaFacts.CallFact;
import com.comus93.specgen.facts.JavaFacts.ImportFact;
import com.comus93.specgen.facts.JavaFacts.MethodFact;
import com.comus93.specgen.facts.JavaFacts.StaticInitFact;
import com.comus93.specgen.facts.JavaFacts.StepFact;
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
        assertEquals(List.of(
                new StepFact(1, "call", 72, "", "result.hasErrors()", ""),
                new StepFact(2, "branch", 72, "",
                        "if (result.hasErrors()) { return \"todo\"; }", ""),
                new StepFact(3, "return", 73, "if:true", "return \"todo\";", ""),
                new StepFact(4, "call", 75, "", "retrieveLoggedinUserName()", ""),
                new StepFact(5, "call", 75, "", "todo.getDesc()", ""),
                new StepFact(6, "instantiate", 75, "", "new Date()", ""),
                new StepFact(7, "call", 75, "",
                        "service.addTodo(retrieveLoggedinUserName(), todo.getDesc(), new Date(), false)",
                        ""),
                new StepFact(8, "call", 77, "", "model.clear()", ""),
                new StepFact(9, "return", 78, "", "return \"redirect:list-todos\";", "")),
                addTodo.steps());
        assertTrue(addTodo.javadoc().isEmpty());

        assertEquals(3, method(facts, "deleteTodo(ModelMap,int)").steps().size());
        assertEquals(3, method(facts, "handleException(HttpServletRequest,Exception)").steps().size());
        assertEquals(4, method(facts, "initBinder(WebDataBinder)").steps().size());
        assertEquals(4, method(facts, "listTodos(ModelMap)").steps().size());
        assertEquals(9, method(facts, "retrieveLoggedinUserName()").steps().size());
        assertEquals(5, method(facts, "showTodoPage(ModelMap)").steps().size());
        assertEquals(4, method(facts, "updateTodo(ModelMap,int)").steps().size());
        assertEquals(7, method(facts, "updateTodo(ModelMap,Todo,BindingResult)").steps().size());

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

        assertEquals(List.of(
                new ImportFact("SimpleDateFormat", "java.text.SimpleDateFormat", 3),
                new ImportFact("Date", "java.util.Date", 4),
                new ImportFact("HttpServletRequest", "javax.servlet.http.HttpServletRequest", 6),
                new ImportFact("Valid", "javax.validation.Valid", 7),
                new ImportFact("Log", "org.apache.commons.logging.Log", 9),
                new ImportFact("LogFactory", "org.apache.commons.logging.LogFactory", 10),
                new ImportFact("Autowired", "org.springframework.beans.factory.annotation.Autowired", 11),
                new ImportFact("CustomDateEditor", "org.springframework.beans.propertyeditors.CustomDateEditor", 12),
                new ImportFact("SecurityContextHolder", "org.springframework.security.core.context.SecurityContextHolder", 13),
                new ImportFact("UserDetails", "org.springframework.security.core.userdetails.UserDetails", 14),
                new ImportFact("Controller", "org.springframework.stereotype.Controller", 15),
                new ImportFact("ModelMap", "org.springframework.ui.ModelMap", 16),
                new ImportFact("BindingResult", "org.springframework.validation.BindingResult", 17),
                new ImportFact("WebDataBinder", "org.springframework.web.bind.WebDataBinder", 18),
                new ImportFact("ExceptionHandler", "org.springframework.web.bind.annotation.ExceptionHandler", 19),
                new ImportFact("InitBinder", "org.springframework.web.bind.annotation.InitBinder", 20),
                new ImportFact("RequestMapping", "org.springframework.web.bind.annotation.RequestMapping", 21),
                new ImportFact("RequestMethod", "org.springframework.web.bind.annotation.RequestMethod", 22),
                new ImportFact("RequestParam", "org.springframework.web.bind.annotation.RequestParam", 23),
                new ImportFact("SessionAttributes", "org.springframework.web.bind.annotation.SessionAttributes", 24),
                new ImportFact("ExceptionController", "com.in28minutes.exception.ExceptionController", 26)),
                facts.imports());

        List<TypeRefFact> importTypeRefs = facts.typeRefs().stream()
                .filter(ref -> ref.kind().equals("import"))
                .toList();
        assertEquals(List.of(
                new TypeRefFact(3, "import", "SimpleDateFormat", null),
                new TypeRefFact(4, "import", "Date", null),
                new TypeRefFact(6, "import", "HttpServletRequest", null),
                new TypeRefFact(7, "import", "Valid", null),
                new TypeRefFact(9, "import", "Log", null),
                new TypeRefFact(10, "import", "LogFactory", null),
                new TypeRefFact(11, "import", "Autowired", null),
                new TypeRefFact(12, "import", "CustomDateEditor", null),
                new TypeRefFact(13, "import", "SecurityContextHolder", null),
                new TypeRefFact(14, "import", "UserDetails", null),
                new TypeRefFact(15, "import", "Controller", null),
                new TypeRefFact(16, "import", "ModelMap", null),
                new TypeRefFact(17, "import", "BindingResult", null),
                new TypeRefFact(18, "import", "WebDataBinder", null),
                new TypeRefFact(19, "import", "ExceptionHandler", null),
                new TypeRefFact(20, "import", "InitBinder", null),
                new TypeRefFact(21, "import", "RequestMapping", null),
                new TypeRefFact(22, "import", "RequestMethod", null),
                new TypeRefFact(23, "import", "RequestParam", null),
                new TypeRefFact(24, "import", "SessionAttributes", null),
                new TypeRefFact(26, "import", "ExceptionController", null)),
                importTypeRefs);

        assertEquals(27, facts.calls().size());
        assertEquals(new CallFact(32, "[field-initializer]", "getLog", ""), facts.calls().get(0));
        assertTrue(facts.calls().stream().allMatch(call -> call.targetId().isEmpty()));
        assertTrue(facts.staticInitializations().isEmpty());
    }

    @Test
    void retainsExplicitStaticFieldAndBlockInitializationFacts() throws Exception {
        JavaFacts facts = analyzer.analyze(fixture("TodoService.java"));

        assertEquals(5, facts.methods().size());
        assertEquals(5, facts.staticInitializations().size());
        assertEquals(15, facts.calls().size());
        assertEquals(List.of(
                new CallFact(16, "[static initializer]", "add", ""),
                new CallFact(18, "[static initializer]", "add", ""),
                new CallFact(19, "[static initializer]", "add", "")),
                facts.calls().subList(0, 3));
        assertTrue(facts.calls().stream().allMatch(call -> call.targetId().isEmpty()));

        assertEquals(2, method(facts, "addTodo(String,String,Date,boolean)").steps().size());
        MethodFact deleteTodo = method(facts, "deleteTodo(int)");
        assertEquals(9, deleteTodo.steps().size());
        assertEquals("while:body>if:true", deleteTodo.steps().get(8).branchPath());
        assertEquals(5, method(facts, "retrieveTodo(int)").steps().size());
        assertEquals(8, method(facts, "retrieveTodos(String)").steps().size());
        assertEquals(2, method(facts, "updateTodo(Todo)").steps().size());

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
    void preservesWildcardAndStaticImportPaths() throws Exception {
        Path source = tempDir.resolve("Imported.java");
        Files.writeString(source, """
                import java.util.*;
                import static java.lang.Math.max;
                import static java.util.Collections.*;
                import java.util.Map;

                class Imported {
                }
                """);

        JavaFacts facts = analyzer.analyze(source);

        assertEquals(List.of(
                new ImportFact("*", "java.util.*", 1),
                new ImportFact("max", "java.lang.Math.max", 2),
                new ImportFact("*", "java.util.Collections.*", 3),
                new ImportFact("Map", "java.util.Map", 4)),
                facts.imports());

        List<TypeRefFact> importRefs = facts.typeRefs().stream()
                .filter(ref -> ref.kind().equals("import"))
                .toList();
        assertEquals(List.of("*", "max", "*", "Map"),
                importRefs.stream().map(TypeRefFact::name).toList());
        assertEquals(List.of(1, 2, 3, 4),
                importRefs.stream().map(TypeRefFact::line).toList());
        assertTrue(importRefs.stream().allMatch(ref -> ref.resolvedClassId() == null));
    }

    @Test
    void keepsStaticTargetsOnlyForDeclaredStaticFieldsAndRetainsFullExpressions() throws Exception {
        Path source = tempDir.resolve("StaticTargets.java");
        Files.writeString(source, """
                class StaticTargets {
                    static java.util.List<String> declared = new java.util.ArrayList<>();
                    java.util.List<String> instance = new java.util.ArrayList<>();

                    static {
                        declared.add(new String("ok"));
                        this.declared.add(new String("qualified"));
                        instance.add(new String("instance"));
                        SomeUtil.init(new Widget());
                        this.instance.add(new Widget());
                        declared = new java.util.ArrayList<>();
                        instance = new java.util.ArrayList<>();
                    }
                }
                """);

        JavaFacts facts = analyzer.analyze(source);

        assertEquals("declared", staticFactAt(facts, 2).targetField());
        assertEquals("field initializer", staticFactAt(facts, 2).sourceKind());
        assertEquals("new java.util.ArrayList<>()", staticFactAt(facts, 2).initializer());

        List<StaticInitFact> blockFacts = facts.staticInitializations().stream()
                .filter(fact -> fact.sourceKind().equals("static initializer"))
                .toList();
        assertEquals(7, blockFacts.size());

        assertStaticFact(blockFacts, 6, "declared", "new String(\"ok\")");
        assertStaticFact(blockFacts, 7, "declared", "new String(\"qualified\")");
        assertStaticFact(blockFacts, 8, "", "instance.add(new String(\"instance\"))");
        assertStaticFact(blockFacts, 9, "", "SomeUtil.init(new Widget())");
        assertStaticFact(blockFacts, 10, "", "this.instance.add(new Widget())");
        assertStaticFact(blockFacts, 11, "declared", "new java.util.ArrayList<>()");
        assertStaticFact(blockFacts, 12, "", "instance = new java.util.ArrayList<>()");
    }

    @Test
    void rejectsQualifiedReceiversThatOnlyShareAStaticFieldName() throws Exception {
        Path source = tempDir.resolve("QualifiedTargets.java");
        Files.writeString(source, """
                class QualifiedTargets {
                    static java.util.List<String> declared = new java.util.ArrayList<>();

                    static {
                        other.declared.add(new Widget());
                        other.declared = new java.util.ArrayList<>();
                        this.declared.add(new Widget());
                        QualifiedTargets.declared.add(new Widget());
                        declared.add(new Widget());
                    }
                }
                """);

        JavaFacts facts = analyzer.analyze(source);

        List<StaticInitFact> blockFacts = facts.staticInitializations().stream()
                .filter(fact -> fact.sourceKind().equals("static initializer"))
                .toList();
        assertEquals(5, blockFacts.size());

        assertStaticFact(blockFacts, 5, "", "other.declared.add(new Widget())");
        assertStaticFact(blockFacts, 6, "", "other.declared = new java.util.ArrayList<>()");
        assertStaticFact(blockFacts, 7, "declared", "new Widget()");
        assertStaticFact(blockFacts, 8, "declared", "new Widget()");
        assertStaticFact(blockFacts, 9, "declared", "new Widget()");
    }

    @Test
    void preservesObjectCreationGenericArgumentTypesWithoutResolution() throws Exception {
        Path source = tempDir.resolve("GenericCreation.java");
        Files.writeString(source, """
                import java.util.HashMap;

                class GenericCreation<T> {
                    void run() {
                        consume(new HashMap<String, Todo>());
                        Object other = new java.util.ArrayList<Todo>();
                    }

                    void consume(Object value) {
                    }
                }
                """);

        JavaFacts facts = analyzer.analyze(source);

        List<TypeRefFact> createdTypes = facts.typeRefs().stream()
                .filter(ref -> ref.kind().equals("new"))
                .toList();
        assertEquals(List.of("HashMap", "java.util.ArrayList"),
                createdTypes.stream().map(TypeRefFact::name).toList());
        assertTrue(createdTypes.stream().allMatch(ref -> ref.resolvedClassId() == null));

        assertTypeRefAt(facts, 5, "String");
        assertTypeRefAt(facts, 5, "Todo");
        assertTypeRefAt(facts, 6, "Todo");
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

    private static StaticInitFact staticFactAt(JavaFacts facts, int line) {
        return facts.staticInitializations().stream()
                .filter(fact -> fact.line() == line)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing static fact at line " + line));
    }

    private static void assertStaticFact(List<StaticInitFact> facts, int line,
            String targetField, String initializer) {
        StaticInitFact fact = facts.stream()
                .filter(candidate -> candidate.line() == line)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing static fact at line " + line));
        assertEquals(targetField, fact.targetField());
        assertEquals(initializer, fact.initializer());
    }

    private static void assertTypeRefAt(JavaFacts facts, int line, String name) {
        assertTrue(facts.typeRefs().stream()
                        .anyMatch(ref -> ref.kind().equals("type") && ref.line() == line && ref.name().equals(name)),
                () -> "missing type ref " + name + " at line " + line);
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
