package com.comus93.specgen.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        assertTrue(Files.isRegularFile(output.resolve("first").resolve("A.xls")));
        assertTrue(Files.isRegularFile(output.resolve("second").resolve("B.xls")));
    }

    @Test
    void parseFailureShowsOriginalSourceExcerptAndCaretInResult() throws IOException {
        Path module = tempDir.resolve("sources").resolve("jsp");
        Path source = write(module.resolve("Unsupported.jsp"), String.join("\n",
                "<%@ taglib prefix=\"c\" uri=\"http://java.sun.com/jsp/jstl/core\" %>",
                "<div <c:if test=\"\u0024{edit}\"><span>not-an-attribute</span></c:if> "
                        + "style=\"display:flex\">",
                "  <cpc:escv code=\"MIP-M0006\" value=\"유효 기간\" />",
                "</div>"));

        RunSummary summary = new JobRunner().run(
                config(SourceMode.JSP, tempDir.resolve("output"), module), event -> { });

        FileResult failed = summary.results().get(0);
        assertEquals(FileResult.FileStatus.FAILED, failed.status());
        assertEquals(SourceAnalysisException.PARSE_ERROR, failed.errorCode());
        assertTrue(failed.message().contains("Source excerpt (original JSP)"));
        assertTrue(failed.message().contains(">    2 | <div <c:if"), failed.message());
        assertTrue(failed.message().contains("not-an-attribute"), failed.message());
        assertTrue(failed.message().contains("MIP-M0006"), failed.message());
        assertTrue(failed.message().contains("^"), failed.message());
        assertTrue(Files.exists(source));
    }

    @Test
    void jspParseFailureDoesNotStopLaterFilesOrEraseSuccessfulWorkbooks() throws IOException {
        Path module = tempDir.resolve("sources").resolve("jsp");
        write(module.resolve("01-valid.jsp"), "<html><body><input name=\"first\"/></body></html>");
        write(module.resolve("02-invalid.jsp"),
                "<div <c:if test=\"\u0024{edit}\"><span>unsupported</span></c:if> "
                        + "style=\"display:flex\"></div>");
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
        assertTrue(Files.isRegularFile(output.resolve("jsp").resolve("01-valid.xls")));
        assertFalse(Files.exists(output.resolve("jsp").resolve("02-invalid.xls")));
        assertTrue(Files.isRegularFile(output.resolve("jsp").resolve("03-valid.xls")));
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
        assertFalse(Files.exists(output.resolve("mixed").resolve("Bad.xls")));
        assertTrue(Files.isRegularFile(output.resolve("mixed").resolve("Good.xls")));
        assertEquals(List.of(FileResult.FileStatus.FAILED, FileResult.FileStatus.SUCCESS),
                events.stream().map(ProgressEvent::status).toList());
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
        Path generated = output.resolve("shared").resolve("A.xls");
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
        Path other = output.resolve("module").resolve("Other.xls");
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
        assertTrue(Files.isRegularFile(output.resolve("mixed").resolve("A.xls")));
        assertTrue(Files.isRegularFile(output.resolve("mixed").resolve("B.xls")));
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
        assertFalse(Files.exists(output));
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
