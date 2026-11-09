package com.comus93.specgen.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void readsUtf8JobWithModulesAndDefaults() throws IOException {
        Path json = writeJson("""
                {
                  "mode": "JAVA",
                  "system": "SYS1",
                  "author": "김작성",
                  "authoringDateOverride": null,
                  "programName": "프로그램 사양서",
                  "overview": "한글 개요",
                  "outputDirectory": "C:\\\\spec output\\\\결과",
                  "modules": [
                    {
                      "sourceFolder": "C:\\\\SYS1_PJT1\\\\src\\\\main\\\\java\\\\ext\\\\sys1\\\\module1",
                      "subsystem": "SYS2",
                      "moduleName": "모듈1"
                    },
                    {
                      "sourceFolder": "C:\\\\SYS1_PJT1\\\\src\\\\main\\\\java\\\\ext\\\\sys1\\\\module2",
                      "moduleName": "module2"
                    }
                  ]
                }
                """);

        JobConfig config = new JsonJobConfigReader().read(json);

        assertEquals(SourceMode.JAVA, config.mode());
        assertEquals("SYS1", config.system());
        assertEquals("김작성", config.author());
        assertNull(config.authoringDateOverride());
        assertEquals("프로그램 사양서", config.programName());
        assertEquals("한글 개요", config.overview());
        assertEquals(Path.of("C:\\spec output\\결과"), config.outputDirectory());
        assertEquals(OutputFormat.XLS, config.outputFormat());
        assertEquals(2, config.modules().size());

        ModuleSelection first = config.modules().get(0);
        assertEquals(Path.of("C:\\SYS1_PJT1\\src\\main\\java\\ext\\sys1\\module1"), first.sourceFolder());
        assertEquals("SYS2", first.subsystem());
        assertEquals("모듈1", first.moduleName());

        ModuleSelection second = config.modules().get(1);
        assertEquals("SYS1", second.subsystem(), "missing subsystem defaults to the job system");
        assertEquals("module2", second.moduleName());
    }

    @Test
    void readsAuthoringDateOverrideAndXlsx() throws IOException {
        Path json = writeJson("""
                {
                  "mode": "JSP",
                  "system": "SYS1",
                  "authoringDateOverride": "2026-11-05",
                  "outputDirectory": "D:\\\\SpecOutput",
                  "outputFormat": "xlsx",
                  "modules": [
                    {
                      "sourceFolder": "C:\\\\SYS1_PJT1\\\\src\\\\main\\\\webapp",
                      "subsystem": "SYS1",
                      "moduleName": "todo"
                    }
                  ]
                }
                """);

        JobConfig config = new JsonJobConfigReader().read(json);

        assertEquals(SourceMode.JSP, config.mode());
        assertEquals(LocalDate.of(2026, 11, 5), config.authoringDateOverride());
        assertEquals(OutputFormat.XLSX, config.outputFormat());
    }

    @Test
    void keepsLocalPathsWithoutCanonicalNormalization() throws IOException {
        Path json = writeJson("""
                {
                  "mode": "JAVA",
                  "system": "SYS1",
                  "outputDirectory": "D:\\\\SpecOutput",
                  "modules": [
                    {
                      "sourceFolder": "C:\\\\root\\\\module\\\\..\\\\module1",
                      "subsystem": "SYS1",
                      "moduleName": "module1"
                    }
                  ]
                }
                """);

        JobConfig config = new JsonJobConfigReader().read(json);

        assertEquals("C:\\root\\module\\..\\module1", config.modules().get(0).sourceFolder().toString());
    }

    @Test
    void rejectsInvalidAuthoringDate() throws IOException {
        Path json = writeJson("""
                {
                  "mode": "JAVA",
                  "system": "SYS1",
                  "authoringDateOverride": "2026-02-30",
                  "outputDirectory": "D:\\\\SpecOutput",
                  "modules": [
                    {
                      "sourceFolder": "C:\\\\root\\\\module1",
                      "subsystem": "SYS1",
                      "moduleName": "module1"
                    }
                  ]
                }
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new JsonJobConfigReader().read(json));
        assertTrue(error.getMessage().contains("authoringDateOverride"));
    }

    @Test
    void rejectsInvalidMode() throws IOException {
        Path json = writeJson("""
                {
                  "mode": "java",
                  "system": "SYS1",
                  "outputDirectory": "D:\\\\SpecOutput",
                  "modules": [
                    {
                      "sourceFolder": "C:\\\\root\\\\module1",
                      "subsystem": "SYS1",
                      "moduleName": "module1"
                    }
                  ]
                }
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new JsonJobConfigReader().read(json));
        assertTrue(error.getMessage().contains("mode"));
    }

    @Test
    void rejectsEmptyModuleList() throws IOException {
        Path json = writeJson("""
                {
                  "mode": "JAVA",
                  "system": "SYS1",
                  "outputDirectory": "D:\\\\SpecOutput",
                  "modules": []
                }
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new JsonJobConfigReader().read(json));
        assertTrue(error.getMessage().contains("modules"));
    }

    @Test
    void rejectsInvalidOutputFormat() throws IOException {
        Path json = writeJson("""
                {
                  "mode": "JAVA",
                  "system": "SYS1",
                  "outputDirectory": "D:\\\\SpecOutput",
                  "outputFormat": "XLS",
                  "modules": [
                    {
                      "sourceFolder": "C:\\\\root\\\\module1",
                      "subsystem": "SYS1",
                      "moduleName": "module1"
                    }
                  ]
                }
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new JsonJobConfigReader().read(json));
        assertTrue(error.getMessage().contains("outputFormat"));
    }

    @Test
    void acceptsUnicodeSystemAsOnePathSegment() {
        JobConfig config = configWithSystem("시스템-한글");

        assertEquals("시스템-한글", config.system());
    }

    @Test
    void rejectsSystemsThatAreNotSafeWindowsPathSegments() {
        for (String system : new String[] {
                "", " ", ".", "..", "../escape", "a/b", "a\\b",
                "CON", "con.txt", "PrN.xls", "AUX", "NUL.log",
                "COM1", "com9.txt", "LPT1", "lpt9.xls",
                "CONIN$", "conout$.txt", "COM¹.txt", "lpt²",
                "name.", "name ", "a<b", "a>b", "a:b", "a\"b",
                "a|b", "a?b", "a*b", "a\u0001b", "a\u0085b"
        }) {
            assertThrows(IllegalArgumentException.class, () -> configWithSystem(system), system);
        }
    }

    private JobConfig configWithSystem(String system) {
        return new JobConfig(SourceMode.JAVA, system, "author", null, "program", "overview",
                tempDir.resolve("output"), OutputFormat.XLS,
                java.util.List.of(new ModuleSelection(tempDir.resolve("module"), "SYS1", "module")));
    }

    private Path writeJson(String json) throws IOException {
        Path file = tempDir.resolve("job.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }
}
