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
        JobConfig config = new JobConfig(SourceMode.JAVA, "SYS", "", null, "", "", temp.resolve("out"),
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
        assertEquals("SYS", data(model.sheets().get(0)).get(0).get(2));
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

    @Test
    void oversizedTextContinuesLosslesslyInBothWorkbookFormats() throws Exception {
        String snippet = "x".repeat(32760) + "😀" + "y".repeat(100);
        String javadoc = "Documentation " + "d".repeat(33000);
        String initializer = "\"prefix" + "i".repeat(33000) + "😀suffix\"";
        List<StepFact> manySteps = new ArrayList<>();
        List<String> manyLines = new ArrayList<>();
        for (int i = 1; i <= 400; i++) {
            String source = "step" + i + "_" + "s".repeat(90);
            manySteps.add(new StepFact(i, "CALL", i, "", source, ""));
            manyLines.add("CALL  " + source);
        }
        String manyDetail = String.join("\n", manyLines);
        assertTrue(manyDetail.length() > 32767);
        MethodFact large = new MethodFact("aLarge", "aLarge()", List.of(), "void", "public",
                1, 2, javadoc, List.of(new StepFact(1, "CALL", 1, "", snippet, "")));
        MethodFact many = new MethodFact("bMany", "bMany()", List.of(), "void", "private",
                3, 4, "many", manySteps);
        MethodFact small = new MethodFact("zSmall", "zSmall()", List.of(), "int", "public",
                5, 6, "small", List.of(new StepFact(1, "CALL", 5, "", "ok()", "")));
        JavaFacts facts = new JavaFacts(temp.resolve("Overflow.java"), "Class",
                List.of(new VariableFact("FIELD", "value", "String", "static", "class",
                        initializer, 1, "Overflow")),
                List.of(large, many, small), List.of(), List.of(), List.of(),
                List.of(new StaticInitFact("value", "FIELD", initializer, 1)));
        WorkbookModel model = map(facts, LocalDate.of(2020, 1, 2));
        assertOverflowRows(model.sheets().stream().collect(java.util.stream.Collectors.toMap(
                SheetModel::name, s -> data(s).stream().map(JavaSpecMapperTest::strings).toList())),
                snippet, javadoc, initializer, manyDetail);
        for (SheetModel sheet : model.sheets()) {
            for (SheetRow row : sheet.rows()) {
                for (Object value : row.values()) {
                    if (value instanceof String text) assertTrue(text.length() <= 32767, sheet.name());
                }
            }
        }
        for (OutputFormat format : List.of(OutputFormat.XLS, OutputFormat.XLSX)) {
            Path output = temp.resolve("overflow." + format.name().toLowerCase(java.util.Locale.ROOT));
            new PoiWorkbookWriter().write(model, output, format);
            try (Workbook workbook = WorkbookFactory.create(output.toFile())) {
                java.util.Map<String, List<List<String>>> rows = new java.util.HashMap<>();
                DataFormatter formatter = new DataFormatter();
                for (SheetModel definition : model.sheets()) {
                    Sheet physical = workbook.getSheet(definition.name());
                    List<List<String>> dataRows = new ArrayList<>();
                    int width = definition.rows().get(2).values().size();
                    for (int i = 0; i < data(definition).size(); i++) {
                        List<String> values = new ArrayList<>();
                        for (int column = 0; column < width; column++) {
                            values.add(formatter.formatCellValue(physical.getRow(i + 4).getCell(column)));
                        }
                        dataRows.add(values);
                    }
                    rows.put(definition.name(), dataRows);
                    for (org.apache.poi.ss.usermodel.Row row : physical) {
                        for (org.apache.poi.ss.usermodel.Cell cell : row) {
                            assertTrue(formatter.formatCellValue(cell).length() <= 32767, definition.name());
                        }
                    }
                }
                assertOverflowRows(rows, snippet, javadoc, initializer, manyDetail);
            }
        }
    }

    @Test
    void exactCellLimitAndSurrogateBoundaryRoundTripInBothFormats() throws Exception {
        String atLimit = "a".repeat(32765) + "😀";
        String overLimit = "b".repeat(32766) + "😀";
        assertEquals(32767, atLimit.length());
        assertEquals(32768, overLimit.length());
        JavaFacts facts = new JavaFacts(temp.resolve("Boundary.java"), "Class", List.of(), List.of(),
                List.of(new CallFact(1, "normal", atLimit, "target"),
                        new CallFact(2, "large", overLimit, "target")),
                List.of(), List.of(), List.of());
        WorkbookModel model = map(facts, LocalDate.of(2020, 1, 2));
        List<List<String>> rows = data(sheet(model, "05_호출관계")).stream()
                .map(JavaSpecMapperTest::strings).toList();
        assertBoundaryRows(rows, atLimit, overLimit);

        for (OutputFormat format : List.of(OutputFormat.XLS, OutputFormat.XLSX)) {
            Path output = temp.resolve("boundary." + format.name().toLowerCase(java.util.Locale.ROOT));
            new PoiWorkbookWriter().write(model, output, format);
            try (Workbook workbook = WorkbookFactory.create(output.toFile())) {
                Sheet physical = workbook.getSheet("05_호출관계");
                DataFormatter formatter = new DataFormatter();
                List<List<String>> written = new ArrayList<>();
                for (int row = 4; row < 7; row++) {
                    List<String> values = new ArrayList<>();
                    for (int column = 0; column < 3; column++) {
                        values.add(formatter.formatCellValue(physical.getRow(row).getCell(column)));
                    }
                    written.add(values);
                }
                assertBoundaryRows(written, atLimit, overLimit);
            }
        }
    }

    private static void assertBoundaryRows(List<List<String>> rows, String atLimit, String overLimit) {
        assertEquals(3, rows.size());
        assertEquals(List.of("normal", atLimit, "target"), rows.get(0));
        assertEquals(List.of("large", "b".repeat(32766), "target"), rows.get(1));
        assertEquals(List.of("↳ 계속", "😀", ""), rows.get(2));
        assertEquals(overLimit, joinColumn(rows.subList(1, 3), 1));
    }

    @Test
    void allLongColumnsKeepTheirFragmentsAndUseAnExtraAssociationColumn() {
        String first = "a".repeat(32766) + "😀" + "b".repeat(20);
        String second = "line one\n" + "c".repeat(33000);
        String third = "d".repeat(33000);
        JavaFacts facts = new JavaFacts(temp.resolve("Calls.java"), "Class", List.of(), List.of(),
                List.of(new CallFact(1, first, second, third)), List.of(), List.of(), List.of());
        List<List<String>> rows = data(sheet(map(facts, LocalDate.of(2020, 1, 2)), "05_호출관계"))
                .stream().map(JavaSpecMapperTest::strings).toList();
        assertEquals(first, joinColumn(rows, 0));
        assertEquals(second, joinColumn(rows, 1));
        assertEquals(third, joinColumn(rows, 2));
        assertEquals("line one\n", rows.get(0).get(1));
        assertFalse(Character.isHighSurrogate(rows.get(0).get(0).charAt(rows.get(0).get(0).length() - 1)));
        for (List<String> row : rows) {
            for (int column = 0; column < 3; column++) assertTrue(row.get(column).length() <= 32767);
        }
        for (int i = 1; i < rows.size(); i++) assertFalse(rows.get(i).get(3).isEmpty());
    }

    private static void assertOverflowRows(java.util.Map<String, List<List<String>>> sheets,
            String snippet, String javadoc, String initializer, String manyDetail) {
        List<List<String>> details = sheets.get("상세 사양");
        int many = indexOfFirstColumn(details, "bMany");
        int small = indexOfFirstColumn(details, "zSmall");
        assertEquals(List.of("aLarge", "public", "", javadoc.substring(0,
                details.get(0).get(3).length()), details.get(0).get(4), "", "void"), details.get(0));
        assertTrue(many > 1);
        assertTrue(small - many > 1);
        assertEquals(javadoc, joinColumn(details.subList(0, many), 3));
        assertEquals("CALL  " + snippet, joinColumn(details.subList(0, many), 4));
        assertEquals(manyDetail, joinColumn(details.subList(many, small), 4));
        assertEquals(List.of("zSmall", "public", "", "small", "CALL  ok()", "", "int"), details.get(small));
        assertEquals(small + 1, details.size());
        assertAssociated(details.subList(1, many), 3, 4);
        assertAssociated(details.subList(many + 1, small), 3, 4);

        List<List<String>> steps = sheets.get("04_처리단계");
        int nextMethod = indexOfFirstColumn(steps, "bMany()");
        assertTrue(nextMethod > 1);
        assertEquals(List.of("aLarge()", "1", "CALL", "", steps.get(0).get(4), ""), steps.get(0));
        assertEquals(snippet, joinColumn(steps.subList(0, nextMethod), 4));
        assertAssociated(steps.subList(1, nextMethod), 4);
        assertEquals(List.of("zSmall()", "1", "CALL", "", "ok()", ""), steps.get(steps.size() - 1));

        List<List<String>> variables = sheets.get("02_OBJ정의");
        assertTrue(variables.size() > 1);
        assertEquals(List.of("FIELD", "value", "String", "static", "class",
                variables.get(0).get(5), "Overflow"), variables.get(0));
        assertEquals(initializer, joinColumn(variables, 5));
        assertAssociated(variables.subList(1, variables.size()), 5);
        List<List<String>> initializers = sheets.get("06_정적초기화");
        assertTrue(initializers.size() > 1);
        assertEquals(List.of("value", "FIELD", initializers.get(0).get(2)), initializers.get(0));
        assertEquals(initializer, joinColumn(initializers, 2));
        assertAssociated(initializers.subList(1, initializers.size()), 2);
    }

    private static int indexOfFirstColumn(List<List<String>> rows, String value) {
        for (int i = 0; i < rows.size(); i++) {
            if (value.equals(rows.get(i).get(0))) return i;
        }
        fail("Missing row: " + value);
        return -1;
    }

    private static String joinColumn(List<List<String>> rows, int column) {
        return rows.stream().map(row -> row.get(column)).collect(java.util.stream.Collectors.joining());
    }

    private static void assertAssociated(List<List<String>> rows, int... textColumns) {
        for (List<String> row : rows) {
            boolean associated = false;
            for (int column = 0; column < row.size(); column++) {
                final int index = column;
                if (Arrays.stream(textColumns).noneMatch(c -> c == index) && !row.get(column).isEmpty()) {
                    associated = true;
                }
            }
            assertTrue(associated, "Continuation row must visibly identify its logical row");
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
