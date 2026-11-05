package com.comus93.specgen.parser;

import static org.junit.jupiter.api.Assertions.*;

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
        ScreenFieldFact desc = field(facts, "desc");
        assertEquals("todo", desc.object());
        assertNull(desc.referenceField());
        assertEquals(Boolean.TRUE, desc.editable());
        assertEquals("text", desc.fieldType());
        assertEquals("required", desc.rawAttributes().get("required"));
        assertEquals("desc", desc.rawAttributes().get("path"));
        assertNull(desc.i18nId());
        assertTrue(desc.line() > 0);

        ScreenFieldFact id = field(facts, "id");
        assertEquals(Boolean.FALSE, id.editable());
        assertEquals("id", id.rawAttributes().get("path"));
        assertNull(id.referenceField());
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
    void reportsFileReadFailureWithoutSwallowingIt() {
        Path missing = tempDir.resolve("missing.jsp");
        SourceAnalysisException error = assertThrows(SourceAnalysisException.class,
                () -> analyzer.analyze(missing));
        assertEquals(missing, error.sourceFile());
        assertEquals(SourceAnalysisException.READ_ERROR, error.errorCode());
    }

    private static ScreenFieldFact field(JspFacts facts, String fieldName) {
        return facts.screenFields().stream()
                .filter(field -> fieldName.equals(field.fieldName()))
                .findFirst().orElseThrow();
    }
}
