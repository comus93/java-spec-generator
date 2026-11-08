package com.comus93.specgen.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.facts.JspFacts;
import com.comus93.specgen.facts.JspFacts.EventFact;
import com.comus93.specgen.facts.JspFacts.FlowFact;
import com.comus93.specgen.facts.JspFacts.ScreenFieldFact;

class PmdJspAnalyzerTest {

    private final PmdJspAnalyzer analyzer = new PmdJspAnalyzer();

    @TempDir
    Path tempDir;

    private static Path fixture(String name) {
        return Path.of("src/test/resources/fixtures/jsp", name);
    }

    @Test
    void extractsFormFieldsAndKeepsUnknownReferencesNull() throws Exception {
        Path source = fixture("todo.jsp");
        JspFacts facts = analyzer.analyze(source);

        assertEquals(source, facts.sourceFile());
        ScreenFieldFact desc = field(facts, "Description");
        assertEquals("todo", desc.object());
        assertEquals("desc", desc.referenceField());
        assertEquals(Boolean.TRUE, desc.editable());
        assertEquals("form:input", desc.fieldType());
        assertEquals("required", desc.rawAttributes().get("required"));
        assertEquals("desc", desc.rawAttributes().get("path"));
        assertNull(desc.i18nId());
        assertTrue(desc.line() > 0);

        ScreenFieldFact id = field(facts, "id");
        assertEquals(Boolean.FALSE, id.editable());
        assertEquals("id", id.rawAttributes().get("path"));
        assertEquals("id", id.referenceField());
        assertEquals("form:hidden", id.fieldType());
        ScreenFieldFact submit = field(facts, "Submit");
        assertEquals("todo", submit.object());
        assertNull(submit.referenceField());
        assertEquals("input", submit.fieldType());
        assertTrue(facts.events().isEmpty());
        assertTrue(facts.constants().isEmpty());
        assertFalse(facts.flows().isEmpty());
        assertEquals(List.of(1, 2, 3), facts.flows().stream()
                .limit(3).map(FlowFact::sequence).toList());
        assertTrue(facts.flows().stream().allMatch(flow -> flow.line() > 0));
        for (int i = 1; i < facts.flows().size(); i++) {
            assertTrue(facts.flows().get(i - 1).line() <= facts.flows().get(i).line(),
                    "static flows must follow source order");
        }
    }

    @Test
    void extractsForEachElAndExactlyThreeAnchorClicksWithoutJavaScript() throws Exception {
        JspFacts facts = analyzer.analyze(fixture("list-todos.jsp"));

        FlowFact loop = facts.flows().stream()
                .filter(flow -> flow.tagOrFile().equals("c:forEach"))
                .findFirst().orElseThrow();
        assertEquals("${todos}", loop.attributes().get("items"));
        assertEquals("todo", loop.attributes().get("var"));
        assertTrue(facts.flows().stream()
                .anyMatch(flow -> flow.tagOrFile().contains("${todo.desc}")
                        || flow.attributes().values().stream().anyMatch(value -> value.contains("${todo.targetDate}"))));

        List<ScreenFieldFact> fields = facts.screenFields();
        assertEquals(List.of("<spring:message>", "Description", "Target Date", "Is Completed?", "Update", "Delete", "Add"),
                fields.stream().map(ScreenFieldFact::fieldName).toList());
        assertEquals("todo", fields.get(1).object());
        assertEquals("${todo.desc}", fields.get(1).referenceField());
        assertEquals("${todo.targetDate}", fields.get(2).referenceField());
        assertEquals("fmt:formatDate", fields.get(2).fieldType());
        assertEquals("${todo.id}", fields.get(4).referenceField());
        assertEquals("a", fields.get(4).fieldType());
        assertNull(fields.get(6).object());
        assertEquals("todo.caption", fields.get(0).i18nId());

        List<EventFact> events = facts.events();
        assertEquals(3, events.size(), "only three <a href> tags produce LINK_CLICK");
        assertEquals(List.of("LINK_CLICK", "LINK_CLICK", "LINK_CLICK"),
                events.stream().map(EventFact::type).toList());
        assertEquals(List.of("Update", "Delete", "Add"),
                events.stream().map(EventFact::name).toList());
        assertEquals(List.of("/update-todo?id=${todo.id}", "/delete-todo?id=${todo.id}", "/add-todo"),
                events.stream().map(EventFact::description).toList());
        assertTrue(events.stream().allMatch(event -> event.returnValue() == null));
        assertTrue(events.get(0).line() <= events.get(1).line());
        assertTrue(events.get(1).line() <= events.get(2).line());
    }

    @Test
    void preservesCustomTagAttributesAndDoesNotInterpretOnclickOrScript() throws Exception {
        JspFacts facts = analyzer.analyze(fixture("custom-legacy.jsp"));
        assertTrue(facts.flows().stream().anyMatch(flow -> flow.tagOrFile().equals("x:widget")
                && "${user.code}".equals(flow.attributes().get("data-ref"))));
        assertEquals(1, facts.events().size());
        assertEquals("Open", facts.events().get(0).name());
        assertEquals("/view?id=${user.code}", facts.events().get(0).description());
    }

    @Test
    void preservesNestedFormControlsMessagesAndSubmitInsideTableCells() throws Exception {
        JspFacts facts = analyzer.analyze(customJsp("nested-controls.jsp",
                "<table><thead><tr><th>Column</th></tr></thead><tbody><tr><td>"
                        + "<form:form commandName=\"todo\">"
                        + "<form:label path=\"name\">Name</form:label>"
                        + "<form:input path=\"name\"/>"
                        + "<spring:message code=\"caption\"/>"
                        + "<input type=\"submit\" value=\"Save\"/>"
                        + "</form:form></td></tr></tbody></table>"));

        assertEquals(List.of("Name", "<spring:message>", "Save"),
                facts.screenFields().stream().map(ScreenFieldFact::fieldName).toList());
        assertEquals("name", field(facts, "Name").referenceField());
        assertEquals("todo", field(facts, "Save").object());
    }

    @Test
    void preservesPlainInputSelectAndTextareaControls() throws Exception {
        JspFacts facts = analyzer.analyze(customJsp("plain-controls.jsp",
                "<input name=\"plainText\" type=\"text\"/>"
                        + "<select name=\"plainSelect\"><option>A</option></select>"
                        + "<textarea name=\"plainArea\"></textarea>"));

        assertEquals(List.of("plainText", "plainSelect", "plainArea"),
                facts.screenFields().stream().map(ScreenFieldFact::fieldName).toList());
        assertEquals("text", field(facts, "plainText").fieldType());
        assertEquals("select", field(facts, "plainSelect").fieldType());
        assertEquals("textarea", field(facts, "plainArea").fieldType());
    }

    @Test
    void keepsBlankHeaderPositionsAndIsolatesNestedTables() throws Exception {
        JspFacts facts = analyzer.analyze(customJsp("nested-table.jsp",
                "<table><thead><tr><th>A</th><th></th></tr></thead><tbody>"
                        + "<tr><td>${one}</td><td>${two}</td></tr>"
                        + "<tr><td><table><thead><tr><th>Inner</th></tr></thead><tbody>"
                        + "<tr><td>${inner}</td></tr></tbody></table></td><td></td></tr>"
                        + "</tbody></table>"));

        assertEquals(List.of("A", "${two}", "Inner"),
                facts.screenFields().stream().map(ScreenFieldFact::fieldName).toList());
        assertEquals("${two}", field(facts, "${two}").referenceField());
        assertEquals("${inner}", field(facts, "Inner").referenceField());
        assertTrue(facts.screenFields().stream()
                .noneMatch(field -> "A".equals(field.fieldName())
                        && "${inner}".equals(field.referenceField())));
    }

    @Test
    void scopesFormLabelsToTheirOwnFormAndAvoidsAmbiguousPairing() throws Exception {
        JspFacts facts = analyzer.analyze(customJsp("form-labels.jsp",
                "<form:form commandName=\"first\">"
                        + "<form:label path=\"name\">First Name</form:label>"
                        + "<form:input path=\"name\"/>"
                        + "</form:form>"
                        + "<form:form commandName=\"second\">"
                        + "<form:input path=\"name\"/>"
                        + "<form:label path=\"name\">Second Name</form:label>"
                        + "<form:input path=\"name\"/>"
                        + "</form:form>"
                        + "<form:form commandName=\"ambiguous\">"
                        + "<form:label path=\"value\">One</form:label>"
                        + "<form:label path=\"value\">Two</form:label>"
                        + "<form:input path=\"value\"/>"
                        + "</form:form>"));

        assertEquals(List.of("First Name", "name", "Second Name", "value"),
                facts.screenFields().stream().map(ScreenFieldFact::fieldName).toList());
    }

    @Test
    void preservesOriginalRawAttributeText() throws Exception {
        JspFacts facts = analyzer.analyze(customJsp("raw-attributes.jsp",
                "<form:input path='single' data-x=\"double\" disabled />"
                        + "<a href='/go?id=${x}'>Go</a>"));

        ScreenFieldFact input = field(facts, "single");
        assertEquals("path='single'", input.rawAttributeText().get("path"));
        assertEquals("data-x=\"double\"", input.rawAttributeText().get("data-x"));
        assertEquals("disabled", input.rawAttributeText().get("disabled"));

        ScreenFieldFact link = field(facts, "Go");
        assertEquals("href='/go?id=${x}'", link.rawAttributeText().get("href"));
        assertEquals("/go?id=${x}", facts.events().get(0).description());
    }

    @Test
    void acceptsHtml5DoctypeAfterPageAndTaglibDirectivesWithoutChangingSourceLines() throws Exception {
        String source = String.join("\n",
                "<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\" %>",
                "<%@ taglib prefix=\"c\" uri=\"http://java.sun.com/jsp/jstl/core\" %>",
                "<%@ taglib prefix=\"fmt\" uri=\"http://java.sun.com/jsp/jstl/fmt\" %>",
                "<!DOCTYPE html>",
                "<html>",
                "<head><title>Insert title here</title></head>",
                "<body><input name=\"accountId\" type=\"text\"/></body>",
                "</html>");
        Path path = customJsp("directives-doctype.jsp", source);

        JspFacts facts = analyzer.analyze(path);

        assertEquals(source, Files.readString(path, StandardCharsets.UTF_8),
                "the original JSP must never be rewritten");
        assertEquals("accountId", field(facts, "accountId").fieldName());
        assertTrue(facts.flows().stream().anyMatch(flow ->
                "html".equalsIgnoreCase(flow.tagOrFile()) && flow.line() == 5));
        assertTrue(facts.flows().stream().anyMatch(flow ->
                "input".equalsIgnoreCase(flow.tagOrFile()) && flow.line() == 7));
    }

    @Test
    void acceptsDoctypeAtStartWithoutChangingExistingParserBehavior() throws Exception {
        Path path = customJsp("doctype-first.jsp",
                "<!DOCTYPE html>\n<%@ page language=\"java\" %>\n"
                        + "<html><body><input name=\"first\" /></body></html>");

        JspFacts facts = analyzer.analyze(path);

        assertEquals("first", field(facts, "first").fieldName());
        assertTrue(facts.flows().stream().anyMatch(flow ->
                "html".equalsIgnoreCase(flow.tagOrFile()) && flow.line() == 3));
    }

    @Test
    void doesNotSilenceUnsupportedDocumentTypeDeclarations() throws Exception {
        Path path = customJsp("unsupported-doctype.jsp",
                "<%@ page language=\"java\" %>\n<!DOCTYPE not-html>\n<html/>");

        SourceAnalysisException error = assertThrows(
                SourceAnalysisException.class, () -> analyzer.analyze(path));

        assertEquals(SourceAnalysisException.PARSE_ERROR, error.errorCode());
    }

    @Test
    void reportsFileReadFailureWithoutSwallowingIt() {
        Path missing = tempDir.resolve("missing.jsp");
        SourceAnalysisException error = assertThrows(SourceAnalysisException.class,
                () -> analyzer.analyze(missing));
        assertEquals(missing, error.sourceFile());
        assertEquals(SourceAnalysisException.READ_ERROR, error.errorCode());
    }

    private Path customJsp(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static ScreenFieldFact field(JspFacts facts, String fieldName) {
        return facts.screenFields().stream()
                .filter(field -> fieldName.equals(field.fieldName()))
                .findFirst().orElseThrow();
    }
}
