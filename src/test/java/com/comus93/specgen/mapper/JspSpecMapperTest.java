package com.comus93.specgen.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.comus93.specgen.config.*;
import com.comus93.specgen.excel.*;
import com.comus93.specgen.facts.JspFacts;
import com.comus93.specgen.parser.PmdJspAnalyzer;
import com.comus93.specgen.scan.SourceJob;

class JspSpecMapperTest {
    private final Path root = Path.of("src/test/resources/fixtures/jsp");
    private final ModuleSelection module = new ModuleSelection(root, "sub", "views");
    private final JobConfig config = new JobConfig(SourceMode.JSP, "system", "author",
            LocalDate.of(2020, 1, 2), "program", "overview", Path.of("out"), OutputFormat.XLSX, List.of(module));

    private WorkbookModel map(String name) throws Exception {
        Path source = root.resolve(name);
        return new JspSpecMapper().map(new PmdJspAnalyzer().analyze(source), config,
                new SourceJob(module, source, Path.of(name), Path.of("out", name + ".xlsx")));
    }

    @Test void mapsFiveSheetsAndThreeActualLinks() throws Exception {
        WorkbookModel book = map("list-todos.jsp");
        assertEquals(List.of("01_표제부", "02_화면필드정의", "03_최소흐름", "04_원본경로", "상세 사양"),
                book.sheets().stream().map(SheetModel::name).toList());
        assertEquals(List.of("순번", "태그 / 파일", "속성"), book.sheets().get(2).rows().get(2).values());
        assertEquals(List.of("list-todos.jsp"), book.sheets().get(3).rows().get(3).values());
        List<SheetRow> rows = book.sheets().get(4).rows();
        assertEquals(List.of("상수 및 전역 변수", "Function 및 Event", "명세 변경 사항"), rows.stream()
                .filter(r -> r.role() == RowKind.SECTION).map(r -> r.values().get(0)).toList());
        List<List<Object>> events = rows.stream().filter(r -> r.role() == RowKind.DATA && !r.values().isEmpty()
                && "LINK_CLICK".equals(r.values().get(0))).map(SheetRow::values).toList();
        assertEquals(3, events.size());
        assertEquals(java.util.Arrays.asList("LINK_CLICK", "Update", "href=\"/update-todo?id=${todo.id}\"", null), events.get(0));
        assertEquals(java.util.Arrays.asList("LINK_CLICK", "Delete", "href=\"/delete-todo?id=${todo.id}\"", null), events.get(1));
        assertEquals(java.util.Arrays.asList("LINK_CLICK", "Add", "href=\"/add-todo\"", null), events.get(2));
        assertTrue(book.sheets().stream().allMatch(s -> s.rows().get(0).role() == RowKind.TITLE && s.rows().get(1).role() == RowKind.EMPTY));
    }

    @Test void mapsTableHeadersAndLinksToSourceGroundedScreenFields() throws Exception {
        WorkbookModel book = map("list-todos.jsp");
        List<List<Object>> fields = book.sheets().get(1).rows().stream()
                .filter(r -> r.role() == RowKind.DATA).map(SheetRow::values).toList();
        assertEquals(List.of("<spring:message>", "Description", "Target Date", "Is Completed?", "Update", "Delete", "Add"),
                fields.stream().map(row -> row.get(0)).toList());
        assertEquals("todo", fields.get(1).get(1));
        assertEquals("${todo.desc}", fields.get(1).get(2));
        assertEquals("EL", fields.get(1).get(5));
        assertEquals("${todo.targetDate}", fields.get(2).get(2));
        assertEquals("fmt:formatDate", fields.get(2).get(5));
        assertEquals("${todo.done}", fields.get(3).get(2));
        assertEquals("${todo.id}", fields.get(4).get(2));
        assertEquals("a", fields.get(4).get(5));
        assertNull(fields.get(6).get(1));
        assertEquals("todo.caption", fields.get(0).get(6));
    }

    @Test void mapsFormFactsWithoutInventingFields() throws Exception {
        WorkbookModel book = map("todo.jsp");
        List<SheetRow> rows = book.sheets().get(1).rows();
        assertEquals(List.of("화면 필드", "Object", "참조 필드", "Edit", "Remark (속성 원문)", "항목 유형", "다국어 ID"), rows.get(2).values());
        SheetRow desc = rows.stream().filter(r -> !r.values().isEmpty() && "Description".equals(r.values().get(0))).findFirst().orElseThrow();
        assertEquals("todo", desc.values().get(1));
        assertEquals("desc", desc.values().get(2));
        assertNull(desc.values().get(3));
        assertTrue(desc.values().get(4).toString().contains("path=\"desc\""));
        assertEquals("form:input", desc.values().get(5));
        assertNull(desc.values().get(6));
        assertTrue(rows.stream().anyMatch(r -> !r.values().isEmpty() && "Submit".equals(r.values().get(0))));
        assertEquals(10, book.sheets().get(0).rows().stream().filter(r -> r.role() == RowKind.DATA).count());
        assertTrue(book.sheets().get(2).rows().stream().filter(r -> r.role() == RowKind.DATA).allMatch(r -> r.values().size() == 3));
    }
}
