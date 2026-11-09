package com.comus93.specgen.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.config.ModuleSelection;
import com.comus93.specgen.config.OutputFormat;
import com.comus93.specgen.config.SourceMode;
import com.comus93.specgen.facts.JavaFacts;
import com.comus93.specgen.facts.JspFacts;
import com.comus93.specgen.parser.JavaParserAnalyzer;
import com.comus93.specgen.parser.PmdJspAnalyzer;
import com.comus93.specgen.runner.FileResult;
import com.comus93.specgen.runner.JobRunner;
import com.comus93.specgen.runner.RunSummary;

/**
 * Task 12 functional gate. Golden workbooks are reference assets only; their
 * cell values, layout, styles and total-difference counts are not PASS/FAIL
 * criteria.
 */
class GoldenSampleTest {

    private static final List<String> GOLDEN_FILES = List.of(
            "docs/golden/JavaParser_TodoController_ProgramSpec_Grayscale.xlsx",
            "docs/golden/JavaParser_TodoService_ProgramSpec_Grayscale.xlsx",
            "docs/golden/PMD_JSP_todo_ProgramSpec_Grayscale.xlsx",
            "docs/golden/PMD_JSP_list-todos_ProgramSpec_Grayscale.xlsx");

    private static final List<String> JAVA_CONTROLLER_SHEETS = List.of(
            "01_표제부", "02_OBJ정의", "03_메서드정의", "04_처리단계", "05_호출관계",
            "06_타입참조", "07_Import", "08_원본경로", "상세 사양");
    private static final List<String> JAVA_SERVICE_SHEETS = List.of(
            "01_표제부", "02_OBJ정의", "03_메서드정의", "04_처리단계", "05_호출관계",
            "06_정적초기화", "07_타입참조", "08_Import", "09_원본경로", "상세 사양");
    private static final List<String> JSP_SHEETS = List.of(
            "01_표제부", "02_화면필드정의", "03_최소흐름", "04_원본경로", "상세 사양");

    private static final Pattern SOURCE_LOCATION = Pattern.compile("\\[L\\d+]");
    private static final List<String> FORBIDDEN_METADATA = List.of("AST", "소스 위치");

    @TempDir
    Path tempDir;

    @Test
    void goldenAssetStatusIsInformationalAndDoesNotBlockFunction() throws Exception {
        Path root = projectRoot();
        Path manifest = root.resolve("docs/test/golden-manifest.sha256");
        Map<String, String> recorded = new LinkedHashMap<>();
        if (Files.isRegularFile(manifest)) {
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                String clean = line.strip();
                if (clean.isEmpty() || clean.startsWith("#")) {
                    continue;
                }
                String[] parts = clean.split("\\s+", 2);
                if (parts.length == 2) {
                    recorded.put(parts[1].strip(), parts[0].toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String relative : GOLDEN_FILES) {
            Path asset = root.resolve(relative);
            if (!Files.isRegularFile(asset)) {
                System.out.println("[ASSET] MISSING " + relative);
                continue;
            }
            String actual = sha256(asset);
            String expected = recorded.get(relative);
            System.out.println("[ASSET] " + (expected == null ? "UNRECORDED" : expected.equals(actual)
                    ? "OK " + relative : "MISMATCH " + relative));
        }
        // Informational only: no assertion is allowed to turn asset state into a functional failure.
    }

    @Test
    void javaFixturesProduceRequiredSheetsAndSourceDerivedFacts() throws Exception {
        Path root = projectRoot();
        Path project = root.resolve("src/test/resources/fixtures/spring-mvc");
        Path output = tempDir.resolve("java-output");
        RunSummary summary = run(SourceMode.JAVA, project, output, "todo");
        assertEquals(2, summary.successCount());
        assertEquals(0, summary.failureCount() + summary.warningCount());

        Path controllerFile = outputFile(summary, "TodoController.java");
        Path serviceFile = outputFile(summary, "TodoService.java");
        JavaFacts controllerFacts = new JavaParserAnalyzer().analyze(
                project.resolve("src/main/java/com/in28minutes/todo/TodoController.java"));
        JavaFacts serviceFacts = new JavaParserAnalyzer().analyze(
                project.resolve("src/main/java/com/in28minutes/todo/TodoService.java"));

        try (Workbook controller = open(controllerFile); Workbook service = open(serviceFile)) {
            assertSheetNames(controller, JAVA_CONTROLLER_SHEETS);
            assertSheetNames(service, JAVA_SERVICE_SHEETS);
            assertEquals(9, controllerFacts.methods().size());
            assertEquals(5, serviceFacts.methods().size());
            assertJavaFactsPresent(controller, controllerFacts);
            assertJavaFactsPresent(service, serviceFacts);
            assertTrue(workbookContains(controller, "src/main/java/com/in28minutes/todo/TodoController.java"));
            assertTrue(workbookContains(service, "src/main/java/com/in28minutes/todo/TodoService.java"));
            assertTrue(workbookContains(controller, "2019-10-28"));
            assertNoInternalMetadata(controller);
            assertNoInternalMetadata(service);
        }
    }

    @Test
    void jspFixturesProduceRequiredSheetsAndSourceDerivedFacts() throws Exception {
        Path root = projectRoot();
        Path project = root.resolve("src/test/resources/fixtures/spring-mvc");
        Path output = tempDir.resolve("jsp-output");
        RunSummary summary = run(SourceMode.JSP, project, output, "views");
        assertEquals(2, summary.successCount());
        assertEquals(0, summary.failureCount() + summary.warningCount());

        Path todoFile = outputFile(summary, "todo.jsp");
        Path listFile = outputFile(summary, "list-todos.jsp");
        JspFacts todoFacts = new PmdJspAnalyzer().analyze(
                project.resolve("src/main/webapp/WEB-INF/views/todo.jsp"));
        JspFacts listFacts = new PmdJspAnalyzer().analyze(
                project.resolve("src/main/webapp/WEB-INF/views/list-todos.jsp"));

        try (Workbook todo = open(todoFile); Workbook list = open(listFile)) {
            assertSheetNames(todo, JSP_SHEETS);
            assertSheetNames(list, JSP_SHEETS);

            assertEquals(3, listFacts.events().size());
            assertEquals(List.of("Update", "Delete", "Add"),
                    listFacts.events().stream().map(JspFacts.EventFact::name).toList());
            for (JspFacts.EventFact event : listFacts.events()) {
                assertTrue(workbookContains(list, event.name()));
                assertTrue(workbookContains(list, event.description()));
            }
            for (JspFacts.ScreenFieldFact field : todoFacts.screenFields()) {
                if (field.fieldName() != null && !field.fieldName().isBlank()) {
                    assertTrue(workbookContains(todo, field.fieldName()));
                }
            }
            for (JspFacts.ScreenFieldFact field : listFacts.screenFields()) {
                if (field.fieldName() != null && !field.fieldName().isBlank()) {
                    assertTrue(workbookContains(list, field.fieldName()));
                }
            }
            assertTrue(workbookContains(todo, "src/main/webapp/WEB-INF/views/todo.jsp"));
            assertTrue(workbookContains(list, "src/main/webapp/WEB-INF/views/list-todos.jsp"));
            assertTrue(workbookContains(todo, "2019-10-28"));
            assertNoInternalMetadata(todo);
            assertNoInternalMetadata(list);
        }
    }

    @Test
    void failureIsolationPreservesSourcesAndUnrelatedFilesAndOverwritesOwnOutput() throws Exception {
        Path module = tempDir.resolve("sources").resolve("module");
        Path bad = write(module.resolve("Bad.java"), "class Bad { void broken( { }");
        Path good = write(module.resolve("Good.java"), "class Good { int add(int a, int b) { return a + b; } }");
        String badHash = sha256(bad);
        String goodHash = sha256(good);
        Path output = tempDir.resolve("repeat-output");
        Path keep = output.resolve("keep.txt");
        Path other = output.resolve("SYS1").resolve("java").resolve("module").resolve("Other.xls");
        write(keep, "keep");
        write(other, "sentinel");
        String otherHash = sha256(other);

        RunSummary first = run(SourceMode.JAVA, module, output, "module");
        assertEquals(2, first.totalCount());
        assertEquals(1, first.successCount());
        assertEquals(1, first.failureCount());
        FileResult failed = first.results().stream()
                .filter(result -> result.sourceFile().getFileName().toString().equals("Bad.java"))
                .findFirst().orElseThrow();
        assertEquals(FileResult.FileStatus.FAILED, failed.status());
        assertTrue(Files.isRegularFile(output.resolve("SYS1").resolve("java").resolve("module").resolve("Good.xlsx")));
        assertFalse(Files.exists(output.resolve("SYS1").resolve("java").resolve("module").resolve("Bad.xlsx")));

        RunSummary second = run(SourceMode.JAVA, module, output, "module");
        assertEquals(1, second.successCount());
        assertEquals(1, second.failureCount());
        assertEquals(badHash, sha256(bad));
        assertEquals(goodHash, sha256(good));
        assertEquals("keep", Files.readString(keep));
        assertEquals(otherHash, sha256(other));
        try (var files = Files.walk(output)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".specgen-")));
        }
    }

    private static void assertJavaFactsPresent(Workbook workbook, JavaFacts facts) {
        assertFalse(facts.methods().isEmpty());
        for (JavaFacts.MethodFact method : facts.methods()) {
            assertTrue(workbookContains(workbook, method.name()), "Missing method: " + method.name());
        }
        for (JavaFacts.ImportFact importFact : facts.imports()) {
            assertTrue(workbookContains(workbook, importFact.shortName()),
                    "Missing import: " + importFact.shortName());
        }
        assertTrue(facts.methods().stream().flatMap(method -> method.steps().stream())
                .map(JavaFacts.StepFact::sourceSnippet)
                .filter(snippet -> snippet != null && !snippet.isBlank())
                .anyMatch(snippet -> workbookContains(workbook, snippet)),
                "No source-derived processing step was written");
    }

    private static void assertSheetNames(Workbook workbook, List<String> expected) {
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            actual.add(workbook.getSheetName(i));
        }
        assertEquals(expected, actual);
    }

    private static void assertNoInternalMetadata(Workbook workbook) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    String value = formatter.formatCellValue(cell);
                    assertFalse(SOURCE_LOCATION.matcher(value).find(),
                            "Source-location marker leaked into " + sheet.getSheetName());
                    for (String forbidden : FORBIDDEN_METADATA) {
                        assertFalse(value.contains(forbidden),
                                "Internal metadata leaked into " + sheet.getSheetName());
                    }
                }
            }
        }
    }

    private static boolean workbookContains(Workbook workbook, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (formatter.formatCellValue(cell).contains(expected)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Path outputFile(RunSummary summary, String sourceName) {
        return summary.results().stream()
                .filter(result -> result.status() == FileResult.FileStatus.SUCCESS)
                .filter(result -> result.sourceFile().getFileName().toString().equals(sourceName))
                .map(FileResult::outputFile)
                .findFirst().orElseThrow();
    }

    private RunSummary run(SourceMode mode, Path sourceFolder, Path output, String moduleName) {
        ModuleSelection module = new ModuleSelection(sourceFolder, "SYS", moduleName);
        JobConfig config = new JobConfig(mode, "SYS1", "release-validation", LocalDate.of(2019, 10, 28),
                "Program Spec Generator", "Functional release verification", output,
                OutputFormat.XLSX, List.of(module));
        return new JobRunner().run(config, event -> {
        });
    }

    private static Workbook open(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return WorkbookFactory.create(input);
        }
    }

    private static Path write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private static Path projectRoot() {
        String basedir = System.getProperty("basedir");
        if (basedir == null || basedir.isBlank()) {
            basedir = System.getProperty("user.dir");
        }
        return Path.of(basedir).toAbsolutePath().normalize();
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
