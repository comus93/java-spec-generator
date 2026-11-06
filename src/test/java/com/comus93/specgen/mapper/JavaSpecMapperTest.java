package com.comus93.specgen.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.config.*;
import com.comus93.specgen.excel.*;
import com.comus93.specgen.facts.JavaFacts;
import com.comus93.specgen.facts.JavaFacts.*;
import com.comus93.specgen.parser.JavaParserAnalyzer;
import com.comus93.specgen.scan.SourceJob;

class JavaSpecMapperTest {
    @TempDir Path temp;
    private final JavaSpecMapper mapper = new JavaSpecMapper();

    @Test
    void controllerMatchesGoldenSheetHeadersMethodOrderAndCoreValues() throws Exception {
        compareGolden("TodoController", 9);
    }

    @Test
    void serviceMatchesGoldenAndIncludesStaticInitializationSheet() throws Exception {
        compareGolden("TodoService", 10);
        JavaFacts facts = analyze("TodoService");
        WorkbookModel model = map(facts, null);
        assertEquals(facts.staticInitializations().stream()
                .map(f -> cells(f.targetField(), f.sourceKind(), f.initializer())).toList(),
                data(sheet(model, "06_정적초기화")));
    }

    @Test
    void mapsAllFactsWithoutSourceLocationsOrInventedValues() throws Exception {
        JavaFacts facts = analyze("TodoController");
        WorkbookModel model = map(facts, null);
        assertEquals(facts.variables().stream().map(v -> cells(v.kind(), v.name(), v.type(),
                v.modifiers(), v.lifetime(), v.initializer(), ownerName(v.owner(), facts))).toList(),
                data(sheet(model, "02_OBJ정의")));
        assertEquals(facts.calls().stream().map(c -> cells(c.callerName(), c.calledName(), c.targetId())).toList(),
                data(sheet(model, "05_호출관계")));
        assertEquals(facts.typeRefs().stream().map(t -> cells(t.kind(), t.name(), t.resolvedClassId())).toList(),
                data(sheet(model, "06_타입참조")));
        List<List<Object>> steps = data(sheet(model, "04_처리단계"));
        for (MethodFact method : facts.methods()) {
            assertEquals(method.steps().stream().map(s -> cells(method.signature(), s.sequence(),
                    s.kind(), s.branchPath(), s.sourceSnippet(), s.targetId())).toList(),
                    steps.stream().filter(r -> r.get(0).equals(method.signature())).toList());
        }
        for (List<Object> row : data(sheet(model, "상세 사양"))) {
            assertTrue(blank(row.get(2)));
            assertTrue(blank(row.get(3)));
            assertFalse(row.get(4).toString().matches("(?s).*\\[L\\d+].*"));
        }
        for (SheetModel s : model.sheets()) {
            assertEquals(RowKind.TITLE, s.rows().get(0).role());
            assertEquals(RowKind.EMPTY, s.rows().get(1).role());
            assertEquals(RowKind.HEADER, s.rows().get(2).role());
            for (Object value : s.rows().get(2).values()) {
                assertFalse(List.of("소스 행", "시작행", "끝행", "처리 단계 수", "AST 노드 종류", "AST 깊이")
                        .contains(value));
            }
        }
    }

    @Test
    void titleUsesUserConfigurationAndDateOverride() throws Exception {
        JavaFacts facts = analyze("TodoController");
        LocalDate date = LocalDate.of(2020, 2, 3);
        List<List<Object>> title = data(map(facts, date).sheets().get(0));
        assertEquals(List.of("시스템", "서브 시스템", "모듈", "프로그램 ID", "작성자", "작성일",
                "프로그램명", "개발 유형", "프로그램 유형", "프로그램 개요"),
                title.stream().map(r -> r.get(1)).toList());
        assertEquals(List.of("SYS", "SUB", "표시 모듈", "TodoController", "작성자", date,
                "입력 프로그램", "Java", "Class", "입력 개요"), title.stream().map(r -> r.get(2)).toList());
        assertEquals(List.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10"),
                title.stream().map(r -> r.get(0)).toList());
    }

    @Test
    void defaultsDateToFileModificationInLocalZoneAndLeavesUnreadableDateBlank() throws Exception {
        Path source = temp.resolve("Dates.java");
        Files.writeString(source, "class Dates {}");
        Instant instant = Instant.parse("2001-02-03T23:45:00Z");
        Files.setLastModifiedTime(source, FileTime.from(instant));
        JavaFacts facts = new JavaParserAnalyzer().analyze(source);
        assertEquals(instant.atZone(ZoneId.systemDefault()).toLocalDate(),
                data(map(facts, null).sheets().get(0)).get(5).get(2));
        Files.delete(source);
        assertTrue(blank(data(map(facts, null).sheets().get(0)).get(5).get(2)));
        assertEquals(LocalDate.of(1999, 1, 1),
                data(map(facts, LocalDate.of(1999, 1, 1)).sheets().get(0)).get(5).get(2));
    }

    @Test
    void detailNormalizesOnlyTokensAndPreservesExpressionsJavadocAndKnownIds() {
        Path source = temp.resolve("Tokens.java");
        MethodFact method = new MethodFact("run", "run(String)", List.of(new ParameterFact("text", "String")),
                "void", "private", 900, 999, "Original JavaDoc.", List.of(
                new StepFact(1, "INSTANTIATE", 901, "", "new Thing(\"[L123]\")", ""),
                new StepFact(2, "ASSIGNMENT", 902, "if:true", "x = \"=SUM(1,1)\"", ""),
                new StepFact(3, "BRANCH", 903, "", "if (x != null) use(x);", ""),
                new StepFact(4, "call", 904, "if:true>while:body", "use(x)", "known")));
        JavaFacts facts = new JavaFacts(source, "Class", List.of(), List.of(method),
                List.of(new CallFact(904, "run(String)", "use", "known")),
                List.of(new TypeRefFact(901, "new", "Thing", "known.Type")), List.of(), List.of());
        WorkbookModel model = map(facts, null);
        assertEquals(cells("run", "private", null, "Original JavaDoc.",
                "CREATE  new Thing(\"[L123]\")\n  SET [if:true]  x = \"=SUM(1,1)\"\n"
                + "IF  if (x != null) use(x);\n  CALL [if:true>while:body]  use(x)", "String text", "void"),
                data(sheet(model, "상세 사양")).get(0));
        assertEquals("known", data(sheet(model, "04_처리단계")).get(3).get(5));
        assertEquals("known.Type", data(sheet(model, "06_타입참조")).get(0).get(2));
        assertEquals(model, map(facts, null));
    }

    @Test
    void emptyFactsProduceBlankDataAndSingleModuleRelativePathColumn() {
        Path source = temp.resolve("nested/Empty.java");
        JavaFacts facts = new JavaFacts(source, "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        ModuleSelection module = new ModuleSelection(temp, "", "");
        JobConfig config = new JobConfig(SourceMode.JAVA, "", "", null, "", "", temp.resolve("out"),
                OutputFormat.XLS, List.of(module));
        SourceJob job = new SourceJob(module, source, Path.of("nested/Empty.java"), temp.resolve("out/Empty.xls"));
        WorkbookModel model = mapper.map(facts, config, job);
        assertEquals(List.of(cells("nested/Empty.java")), data(sheet(model, "08_원본경로")));
        for (SheetModel s : model.sheets()) {
            if (!s.name().equals("01_표제부") && !s.name().equals("08_원본경로")) {
                assertEquals(1, data(s).size());
                assertTrue(data(s).get(0).stream().allMatch(JavaSpecMapperTest::blank));
            }
        }
        assertTrue(blank(data(model.sheets().get(0)).get(6).get(2)));
        assertTrue(blank(data(model.sheets().get(0)).get(9).get(2)));
    }

    @Test
    void writtenWorkbookKeepsGoldenTitleBlankAndHeaderRows() throws Exception {
        JavaFacts facts = analyze("TodoController");
        WorkbookModel model = map(facts, LocalDate.of(2019, 10, 28));
        Path output = temp.resolve("physical-layout.xlsx");
        new PoiWorkbookWriter().write(model, output, OutputFormat.XLSX);

        try (Workbook workbook = WorkbookFactory.create(output.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(model.sheets().get(0).rows().get(0).values().get(0),
                    sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("", new DataFormatter().formatCellValue(sheet.getRow(1).getCell(0)));
            assertNull(sheet.getRow(2), "Golden leaves physical row 3 as a visual spacer");
            assertEquals(model.sheets().get(0).rows().get(2).values().get(0),
                    sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("01", sheet.getRow(4).getCell(0).getStringCellValue());
        }
    }

    private void compareGolden(String name, int count) throws Exception {
        JavaFacts facts = analyze(name);
        WorkbookModel model = map(facts, LocalDate.of(2019, 10, 28));
        Path goldenPath = Path.of("docs/golden/JavaParser_" + name + "_ProgramSpec_Grayscale.xlsx");
        byte[] original = Files.readAllBytes(goldenPath);
        try (var input = Files.newInputStream(goldenPath); var golden = WorkbookFactory.create(input)) {
            assertEquals(count, model.sheets().size());
            assertEquals(count, golden.getNumberOfSheets());
            for (int i = 0; i < count; i++) {
                SheetModel actual = model.sheets().get(i);
                Sheet expected = golden.getSheetAt(i);
                assertEquals(expected.getSheetName(), actual.name());
                int width = actual.rows().get(2).values().size();
                assertEquals(goldenRow(expected, 3, width), strings(actual.rows().get(2).values()));
            }
            assertEquals(goldenData(golden.getSheet("03_메서드정의"), 4),
                    data(sheet(model, "03_메서드정의")).stream().map(JavaSpecMapperTest::strings).toList());
            assertEquals(goldenData(golden.getSheetAt(1), 7).subList(0, 3),
                    data(model.sheets().get(1)).subList(0, 3).stream().map(JavaSpecMapperTest::strings).toList());
            int importIndex = count - 3;
            assertEquals(goldenData(golden.getSheetAt(importIndex), 2),
                    data(model.sheets().get(importIndex)).stream().map(JavaSpecMapperTest::strings).toList());
            // Golden uses the sample repository root; this job uses the selected module root.
            assertTrue(goldenData(golden.getSheetAt(count - 2), 1).get(0).get(0).endsWith("/" + name + ".java"));
            assertEquals(List.of(cells(name + ".java")), data(model.sheets().get(count - 2)));
            List<List<String>> expectedDetails = goldenData(golden.getSheetAt(count - 1), 7);
            List<List<Object>> details = data(model.sheets().get(count - 1));
            assertEquals(expectedDetails.stream().map(r -> r.get(0)).toList(),
                    details.stream().map(r -> r.get(0)).toList());
            // addTodo has unchanged, untruncated source steps in both approved samples.
            assertEquals(expectedDetails.get(0), strings(details.get(0)));
        }
        assertArrayEquals(original, Files.readAllBytes(goldenPath), "Golden must remain read-only");
    }

    private WorkbookModel map(JavaFacts facts, LocalDate date) {
        Path source = facts.sourceFile();
        ModuleSelection module = new ModuleSelection(source.getParent(), "SUB", "표시 모듈");
        JobConfig config = new JobConfig(SourceMode.JAVA, "SYS", "작성자", date, "입력 프로그램", "입력 개요",
                temp.resolve("out"), OutputFormat.XLS, List.of(module));
        return mapper.map(facts, config, new SourceJob(module, source, source.getFileName(), temp.resolve("out.xls")));
    }

    private static JavaFacts analyze(String name) throws Exception {
        return new JavaParserAnalyzer().analyze(Path.of("src/test/resources/fixtures/java/" + name + ".java"));
    }

    private static SheetModel sheet(WorkbookModel model, String name) {
        return model.sheets().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<List<Object>> data(SheetModel sheet) {
        return sheet.rows().stream().filter(r -> r.role() == RowKind.DATA).map(SheetRow::values).toList();
    }

    private static List<Object> cells(Object... values) { return Arrays.asList(values); }
    private static String ownerName(String owner, JavaFacts facts) {
        if (owner == null) {
            return null;
        }
        int parameters = owner.indexOf('(');
        if (parameters < 0) {
            return owner;
        }
        String baseName = owner.substring(0, parameters);
        long overloads = facts.methods().stream().filter(method -> method.name().equals(baseName)).count();
        return overloads > 1 ? owner : baseName;
    }

    private static boolean blank(Object value) { return value == null || value.equals(""); }
    private static List<String> strings(List<Object> values) {
        return values.stream().map(v -> v == null ? "" : v.toString()).toList();
    }

    private static List<String> goldenRow(Sheet sheet, int row, int width) {
        DataFormatter formatter = new DataFormatter();
        List<String> result = new ArrayList<>();
        for (int c = 0; c < width; c++) {
            result.add(sheet.getRow(row) == null ? "" : formatter.formatCellValue(sheet.getRow(row).getCell(c)));
        }
        return result;
    }

    private static List<List<String>> goldenData(Sheet sheet, int width) {
        List<List<String>> result = new ArrayList<>();
        for (int r = 4; r <= sheet.getLastRowNum(); r++) {
            List<String> row = goldenRow(sheet, r, width);
            if (row.stream().anyMatch(v -> !v.isEmpty())) result.add(row);
        }
        return result;
    }
}
