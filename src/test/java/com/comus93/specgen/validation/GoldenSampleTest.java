package com.comus93.specgen.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.config.ModuleSelection;
import com.comus93.specgen.config.OutputFormat;
import com.comus93.specgen.config.SourceMode;
import com.comus93.specgen.runner.FileResult;
import com.comus93.specgen.runner.JobRunner;
import com.comus93.specgen.runner.RunSummary;

/**
 * Release gate for the approved Golden samples and the workbooks produced from
 * the pinned Spring MVC fixtures. It intentionally avoids mass-comparing font
 * counts; it checks the stable release contract instead.
 */
class GoldenSampleTest {

    private static final Pattern SOURCE_LOCATION = Pattern.compile("\\[L\\d+]");
    private static final String FULL_PARITY_PROPERTY = "specgen.fullGoldenParity";

    private static final List<GoldenCase> CASES = List.of(
            new GoldenCase("JavaParser_TodoController_ProgramSpec_Grayscale.xlsx",
                    SourceMode.JAVA, "todo", "TodoController.java", "TodoController"),
            new GoldenCase("JavaParser_TodoService_ProgramSpec_Grayscale.xlsx",
                    SourceMode.JAVA, "todo", "TodoService.java", "TodoService"),
            new GoldenCase("PMD_JSP_todo_ProgramSpec_Grayscale.xlsx",
                    SourceMode.JSP, "views", "todo.jsp", "todo"),
            new GoldenCase("PMD_JSP_list-todos_ProgramSpec_Grayscale.xlsx",
                    SourceMode.JSP, "views", "list-todos.jsp", "list-todos"));

    private static final List<String> JSP_LINKS = List.of("Update", "Delete", "Add");

    @TempDir
    Path tempDir;

    @Test
    void goldenFilesMatchManifestAndReleaseContract() throws Exception {
        Path root = projectRoot();
        Map<String, String> manifest = readManifest(root.resolve("docs/test/golden-manifest.sha256"));
        for (GoldenCase testCase : CASES) {
            Path golden = root.resolve("docs/golden").resolve(testCase.goldenFile());
            assertTrue(Files.isRegularFile(golden), "Golden file is missing: " + golden);
            assertEquals(manifest.get(relative(root, golden)), sha256(golden),
                    "Golden SHA-256 does not match the approved manifest: " + golden);
            try (Workbook workbook = open(golden)) {
                validateGoldenContract(workbook, testCase);
            }
        }
    }

    @Test
    void regeneratedOutputsPreserveReleaseCriticalGoldenSemantics() throws Exception {
        Map<String, Path> generated = generateOutputs();
        for (GoldenCase testCase : CASES) {
            Path goldenPath = projectRoot().resolve("docs/golden").resolve(testCase.goldenFile());
            Path output = generated.get(testCase.sourceName());
            assertNotNull(output, "No generated workbook for " + testCase.sourceName());
            assertTrue(Files.isRegularFile(output), "Generated workbook is missing: " + output);
            try (Workbook golden = open(goldenPath); Workbook actual = open(output)) {
                assertSameSheetContract(golden, actual, testCase);
            }
        }
    }

    @Test
    void repeatedGenerationOverwritesOwnOutputsWithoutTouchingUnrelatedFiles() throws Exception {
        Path root = projectRoot();
        Path project = copyFixtureProject(root);
        Path output = tempDir.resolve("repeat-output");
        Path keep = output.resolve("keep.txt");
        Files.createDirectories(keep.getParent());
        Files.writeString(keep, "keep", StandardCharsets.UTF_8);

        RunSummary first = run(SourceMode.JAVA, project, output, "todo");
        RunSummary second = run(SourceMode.JAVA, project, output, "todo");

        assertEquals(2, first.successCount());
        assertEquals(2, second.successCount());
        assertEquals("keep", Files.readString(keep));
        try (Stream<Path> files = Files.walk(output)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".specgen-")),
                    "Temporary workbook files must not remain after repeated generation");
        }
    }

    @Test
    @EnabledIfSystemProperty(named = FULL_PARITY_PROPERTY, matches = "true")
    void regeneratedOutputsMatchEveryGoldenCellForReleaseAudit() throws Exception {
        Map<String, Path> generated = generateOutputs();
        List<String> differences = new ArrayList<>();
        for (GoldenCase testCase : CASES) {
            Path goldenPath = projectRoot().resolve("docs/golden").resolve(testCase.goldenFile());
            Path output = generated.get(testCase.sourceName());
            try (Workbook golden = open(goldenPath); Workbook actual = open(output)) {
                compareEveryCell(golden, actual, testCase.goldenFile(), differences);
            }
        }
        if (!differences.isEmpty()) {
            fail("Full Golden parity differs in " + differences.size() + " place(s):\n"
                    + String.join("\n", differences.subList(0, Math.min(20, differences.size()))));
        }
    }

    private void validateGoldenContract(Workbook workbook, GoldenCase testCase) {
        int expectedSheets = testCase.mode() == SourceMode.JAVA
                ? ("TodoService".equals(testCase.programId()) ? 10 : 9) : 5;
        assertEquals(expectedSheets, workbook.getNumberOfSheets(),
                "Unexpected Golden sheet count: " + testCase.goldenFile());

        Sheet cover = workbook.getSheetAt(0);
        assertTitleAndBlankRow(cover);
        assertFalse(value(cover, 0, 0).isEmpty(), "Cover title must not be blank");
        assertMergedWidth(cover, 0, cover.getRow(0).getLastCellNum());
        assertMergedWidth(cover, 1, cover.getRow(0).getLastCellNum());
        assertTrue(cover.getRow(3) != null, "Cover header row must be the fourth physical row");

        List<List<String>> coverRows = nonEmptyRows(cover, coverWidth(cover));
        assertEquals(12, coverRows.size(), "Cover must contain a title, header and ten program rows");
        for (int i = 0; i < 10; i++) {
            assertEquals(String.format("%02d", i + 1), coverRows.get(i + 2).get(0),
                    "Cover row numbers must run from 01 through 10");
        }
        assertCoverBand(cover, 3);
        assertMonochrome(workbook, testCase.goldenFile());

        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            assertTitleAndBlankRow(sheet);
            int width = sheetWidth(sheet, index == 0 ? 6 : 1);
            int headerIndex = index == workbook.getNumberOfSheets() - 1 ? 4 : 3;
            List<String> headers = rowValues(sheet, headerIndex, width);
            assertTrue(headers.stream().anyMatch(value -> !value.isEmpty()),
                    "Golden header row is missing on sheet " + sheet.getSheetName());
            for (String header : headers) {
                assertFalse(header.toUpperCase(Locale.ROOT).contains("AST"),
                        "AST metadata must not appear in Golden headers on " + sheet.getSheetName());
            }
        }

        if (testCase.mode() == SourceMode.JAVA) {
            Sheet detail = workbook.getSheetAt(workbook.getNumberOfSheets() - 1);
            String detailHeader = rowValues(detail, 3, 7).get(4);
            assertEquals("DETAIL (PSEUDO CODE)", detailHeader,
                    "Java detail sheet must remain separate from processing steps");
            for (int row = 4; row <= detail.getLastRowNum(); row++) {
                String value = rowValues(detail, row, 7).get(4);
                assertFalse(SOURCE_LOCATION.matcher(value).find(),
                        "Golden detail must not contain [Lxxx] source markers");
            }
        } else {
            Sheet detail = workbook.getSheetAt(workbook.getNumberOfSheets() - 1);
            if ("list-todos".equals(testCase.programId())) {
                assertJspLinks(detail);
            }
        }
    }

    private void assertSameSheetContract(Workbook golden, Workbook actual, GoldenCase testCase) {
        assertEquals(golden.getNumberOfSheets(), actual.getNumberOfSheets(),
                "Regenerated sheet count differs from Golden: " + testCase.goldenFile());

        for (int index = 0; index < golden.getNumberOfSheets(); index++) {
            Sheet expected = golden.getSheetAt(index);
            Sheet source = actual.getSheetAt(index);
            assertEquals(expected.getSheetName(), source.getSheetName(),
                    "Regenerated sheet order differs from Golden");
            int width = maxWidth(expected);
            if (rowValues(expected, 0, width).stream().anyMatch(value -> !value.isEmpty())) {
                assertPhysicalRow(expected, source, 0, width, "title");
            } else {
                assertNotNull(source.getRow(0), "Physical title row must exist on " + expected.getSheetName());
            }
            assertPhysicalRow(expected, source, 1, width, "blank spacer");
            assertPhysicalRow(expected, source, 2, width, "visual spacer");
            boolean lastSheet = index == golden.getNumberOfSheets() - 1;
            int headerIndex = lastSheet && testCase.mode() == SourceMode.JSP ? 4 : 3;
            assertPhysicalRow(expected, source, headerIndex, width, "header");
            if (width > 1) {
                assertMergedWidth(source, 0, width);
            }
        }

        Sheet expectedCover = golden.getSheetAt(0);
        Sheet actualCover = actual.getSheetAt(0);
        for (int row = 0; row <= 13; row++) {
            assertPhysicalRow(expectedCover, actualCover, row, 6, "cover");
        }
        assertCoverBand(actualCover, 3);
        assertTableOutline(actual);
        assertMonochrome(actual, testCase.goldenFile());

        int pathIndex = testCase.mode() == SourceMode.JAVA
                ? golden.getNumberOfSheets() - 2 : 3;
        Sheet expectedPath = golden.getSheetAt(pathIndex);
        Sheet actualPath = actual.getSheetAt(pathIndex);
        for (int row = 0; row <= 4; row++) {
            assertPhysicalRow(expectedPath, actualPath, row, 1, "source path");
        }

        if (testCase.mode() == SourceMode.JSP) {
            Sheet expectedFields = golden.getSheetAt(1);
            Sheet actualFields = actual.getSheetAt(1);
            int fieldWidth = maxWidth(expectedFields);
            for (int row = 4; row <= lastNonEmptyRow(expectedFields, fieldWidth); row++) {
                assertPhysicalRow(expectedFields, actualFields, row, fieldWidth, "screen field");
            }

            Sheet expectedFlows = golden.getSheetAt(2);
            Sheet actualFlows = actual.getSheetAt(2);
            int flowWidth = maxWidth(expectedFlows);
            for (int row = 4; row <= lastNonEmptyRow(expectedFlows, flowWidth); row++) {
                assertFlowRow(expectedFlows, actualFlows, row, flowWidth);
            }

            Sheet expectedDetail = golden.getSheetAt(golden.getNumberOfSheets() - 1);
            Sheet actualDetail = actual.getSheetAt(actual.getNumberOfSheets() - 1);
            int detailWidth = maxWidth(expectedDetail);
            for (int row = 0; row <= lastNonEmptyRow(expectedDetail, detailWidth); row++) {
                assertPhysicalRow(expectedDetail, actualDetail, row, detailWidth, "detail");
            }
            if ("list-todos".equals(testCase.programId())) {
                assertJspLinks(actualDetail);
            }
        } else {
            for (int index = 1; index < golden.getNumberOfSheets() - 1; index++) {
                if (index == pathIndex) {
                    continue;
                }
                Sheet expected = golden.getSheetAt(index);
                Sheet source = actual.getSheetAt(index);
                int width = maxWidth(expected);
                if (index == 4 || index == 5) {
                    assertSameMultiset(expected, source, 4, width);
                } else {
                    for (int row = 4; row <= lastNonEmptyRow(expected, width); row++) {
                        assertPhysicalRow(expected, source, row, width, "Java core data");
                    }
                }
            }

            Sheet expectedDetail = golden.getSheetAt(golden.getNumberOfSheets() - 1);
            Sheet actualDetail = actual.getSheetAt(actual.getNumberOfSheets() - 1);
            int detailWidth = maxWidth(expectedDetail);
            for (int row = 0; row <= lastNonEmptyRow(expectedDetail, detailWidth); row++) {
                assertPhysicalRow(expectedDetail, actualDetail, row, detailWidth, "Java detail");
            }
            for (int row = 4; row <= actualDetail.getLastRowNum(); row++) {
                String value = rowValues(actualDetail, row, detailWidth).get(4);
                assertFalse(SOURCE_LOCATION.matcher(value).find(),
                        "Regenerated detail must not contain [Lxxx] source markers");
            }
        }
    }

    private static void assertPhysicalRow(Sheet expected, Sheet actual, int row, int width, String label) {
        assertEquals(rowValues(expected, row, width), rowValues(actual, row, width),
                "Physical " + label + " row " + (row + 1) + " differs from Golden on "
                        + expected.getSheetName());
    }

    private static void assertFlowRow(Sheet expected, Sheet actual, int row, int width) {
        List<String> expectedValues = rowValues(expected, row, width);
        List<String> actualValues = rowValues(actual, row, width);
        assertEquals(expectedValues.get(0), actualValues.get(0),
                "Flow sequence differs at physical row " + (row + 1));
        assertEquals(expectedValues.get(1), actualValues.get(1),
                "Flow tag/file differs at physical row " + (row + 1));
        assertEquals(expectedValues.get(2), actualValues.get(2),
                "Flow raw attributes differ at physical row " + (row + 1));
    }

    private static void assertSameMultiset(Sheet expected, Sheet actual, int firstRow, int width) {
        List<String> expectedRows = dataRows(expected, firstRow, width);
        List<String> actualRows = dataRows(actual, firstRow, width);
        assertEquals(counts(expectedRows), counts(actualRows),
                "Java core data rows differ from Golden on " + expected.getSheetName());
    }

    private static List<String> dataRows(Sheet sheet, int firstRow, int width) {
        List<String> rows = new ArrayList<>();
        for (int row = firstRow; row <= lastNonEmptyRow(sheet, width); row++) {
            List<String> values = rowValues(sheet, row, width);
            if (values.stream().anyMatch(value -> !value.isEmpty())) {
                rows.add(String.join("\u0001", values));
            }
        }
        return rows;
    }

    private static Map<String, Integer> counts(List<String> rows) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String row : rows) {
            counts.merge(row, 1, Integer::sum);
        }
        return counts;
    }

    private static int lastNonEmptyRow(Sheet sheet, int width) {
        for (int row = sheet.getLastRowNum(); row >= 0; row--) {
            if (rowValues(sheet, row, width).stream().anyMatch(value -> !value.isEmpty())) {
                return row;
            }
        }
        return -1;
    }

    private static void assertTableOutline(Workbook workbook) {
        for (int index = 0; index < workbook.getNumberOfSheets() - 1; index++) {
            Sheet sheet = workbook.getSheetAt(index);
            for (Cell cell : sheet.getRow(3)) {
                if (!new DataFormatter(Locale.ROOT).formatCellValue(cell).isBlank()) {
                    var style = cell.getCellStyle();
                    boolean bordered = style.getBorderTop() != BorderStyle.NONE
                            || style.getBorderBottom() != BorderStyle.NONE
                            || style.getBorderLeft() != BorderStyle.NONE
                            || style.getBorderRight() != BorderStyle.NONE;
                    assertTrue(bordered, "Header cell must keep a visible table outline on "
                            + sheet.getSheetName());
                }
            }
        }
    }

    private static void assertJspLinks(Sheet detail) {
        List<String> linkNames = new ArrayList<>();
        for (int row = 0; row <= detail.getLastRowNum(); row++) {
            List<String> values = rowValues(detail, row, 5);
            if ("LINK_CLICK".equals(values.get(0))) {
                linkNames.add(values.get(1));
                assertTrue(values.get(2).startsWith("href=\""),
                        "LINK_CLICK rows must preserve the href value");
            }
        }
        assertEquals(JSP_LINKS, linkNames, "Expected exactly the three approved JSP links");
    }

    private static void assertTitleAndBlankRow(Sheet sheet) {
        assertNotNull(sheet.getRow(0), "Every sheet needs a title row");
        List<String> secondRow = rowValues(sheet, 1, sheetWidth(sheet, 1));
        assertTrue(secondRow.stream().allMatch(String::isEmpty), "Physical row 2 must be blank");
    }

    private static void assertMergedWidth(Sheet sheet, int row, int width) {
        assertTrue(sheet.getMergedRegions().stream().anyMatch(region ->
                        region.getFirstRow() == row && region.getLastRow() == row
                                && region.getFirstColumn() == 0 && region.getLastColumn() >= width - 1),
                "Title row " + (row + 1) + " must be merged across the sheet width");
    }

    private static void assertCoverBand(Sheet cover, int headerRow) {
        assertTrue(headerRow >= 0, "Cover header row was not found");
        for (int column = 3; column <= 5; column++) {
            Cell cell = cover.getRow(headerRow).getCell(column);
            assertNotNull(cell, "Cover D:F band cell is missing");
            assertTrue(cell.getCellStyle().getFillPattern() != FillPatternType.NO_FILL,
                    "Cover D:F band must be filled");
        }
    }

    private static void assertMonochrome(Workbook workbook, String label) {
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    XSSFCellStyle style = (XSSFCellStyle) cell.getCellStyle();
                    assertGrayscale(style.getFillForegroundXSSFColor(), label + " fill");
                    XSSFFont font = style.getFont();
                    assertGrayscale(font == null ? null : font.getXSSFColor(), label + " font");
                }
            }
        }
    }

    private static void assertGrayscale(XSSFColor color, String label) {
        if (color == null || color.getRGB() == null || color.isAuto() || color.isThemed()) {
            return;
        }
        byte[] rgb = color.getRGB();
        if (rgb.length < 3) {
            return;
        }
        assertEquals(rgb[rgb.length - 3], rgb[rgb.length - 2], label + " must use a grayscale red/green value");
        assertEquals(rgb[rgb.length - 2], rgb[rgb.length - 1], label + " must use a grayscale green/blue value");
    }

    private Map<String, Path> generateOutputs() throws IOException {
        Path project = copyFixtureProject(projectRoot());
        Path javaOutput = tempDir.resolve("release-java");
        Path jspOutput = tempDir.resolve("release-jsp");
        RunSummary javaSummary = run(SourceMode.JAVA, project, javaOutput, "todo");
        RunSummary jspSummary = run(SourceMode.JSP, project, jspOutput, "views");

        assertEquals(2, javaSummary.successCount(), "Both Java Golden fixtures must regenerate");
        assertEquals(2, jspSummary.successCount(), "Both JSP Golden fixtures must regenerate");
        assertEquals(0, javaSummary.failureCount() + jspSummary.failureCount());
        assertEquals(0, javaSummary.warningCount() + jspSummary.warningCount());

        Map<String, Path> outputs = new LinkedHashMap<>();
        for (FileResult result : javaSummary.results()) {
            outputs.put(result.sourceFile().getFileName().toString(), result.outputFile());
        }
        for (FileResult result : jspSummary.results()) {
            outputs.put(result.sourceFile().getFileName().toString(), result.outputFile());
        }
        return outputs;
    }

    private RunSummary run(SourceMode mode, Path project, Path output, String moduleName) {
        ModuleSelection module = new ModuleSelection(project, "", moduleName);
        JobConfig config = new JobConfig(mode, "", "", LocalDate.of(2019, 10, 28), "", "",
                output, OutputFormat.XLSX, List.of(module));
        return new JobRunner().run(config, event -> {
        });
    }

    private static Path copyFixtureProject(Path root) throws IOException {
        Path source = root.resolve("src/test/resources/fixtures/spring-mvc");
        Path project = Files.createTempDirectory("specgen-golden-");
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = project.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
        return project;
    }

    private static void compareEveryCell(Workbook golden, Workbook actual, String label, List<String> differences) {
        if (golden.getNumberOfSheets() != actual.getNumberOfSheets()) {
            differences.add(label + ": sheet count " + golden.getNumberOfSheets()
                    + " != " + actual.getNumberOfSheets());
            return;
        }
        for (int index = 0; index < golden.getNumberOfSheets(); index++) {
            Sheet expected = golden.getSheetAt(index);
            Sheet source = actual.getSheetAt(index);
            if (!expected.getSheetName().equals(source.getSheetName())) {
                differences.add(label + ": sheet " + index + " name differs");
                continue;
            }
            int width = Math.max(sheetWidth(expected, 1), sheetWidth(source, 1));
            int rows = Math.max(expected.getLastRowNum(), source.getLastRowNum());
            for (int row = 0; row <= rows; row++) {
                List<String> expectedValues = rowValues(expected, row, width);
                List<String> actualValues = rowValues(source, row, width);
                if (!expectedValues.equals(actualValues)) {
                    differences.add(label + " / " + expected.getSheetName() + " row " + (row + 1)
                            + " expected " + expectedValues + " but was " + actualValues);
                }
            }
        }
    }

    private static Workbook open(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return WorkbookFactory.create(input);
        }
    }

    private static int findHeaderRow(Sheet sheet, List<String> expected) {
        for (int row = 0; row <= Math.min(sheet.getLastRowNum(), 8); row++) {
            if (rowValues(sheet, row, expected.size()).equals(expected)) {
                return row;
            }
        }
        return -1;
    }

    private static int coverWidth(Sheet cover) {
        return sheetWidth(cover, 6);
    }

    private static int sheetWidth(Sheet sheet, int fallback) {
        return Math.max(fallback, maxWidth(sheet));
    }

    private static int maxWidth(Sheet sheet) {
        int max = 1;
        for (int row = 0; row <= sheet.getLastRowNum(); row++) {
            Row current = sheet.getRow(row);
            if (current != null) {
                max = Math.max(max, current.getLastCellNum());
            }
        }
        return max;
    }

    private static List<List<String>> nonEmptyRows(Sheet sheet, int width) {
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row <= sheet.getLastRowNum(); row++) {
            List<String> values = rowValues(sheet, row, width);
            if (values.stream().anyMatch(value -> !value.isEmpty())) {
                rows.add(values);
            }
        }
        return rows;
    }

    private static List<String> rowValues(Sheet sheet, int rowIndex, int width) {
        List<String> values = new ArrayList<>(width);
        Row row = sheet.getRow(rowIndex);
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (int column = 0; column < width; column++) {
            Cell cell = row == null ? null : row.getCell(column);
            values.add(cell == null || cell.getCellType() == CellType.BLANK
                    ? "" : formatter.formatCellValue(cell));
        }
        return values;
    }

    private static String value(Sheet sheet, int row, int column) {
        Row current = sheet.getRow(row);
        if (current == null) {
            return "";
        }
        Cell cell = current.getCell(column);
        return cell == null ? "" : new DataFormatter(Locale.ROOT).formatCellValue(cell);
    }

    private static Path projectRoot() {
        String basedir = System.getProperty("basedir");
        if (basedir == null || basedir.isBlank()) {
            basedir = System.getProperty("user.dir");
        }
        return Path.of(basedir).toAbsolutePath().normalize();
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static Map<String, String> readManifest(Path manifestPath) throws IOException {
        if (!Files.isRegularFile(manifestPath)) {
            fail("Golden manifest is missing: " + manifestPath);
        }
        Map<String, String> recorded = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(manifestPath, StandardCharsets.UTF_8)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            if (parts.length != 2) {
                fail("Malformed Golden manifest line: " + rawLine);
            }
            recorded.put(parts[1].strip(), parts[0].toLowerCase(Locale.ROOT));
        }
        return recorded;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record GoldenCase(String goldenFile, SourceMode mode, String moduleName,
            String sourceName, String programId) {
    }
}
