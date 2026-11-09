package com.comus93.specgen.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.config.ModuleSelection;
import com.comus93.specgen.config.OutputFormat;
import com.comus93.specgen.config.SourceMode;
import com.comus93.specgen.excel.PoiWorkbookWriter;
import com.comus93.specgen.facts.JavaFacts;
import com.comus93.specgen.mapper.JavaSpecMapper;
import com.comus93.specgen.mapper.JspSpecMapper;
import com.comus93.specgen.parser.JavaParserAnalyzer;
import com.comus93.specgen.parser.PmdJspAnalyzer;
import com.comus93.specgen.parser.SourceAnalysisException;
import com.comus93.specgen.parser.SourceAnalyzer;
import com.comus93.specgen.scan.JobScanner;

class JobRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void processesModulesInRegistrationOrderAndReportsProgress() throws IOException {
        Path first = tempDir.resolve("sources").resolve("first");
        Path second = tempDir.resolve("sources").resolve("second");
        write(first.resolve("A.java"), "class A {}");
        write(second.resolve("B.java"), "class B {}");
        Path output = tempDir.resolve("output");
        List<ProgressEvent> events = new ArrayList<>();

        RunSummary summary = new JobRunner().run(config(SourceMode.JAVA, output, first, second), events::add);

        assertEquals(2, summary.totalCount());
        assertEquals(2, summary.successCount());
        assertEquals(List.of("A.java", "B.java"), summary.results().stream()
                .map(result -> result.sourceFile().getFileName().toString()).toList());
        assertEquals(List.of(1, 2), events.stream().map(ProgressEvent::completed).toList());
        assertEquals(List.of(2, 2), events.stream().map(ProgressEvent::total).toList());
        assertEquals(List.of(FileResult.FileStatus.SUCCESS, FileResult.FileStatus.SUCCESS),
                events.stream().map(ProgressEvent::status).toList());
        assertTrue(Files.isRegularFile(output.resolve("SYS/java/first/A.xls")));
        assertTrue(Files.isRegularFile(output.resolve("SYS/java/second/B.xls")));
    }

    @Test
    void parseFailureShowsOriginalSourceExcerptAndCaretInResult() throws IOException {
        Path module = tempDir.resolve("sources").resolve("jsp");
        Path source = write(module.resolve("Unsupported.jsp"), String.join("\n",
                "",
                "<div <c:if test=\"\u0024{edit}\"><span>unsupported</span></c:if>></div>"));

        RunSummary summary = new JobRunner().run(
                config(SourceMode.JSP, tempDir.resolve("output"), module), event -> { });

        FileResult failed = summary.results().get(0);
        assertEquals(FileResult.FileStatus.FAILED, failed.status());
        assertEquals(SourceAnalysisException.PARSE_ERROR, failed.errorCode());
        assertTrue(failed.message().contains("Source excerpt (original JSP)"));
        assertTrue(failed.message().contains(">    2 | <div <c:if"), failed.message());
        assertTrue(failed.message().contains("unsupported"), failed.message());
        assertTrue(failed.message().contains("^"), failed.message());
        assertTrue(Files.exists(source));
    }

    @Test
    void jspParseFailureDoesNotStopLaterFilesOrEraseSuccessfulWorkbooks() throws IOException {
        Path module = tempDir.resolve("sources").resolve("jsp");
        write(module.resolve("01-valid.jsp"), "<html><body><input name=\"first\"/></body></html>");
        write(module.resolve("02-invalid.jsp"),
                "<div <c:if test=\"\u0024{edit}\"><span>unsupported</span></c:if>></div>");
        write(module.resolve("03-valid.jsp"),
                "<html><body><a href=\"/next\">Next</a></body></html>");
        List<ProgressEvent> events = new ArrayList<>();
        Path output = tempDir.resolve("output");

        RunSummary summary = new JobRunner().run(
                config(SourceMode.JSP, output, module), events::add);

        assertEquals(3, summary.totalCount());
        assertEquals(2, summary.successCount());
        assertEquals(1, summary.failureCount());
        assertEquals(List.of(FileResult.FileStatus.SUCCESS,
                FileResult.FileStatus.FAILED,
                FileResult.FileStatus.SUCCESS),
                summary.results().stream().map(FileResult::status).toList());
        assertEquals(List.of(1, 2, 3),
                events.stream().map(ProgressEvent::completed).toList());
        assertTrue(Files.isRegularFile(output.resolve("SYS/jsp/jsp/01-valid.xls")));
        assertFalse(Files.exists(output.resolve("SYS/jsp/jsp/02-invalid.xls")));
        assertTrue(Files.isRegularFile(output.resolve("SYS/jsp/jsp/03-valid.xls")));
    }

    @Test
    void recoversPartialJspAndLogsDiagnosticsWithoutInterruptingLaterFiles() throws IOException {
        Path module = tempDir.resolve("sources").resolve("jsp");
        write(module.resolve("01-valid.jsp"), "<html><body><input name=\"first\"/></body></html>");
        write(module.resolve("02-partial.jsp"), String.join("\n",
                "<html><body>",
                "<input name=\"before\"/>",
                "<div <c:if test=\"\u0024{edit}\"><span>unsupported</span></c:if> "
                        + "style=\"display:flex\">유효 기간</div>",
                "<input name=\"after\"/>",
                "</body></html>"));
        Path invalid = write(module.resolve("03-invalid.jsp"), String.join("\n",
                "",
                "<div <c:if test=\"\u0024{edit}\"><span>unsupported</span></c:if>></div>"));
        write(module.resolve("04-valid.jsp"), "<html><body><a href=\"/next\">Next</a></body></html>");
        Path output = tempDir.resolve("output");
        List<ProgressEvent> events = new ArrayList<>();
        SourceAnalysisException diagnostic = assertThrows(SourceAnalysisException.class,
                () -> new PmdJspAnalyzer().analyze(invalid));

        RunSummary summary = new JobRunner().run(config(SourceMode.JSP, output, module), events::add);

        assertEquals(4, summary.totalCount());
        assertEquals(2, summary.successCount());
        assertEquals(1, summary.warningCount());
        assertEquals(1, summary.failureCount());
        List<FileResult.FileStatus> statuses = List.of(FileResult.FileStatus.SUCCESS,
                FileResult.FileStatus.WARNING, FileResult.FileStatus.FAILED, FileResult.FileStatus.SUCCESS);
        assertEquals(statuses, summary.results().stream().map(FileResult::status).toList());
        assertEquals(statuses, events.stream().map(ProgressEvent::status).toList());
        assertTrue(Files.isRegularFile(output.resolve("SYS/jsp/jsp/01-valid.xls")));
        assertTrue(Files.isRegularFile(output.resolve("SYS/jsp/jsp/02-partial.xls")));
        assertFalse(Files.exists(output.resolve("SYS/jsp/jsp/03-invalid.xls")));
        assertTrue(Files.isRegularFile(output.resolve("SYS/jsp/jsp/04-valid.xls")));

        FileResult recovered = summary.results().get(1);
        assertEquals(SourceAnalysisException.PARSE_ERROR, recovered.errorCode());
        assertTrue(recovered.message().contains("Source excerpt (original JSP)"), recovered.message());
        FileResult failed = summary.results().get(2);
        assertEquals(SourceAnalysisException.PARSE_ERROR, failed.errorCode());
        assertTrue(failed.message().contains("Source excerpt (original JSP)"), failed.message());
        Path logs = output.resolve("SYS").resolve("logs");
        assertTrue(Files.isDirectory(logs));
        try (var paths = Files.walk(logs)) {
            List<String> logContents = paths.filter(Files::isRegularFile).map(path -> {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8);
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            }).toList();
            assertTrue(logContents.stream().anyMatch(log -> log.contains(diagnostic.getMessage())
                    && log.contains(failed.message())
                    && log.contains("unsupported") && log.contains("^")
                    && log.contains(recovered.message()) && log.contains("유효 기간")), logContents.toString());
        }
    }

    @Test
    void continuesAfterAParseFailureAndIsolatesTheFailedFile() throws IOException {
        Path module = tempDir.resolve("sources").resolve("mixed");
        write(module.resolve("Bad.java"), "class Bad { void broken( { }");
        write(module.resolve("Good.java"), "class Good { int add(int a, int b) { return a + b; } }");
        Path output = tempDir.resolve("output");
        List<ProgressEvent> events = new ArrayList<>();

        RunSummary summary = new JobRunner().run(config(SourceMode.JAVA, output, module), events::add);

        assertEquals(2, summary.totalCount());
        assertEquals(1, summary.successCount());
        assertEquals(1, summary.failureCount());
        assertEquals(FileResult.FileStatus.FAILED, summary.results().get(0).status());
        assertEquals(SourceAnalysisException.PARSE_ERROR, summary.results().get(0).errorCode());
        assertEquals(FileResult.FileStatus.SUCCESS, summary.results().get(1).status());
        assertFalse(Files.exists(output.resolve("SYS/java/mixed/Bad.xls")));
        assertTrue(Files.isRegularFile(output.resolve("SYS/java/mixed/Good.xls")));
        assertEquals(List.of(FileResult.FileStatus.FAILED, FileResult.FileStatus.SUCCESS),
                events.stream().map(ProgressEvent::status).toList());
    }

    @Test
    void separatesJavaAndJspWorkbooksAndLogsBySystem() throws IOException {
        Path module = tempDir.resolve("sources").resolve("physical-module");
        Path javaSource = write(module.resolve("nested").resolve("Same.java"),
                "class Same { void javaOnlyMethod() {} }");
        Path jspSource = write(module.resolve("nested").resolve("Same.jsp"),
                "<html><body><a href=\"/jsp-only\">JspOnlyLink</a></body></html>");
        String javaHash = sha256(javaSource);
        String jspHash = sha256(jspSource);
        Path output = tempDir.resolve("output");
        String system = "시스템";
        String otherSystem = "다른시스템";
        ModuleSelection selected = new ModuleSelection(module, system, "display-module");
        ModuleSelection otherSelected = new ModuleSelection(module, otherSystem, "display-module");
        JobConfig javaConfig = new JobConfig(SourceMode.JAVA, system, "author", null, "program",
                "overview", output, OutputFormat.XLS, List.of(selected));
        JobConfig jspConfig = new JobConfig(SourceMode.JSP, system, "author", null, "program",
                "overview", output, OutputFormat.XLS, List.of(selected));
        JobConfig otherConfig = new JobConfig(SourceMode.JAVA, otherSystem, "author", null, "program",
                "overview", output, OutputFormat.XLS, List.of(otherSelected));

        RunSummary javaRun = new JobRunner().run(javaConfig, event -> { });
        RunSummary jspRun = new JobRunner().run(jspConfig, event -> { });
        RunSummary otherRun = new JobRunner().run(otherConfig, event -> { });

        assertEquals(1, javaRun.successCount());
        assertEquals(1, jspRun.successCount());
        assertEquals(1, otherRun.successCount());
        Path javaWorkbook = output.resolve(system).resolve("java").resolve("physical-module")
                .resolve("nested").resolve("Same.xls");
        Path jspWorkbook = output.resolve(system).resolve("jsp").resolve("physical-module")
                .resolve("nested").resolve("Same.xls");
        Path otherWorkbook = output.resolve(otherSystem).resolve("java").resolve("physical-module")
                .resolve("nested").resolve("Same.xls");
        assertEquals(javaWorkbook, javaRun.results().get(0).outputFile());
        assertEquals(jspWorkbook, jspRun.results().get(0).outputFile());
        assertEquals(otherWorkbook, otherRun.results().get(0).outputFile());
        assertTrue(Files.isRegularFile(javaWorkbook));
        assertTrue(Files.isRegularFile(jspWorkbook));
        assertTrue(Files.isRegularFile(otherWorkbook));
        assertTrue(workbookContains(javaWorkbook, "javaOnlyMethod"));
        assertFalse(workbookContains(javaWorkbook, "JspOnlyLink"));
        assertTrue(workbookContains(jspWorkbook, "JspOnlyLink"));
        assertFalse(workbookContains(jspWorkbook, "javaOnlyMethod"));
        assertTrue(workbookContains(otherWorkbook, "javaOnlyMethod"));
        Path logs = output.resolve(system).resolve("logs");
        assertEquals(logs, javaRun.logFile().getParent());
        assertEquals(logs, jspRun.logFile().getParent());
        assertFalse(javaRun.logFile().equals(jspRun.logFile()));
        assertTrue(Files.isRegularFile(javaRun.logFile()));
        assertTrue(Files.isRegularFile(jspRun.logFile()));
        assertEquals(output.resolve(otherSystem).resolve("logs"), otherRun.logFile().getParent());
        assertTrue(Files.isRegularFile(otherRun.logFile()));
        assertEquals(javaHash, sha256(javaSource));
        assertEquals(jspHash, sha256(jspSource));
    }

    private static boolean workbookContains(Path file, String text) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            for (var sheet : workbook) {
                for (var row : sheet) {
                    for (var cell : row) {
                        if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains(text)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    @Test
    void laterJobWinsWhenTwoModulesShareTheSameOutputPath() throws IOException {
        Path first = tempDir.resolve("one").resolve("shared");
        Path second = tempDir.resolve("two").resolve("shared");
        write(first.resolve("A.java"), "class First {}");
        write(second.resolve("A.java"), "class Second { static int value = 1; }");
        Path output = tempDir.resolve("output");

        RunSummary summary = new JobRunner().run(config(SourceMode.JAVA, output, first, second), event -> {
        });

        assertEquals(2, summary.successCount());
        assertEquals(summary.results().get(0).outputFile(), summary.results().get(1).outputFile());
        Path generated = output.resolve("SYS/java/shared/A.xls");
        assertTrue(Files.isRegularFile(generated));
        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(generated))) {
            assertEquals(10, workbook.getNumberOfSheets());
        }
    }

    @Test
    void preservesSourcesAndUnrelatedOutputFiles() throws IOException {
        Path module = tempDir.resolve("sources").resolve("module");
        Path source = write(module.resolve("Keep.java"), "class Keep {}");
        Path output = tempDir.resolve("output");
        Path keep = output.resolve("keep.txt");
        Path other = output.resolve("SYS/java/module/Other.xls");
        write(keep, "keep");
        write(other, "sentinel");
        String sourceHash = sha256(source);

        RunSummary summary = new JobRunner().run(config(SourceMode.JAVA, output, module), event -> {
        });

        assertEquals(1, summary.successCount());
        assertEquals(sourceHash, sha256(source));
        assertEquals("keep", Files.readString(keep));
        assertEquals("sentinel", Files.readString(other));
    }

    @Test
    void scansOnlyTheConfiguredSourceMode() throws IOException {
        Path module = tempDir.resolve("sources").resolve("mixed");
        write(module.resolve("A.java"), "class A {}");
        write(module.resolve("B.jsp"),
                "<%@ page contentType=\"text/html\" %><html><body><a href=\"/x\">Go</a></body></html>");
        Path output = tempDir.resolve("output");

        RunSummary javaSummary = new JobRunner().run(config(SourceMode.JAVA, output, module), event -> {
        });
        RunSummary jspSummary = new JobRunner().run(config(SourceMode.JSP, output, module), event -> {
        });

        assertEquals(1, javaSummary.totalCount());
        assertEquals("A.java", javaSummary.results().get(0).sourceFile().getFileName().toString());
        assertEquals(1, jspSummary.totalCount());
        assertEquals("B.jsp", jspSummary.results().get(0).sourceFile().getFileName().toString());
        assertTrue(Files.isRegularFile(output.resolve("SYS/java/mixed/A.xls")));
        assertTrue(Files.isRegularFile(output.resolve("SYS/jsp/mixed/B.xls")));
    }

    @Test
    void emptySourceFolderProducesZeroResultsWithoutProgress() throws IOException {
        Path module = tempDir.resolve("sources").resolve("empty");
        Files.createDirectories(module);
        Path output = tempDir.resolve("output");
        AtomicInteger progressCalls = new AtomicInteger();

        RunSummary summary = new JobRunner().run(config(SourceMode.JAVA, output, module),
                event -> progressCalls.incrementAndGet());

        assertEquals(0, summary.totalCount());
        assertEquals(0, summary.successCount());
        assertEquals(0, summary.warningCount());
        assertEquals(0, summary.failureCount());
        assertTrue(summary.outputFiles().isEmpty());
        assertEquals(0, progressCalls.get());
        assertTrue(Files.isRegularFile(summary.logFile()));
        assertEquals("", summary.logError());
    }

    @Test
    void createsDistinctLogsForConsecutiveEmptyRuns() throws IOException {
        Path module = tempDir.resolve("sources").resolve("empty");
        Files.createDirectories(module);
        JobConfig config = config(SourceMode.JAVA, tempDir.resolve("output"), module);

        RunSummary first = new JobRunner().run(config, event -> { });
        RunSummary second = new JobRunner().run(config, event -> { });

        assertFalse(first.logFile().equals(second.logFile()));
        assertTrue(Files.isRegularFile(first.logFile()));
        assertTrue(Files.isRegularFile(second.logFile()));
        assertTrue(first.logFile().getFileName().toString()
                .matches("specgen-\\d{8}-\\d{6}(-\\d+)?\\.log"));
        assertTrue(Files.readString(first.logFile(), StandardCharsets.UTF_8).contains("total=0"));
        assertEquals("", first.logError());
        assertEquals("", second.logError());
    }

    @Test
    void logCreationFailureDoesNotPreventWorkbookGeneration() throws IOException {
        Path module = tempDir.resolve("sources").resolve("module");
        write(module.resolve("Valid.java"), "class Valid {}");
        Path output = tempDir.resolve("output");
        write(output.resolve("SYS/logs"), "blocks log directory");

        RunSummary summary = new JobRunner().run(config(SourceMode.JAVA, output, module), event -> { });

        assertEquals(1, summary.successCount());
        assertTrue(Files.isRegularFile(summary.results().get(0).outputFile()));
        assertEquals(null, summary.logFile());
        assertFalse(summary.logError().isEmpty());
    }

    @Test
    void refusesLinkedLogDirectoryWithoutPreventingWorkbookGeneration() throws Exception {
        Path module = tempDir.resolve("sources").resolve("module");
        write(module.resolve("Valid.java"), "class Valid {}");
        Path output = tempDir.resolve("output");
        Path system = Files.createDirectories(output.resolve("SYS"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path link = system.resolve("logs");
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows");
        if (windows) {
            Process create = new ProcessBuilder("cmd.exe", "/c",
                    "mklink /J \"" + link + "\" \"" + outside + "\"").redirectErrorStream(true).start();
            create.getInputStream().readAllBytes();
            assertEquals(0, create.waitFor(), "could not create log junction fixture");
        } else {
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | SecurityException | IOException exception) {
                assumeTrue(false, "symbolic links unavailable: " + exception);
            }
        }
        try {
            RunSummary summary = new JobRunner().run(config(SourceMode.JAVA, output, module), event -> { });
            assertEquals(1, summary.successCount());
            assertTrue(Files.isRegularFile(output.resolve("SYS/java/module/Valid.xls")));
            assertEquals(null, summary.logFile());
            assertFalse(summary.logError().isEmpty());
            try (var paths = Files.list(outside)) {
                assertEquals(0, paths.count());
            }
        } finally {
            if (windows) {
                Process remove = new ProcessBuilder("cmd.exe", "/c", "rmdir \"" + link + "\"")
                        .redirectErrorStream(true).start();
                remove.getInputStream().readAllBytes();
                assertEquals(0, remove.waitFor());
            } else {
                Files.deleteIfExists(link);
            }
        }
    }

    @Test
    void recordsWarningWhenSourceDateMetadataCannotBeRead() throws IOException {
        Path module = tempDir.resolve("sources").resolve("module");
        Path source = write(module.resolve("Warn.java"), "class Warn {}");
        Path output = tempDir.resolve("output");
        SourceAnalyzer<JavaFacts> deletingAnalyzer = path -> {
            JavaFacts facts = new JavaParserAnalyzer().analyze(path);
            try {
                Files.delete(path);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return facts;
        };
        JobRunner runner = new JobRunner(new JobScanner(), deletingAnalyzer, new PmdJspAnalyzer(),
                new JavaSpecMapper(), new JspSpecMapper(), new PoiWorkbookWriter());

        RunSummary summary = runner.run(config(SourceMode.JAVA, output, module), event -> {
        });

        FileResult result = summary.results().get(0);
        assertEquals(FileResult.FileStatus.WARNING, result.status());
        assertEquals(JobRunner.DATE_METADATA_ERROR, result.errorCode());
        assertEquals(1, summary.warningCount());
        assertEquals(0, summary.failureCount());
        assertFalse(Files.exists(source));
        assertTrue(Files.isRegularFile(result.outputFile()));
    }

    private JobConfig config(SourceMode mode, Path output, Path... sourceFolders) {
        List<ModuleSelection> modules = new ArrayList<>();
        for (Path folder : sourceFolders) {
            modules.add(new ModuleSelection(folder, "SYS", folder.getFileName().toString()));
        }
        return new JobConfig(mode, "SYS", "author", null, "program", "overview", output,
                OutputFormat.XLS, modules);
    }

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
