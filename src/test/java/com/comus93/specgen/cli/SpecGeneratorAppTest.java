package com.comus93.specgen.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.SpecGeneratorApp;
import com.comus93.specgen.config.SourceMode;
import com.comus93.specgen.excel.PoiWorkbookWriter;
import com.comus93.specgen.facts.JavaFacts;
import com.comus93.specgen.mapper.JavaSpecMapper;
import com.comus93.specgen.mapper.JspSpecMapper;
import com.comus93.specgen.parser.JavaParserAnalyzer;
import com.comus93.specgen.parser.PmdJspAnalyzer;
import com.comus93.specgen.parser.SourceAnalyzer;
import com.comus93.specgen.runner.JobRunner;
import com.comus93.specgen.scan.JobScanner;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpecGeneratorAppTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsTwoForInvalidArgumentsConfigurationAndInputPaths() throws IOException {
        Path malformed = write(tempDir.resolve("malformed.json"), "{");
        Path missing = tempDir.resolve("missing.json");

        assertEquals(2, run());
        assertEquals(2, run("generate"));
        assertEquals(2, run("generate", "--config"));
        assertEquals(2, run("generate", "--wrong", missing.toString()));
        assertEquals(2, run("unknown"));
        assertEquals(2, run("gui"));
        assertEquals(2, run("generate", "--config", missing.toString()));
        assertEquals(2, run("generate", "--config", malformed.toString()));

        Path source = tempDir.resolve("sources").resolve("module");
        write(source.resolve("A.java"), "class A {}");
        Path invalidOutput = source.resolve("generated");
        Path job = writeJob(SourceMode.JAVA, invalidOutput, source);
        assertEquals(2, run("generate", "--config", job.toString()));
        assertFalse(Files.exists(invalidOutput));
    }

    @Test
    void returnsZeroForSuccessfulGeneration() throws IOException {
        Path source = tempDir.resolve("sources").resolve("module");
        write(source.resolve("A.java"), "class A { int add(int a, int b) { return a + b; } }");
        Path output = tempDir.resolve("output");
        Path job = writeJob(SourceMode.JAVA, output, source);

        int exitCode = run("generate", "--config", job.toString());

        assertEquals(0, exitCode);
        assertTrue(Files.isRegularFile(output.resolve("module").resolve("A.xls")));
    }

    @Test
    void returnsOneWhenAnyFileFailsButKeepsSuccessfulOutputs() throws IOException {
        Path source = tempDir.resolve("sources").resolve("module");
        write(source.resolve("Bad.java"), "class Bad { void broken( { }");
        write(source.resolve("Good.java"), "class Good {}");
        Path output = tempDir.resolve("output");
        Path job = writeJob(SourceMode.JAVA, output, source);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = SpecGeneratorApp.runCli(new String[]{"generate", "--config", job.toString()},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, exitCode);
        assertTrue(Files.isRegularFile(output.resolve("module").resolve("Good.xls")));
        assertFalse(Files.exists(output.resolve("module").resolve("Bad.xls")));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Bad.java"));
    }

    @Test
    void warningOnlyRunReturnsZero() throws IOException {
        Path source = tempDir.resolve("sources").resolve("module");
        write(source.resolve("Warn.java"), "class Warn {}");
        Path output = tempDir.resolve("output");
        Path job = writeJob(SourceMode.JAVA, output, source);
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exitCode = SpecGeneratorApp.runCli(new String[]{"generate", "--config", job.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), runner);

        assertEquals(0, exitCode);
        assertTrue(Files.isRegularFile(output.resolve("module").resolve("Warn.xls")));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("WARNING"));
    }

    private int run(String... args) {
        return SpecGeneratorApp.runCli(args,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private Path writeJob(SourceMode mode, Path output, Path... sourceFolders) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mode", mode.name());
        root.put("system", "SYS");
        root.put("author", "author");
        root.put("programName", "program");
        root.put("overview", "overview");
        root.put("outputDirectory", output.toString());
        List<Map<String, String>> modules = new ArrayList<>();
        for (Path sourceFolder : sourceFolders) {
            Map<String, String> module = new LinkedHashMap<>();
            module.put("sourceFolder", sourceFolder.toString());
            module.put("subsystem", "SYS");
            module.put("moduleName", sourceFolder.getFileName().toString());
            modules.add(module);
        }
        root.put("modules", modules);
        Path file = Files.createTempFile(tempDir, "job-", ".json");
        new ObjectMapper().writeValue(file.toFile(), root);
        return file;
    }

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
