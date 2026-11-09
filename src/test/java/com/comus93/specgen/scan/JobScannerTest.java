package com.comus93.specgen.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.config.ModuleSelection;
import com.comus93.specgen.config.OutputFormat;
import com.comus93.specgen.config.SourceMode;

class JobScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void plansJavaSourceWithPhysicalModuleBasenameAndRelativeHierarchy() throws IOException {
        Path source = tempDir.resolve("src").resolve("module1");
        Path sourceFile = writeSource(source.resolve("controller").resolve("A.java"));
        Path outputRoot = tempDir.resolve("output");
        ModuleSelection module = new ModuleSelection(source, "SYS1", "edited display name");

        List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, outputRoot, module));

        assertEquals(1, jobs.size());
        SourceJob job = jobs.get(0);
        assertEquals(module, job.module());
        assertEquals(sourceFile.toRealPath(), job.sourceFile());
        assertEquals(Path.of("controller", "A.java"), job.relativePath());
        assertEquals(outputRoot.resolve("SYS1").resolve("java").resolve("module1").resolve("controller").resolve("A.xls"), job.outputFile());
        assertFalse(job.outputFile().toString().contains("edited display name"));
    }

    @Test
    void usesConfiguredExcelExtension() throws IOException {
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("A.java"));

        List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, tempDir.resolve("output"),
                OutputFormat.XLSX, new ModuleSelection(source, "SYS1", "module1")));

        assertEquals(1, jobs.size());
        assertEquals("A.xlsx", jobs.get(0).outputFile().getFileName().toString());
    }

    @Test
    void filtersSourcesBySelectedMode() throws IOException {
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("A.java"));
        writeSource(source.resolve("B.jsp"));
        writeSource(source.resolve("notes.txt"));

        List<SourceJob> javaJobs = new JobScanner().plan(config(SourceMode.JAVA, tempDir.resolve("output"),
                new ModuleSelection(source, "SYS1", "module1")));
        List<SourceJob> jspJobs = new JobScanner().plan(config(SourceMode.JSP, tempDir.resolve("output"),
                new ModuleSelection(source, "SYS1", "module1")));

        assertEquals(List.of("A.java"), relativeNames(javaJobs));
        assertEquals(List.of("B.jsp"), relativeNames(jspJobs));
        assertEquals("A.xls", javaJobs.get(0).outputFile().getFileName().toString());
        assertEquals("B.xls", jspJobs.get(0).outputFile().getFileName().toString());
    }

    @Test
    void preservesModuleOrderAndSortsRelativePathsAsStrings() throws IOException {
        Path second = tempDir.resolve("second");
        Path first = tempDir.resolve("first");
        writeSource(second.resolve("Z.java"));
        writeSource(first.resolve("b.java"));
        writeSource(first.resolve("A.java"));
        writeSource(first.resolve("controller").resolve("Z.java"));
        writeSource(first.resolve("aa.java"));

        List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, tempDir.resolve("output"),
                new ModuleSelection(second, "SYS1", "second"),
                new ModuleSelection(first, "SYS1", "first")));

        assertEquals(List.of("Z.java", "A.java", "aa.java", "b.java", Path.of("controller", "Z.java").toString()),
                relativeNames(jobs));
        assertEquals("second", jobs.get(0).module().moduleName());
        assertEquals("first", jobs.get(1).module().moduleName());
    }

    @Test
    void keepsParentAndChildRegistrationsAsIndependentJobs() throws IOException {
        Path parent = tempDir.resolve("src").resolve("module1");
        Path sourceFile = writeSource(parent.resolve("controller").resolve("A.java"));
        Path child = parent.resolve("controller");
        Path outputRoot = tempDir.resolve("output");

        List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, outputRoot,
                new ModuleSelection(parent, "SYS1", "parent"),
                new ModuleSelection(child, "SYS2", "child")));

        assertEquals(2, jobs.size());
        SourceJob parentJob = jobs.get(0);
        SourceJob childJob = jobs.get(1);
        assertEquals(sourceFile.toRealPath(), parentJob.sourceFile());
        assertEquals(sourceFile.toRealPath(), childJob.sourceFile());
        assertEquals(Path.of("controller", "A.java"), parentJob.relativePath());
        assertEquals(Path.of("A.java"), childJob.relativePath());
        assertEquals(outputRoot.resolve("SYS1").resolve("java").resolve("module1").resolve("controller").resolve("A.xls"), parentJob.outputFile());
        assertEquals(outputRoot.resolve("SYS1").resolve("java").resolve("controller").resolve("A.xls"), childJob.outputFile());
        assertEquals("parent", parentJob.module().moduleName());
        assertEquals("child", childJob.module().moduleName());
    }

    @Test
    void deduplicatesOnlyExactNormalizedFolderPaths() throws IOException {
        Path shared = tempDir.resolve("shared");
        Path other = tempDir.resolve("other").resolve("shared");
        writeSource(shared.resolve("A.java"));
        writeSource(other.resolve("A.java"));
        ModuleSelection first = new ModuleSelection(shared, "SYS1", "first");
        ModuleSelection exactDuplicate = new ModuleSelection(shared.resolve("."), "SYS1", "duplicate");
        ModuleSelection sameNameDifferentFolder = new ModuleSelection(other, "SYS1", "first");

        List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, tempDir.resolve("output"),
                first, exactDuplicate, sameNameDifferentFolder));

        assertEquals(2, jobs.size());
        assertEquals(first, jobs.get(0).module());
        assertEquals(sameNameDifferentFolder, jobs.get(1).module());
    }

    @Test
    void reusesExistingOutputFoldersWithoutBlocking() throws IOException {
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("controller").resolve("A.java"));
        Path outputRoot = tempDir.resolve("output");
        Path existingOutput = outputRoot.resolve("SYS1").resolve("java").resolve("module1").resolve("controller").resolve("A.xls");
        Files.createDirectories(existingOutput.getParent());
        Files.writeString(existingOutput, "existing");

        List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, outputRoot,
                new ModuleSelection(source, "SYS1", "module1")));

        assertEquals(1, jobs.size());
        assertEquals(existingOutput, jobs.get(0).outputFile());
    }

    @Test
    void rejectsOutputRootInsideSourceFolder() throws IOException {
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("A.java"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new JobScanner().plan(config(SourceMode.JAVA, source.resolve("generated"),
                        new ModuleSelection(source, "SYS1", "module1"))));

        assertTrue(error.getMessage().contains("Output"));
    }

    @Test
    void rejectsOutputRootEqualToSourceFolder() throws IOException {
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("A.java"));

        assertThrows(IllegalArgumentException.class,
                () -> new JobScanner().plan(config(SourceMode.JAVA, source,
                        new ModuleSelection(source, "SYS1", "module1"))));
    }

    @Test
    void deduplicatesWindowsCaseVariantFolderRegistrationsPreservingFirst() throws IOException {
        assumeTrue(isWindows(), "Windows case-insensitive path equality check");
        Path source = tempDir.resolve("case").resolve("module1");
        Path sourceFile = writeSource(source.resolve("A.java"));
        ModuleSelection first = new ModuleSelection(source, "SYS1", "first");
        ModuleSelection caseVariant = new ModuleSelection(
                Path.of(source.toString().toUpperCase(Locale.ROOT)), "SYS1", "second");

        List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, tempDir.resolve("output"),
                first, caseVariant));

        assertEquals(1, jobs.size());
        assertEquals(first, jobs.get(0).module());
        assertEquals(sourceFile.toRealPath(), jobs.get(0).sourceFile());
    }

    @Test
    void keepsJunctionAliasRegistrationsIndependent() throws Exception {
        assumeTrue(isWindows(), "Windows junction filesystem check");
        Path target = tempDir.resolve("src").resolve("module1");
        Path targetFile = writeSource(target.resolve("A.java"));
        Path alias = tempDir.resolve("module1-alias");
        assertTrue(createJunction(alias, target), "could not create junction alias fixture");

        try {
            ModuleSelection real = new ModuleSelection(target, "SYS1", "real");
            ModuleSelection aliasSelection = new ModuleSelection(alias, "SYS1", "alias");

            List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, tempDir.resolve("output"),
                    real, aliasSelection));

            assertEquals(2, jobs.size());
            assertEquals(real, jobs.get(0).module());
            assertEquals(aliasSelection, jobs.get(1).module());
            assertEquals(targetFile.toRealPath(), jobs.get(0).sourceFile());
            assertEquals(targetFile.toRealPath(), jobs.get(1).sourceFile());
        } finally {
            removeJunction(alias);
        }
    }

    @Test
    void rejectsOutputModuleJunctionIntoSourceFolder() throws Exception {
        assumeTrue(isWindows(), "Windows junction filesystem check");
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("controller").resolve("A.java"));
        Path outputRoot = tempDir.resolve("output");
        Path junction = outputRoot.resolve("SYS1").resolve("java").resolve("module1");
        Files.createDirectories(junction.getParent());
        assertTrue(createJunction(junction, source), "could not create output junction fixture");

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> new JobScanner().plan(config(SourceMode.JAVA, outputRoot,
                            new ModuleSelection(source, "SYS1", "module1"))));
        } finally {
            removeJunction(junction);
        }
    }

    @Test
    void rejectsOutputModuleJunctionOutsideOutputRoot() throws Exception {
        assumeTrue(isWindows(), "Windows junction filesystem check");
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("controller").resolve("A.java"));
        Path external = tempDir.resolve("external");
        Files.createDirectories(external);
        Path outputRoot = tempDir.resolve("output");
        Path junction = outputRoot.resolve("SYS1").resolve("java").resolve("module1");
        Files.createDirectories(junction.getParent());
        assertTrue(createJunction(junction, external), "could not create output junction fixture");

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> new JobScanner().plan(config(SourceMode.JAVA, outputRoot,
                            new ModuleSelection(source, "SYS1", "module1"))));
        } finally {
            removeJunction(junction);
        }
    }

    @Test
    void rejectsDeeperOutputDirectoryJunctionIntoSourceFolder() throws Exception {
        assumeTrue(isWindows(), "Windows junction filesystem check");
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("controller").resolve("A.java"));
        Path outputRoot = tempDir.resolve("output");
        Path moduleOutputDirectory = outputRoot.resolve("SYS1").resolve("java").resolve("module1");
        Files.createDirectories(moduleOutputDirectory);
        Path junction = moduleOutputDirectory.resolve("controller");
        assertTrue(createJunction(junction, source.resolve("controller")),
                "could not create deeper output junction fixture");

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> new JobScanner().plan(config(SourceMode.JAVA, outputRoot,
                            new ModuleSelection(source, "SYS1", "module1"))));
        } finally {
            removeJunction(junction);
        }
    }

    @Test
    void rejectsExistingDestinationReparsePoint() throws Exception {
        assumeTrue(isWindows(), "Windows junction filesystem check");
        Path source = tempDir.resolve("src").resolve("module1");
        writeSource(source.resolve("controller").resolve("A.java"));
        Path external = tempDir.resolve("external");
        Files.createDirectories(external);
        Path outputRoot = tempDir.resolve("output");
        Path destinationParent = outputRoot.resolve("SYS1").resolve("java").resolve("module1").resolve("controller");
        Files.createDirectories(destinationParent);
        Path destination = destinationParent.resolve("A.xls");
        assertTrue(createJunction(destination, external), "could not create destination junction fixture");

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> new JobScanner().plan(config(SourceMode.JAVA, outputRoot,
                            new ModuleSelection(source, "SYS1", "module1"))));
        } finally {
            removeJunction(destination);
        }
    }

    @Test
    void usesPhysicalFolderNameWhenRegisteredPathCaseDiffers() throws IOException {
        assumeTrue(isWindows(), "Windows case-insensitive filesystem check");
        Path source = tempDir.resolve("src").resolve("PhysicalModule");
        Path sourceFile = writeSource(source.resolve("controller").resolve("A.java"));
        Path registered = Path.of(source.toString().toLowerCase(Locale.ROOT));
        Path outputRoot = tempDir.resolve("output");

        List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, outputRoot,
                new ModuleSelection(registered, "SYS1", "edited")));

        assertEquals(1, jobs.size());
        assertEquals(sourceFile.toRealPath(), jobs.get(0).sourceFile());
        assertEquals(outputRoot.resolve("SYS1").resolve("java").resolve("PhysicalModule").resolve("controller").resolve("A.xls"),
                jobs.get(0).outputFile());
    }

    @Test
    void doesNotTraverseJunctionOutsideSourceFolder() throws Exception {
        assumeTrue(isWindows(), "Windows junction filesystem check");
        Path fixture = tempDir.resolve("junction-fixture");
        Path source = fixture.resolve("source");
        Path outside = fixture.resolve("outside");
        Path insideFile = writeSource(source.resolve("Inside.java"));
        writeSource(outside.resolve("Outside.java"));
        Path junction = source.resolve("linked");
        assertTrue(createJunction(junction, outside), "could not create junction fixture");

        try {
            List<SourceJob> jobs = new JobScanner().plan(config(SourceMode.JAVA, tempDir.resolve("output"),
                    new ModuleSelection(source, "SYS1", "module1")));

            assertEquals(1, jobs.size());
            assertEquals(insideFile.toRealPath(), jobs.get(0).sourceFile());
            assertFalse(jobs.stream().anyMatch(job -> job.sourceFile().getFileName().toString().equals("Outside.java")));
        } finally {
            removeJunction(junction);
        }
    }

    @Test
    void plansNestedJavaAndJspPathsUnderUnicodeSystemAndMode() throws IOException {
        Path source = tempDir.resolve("sources").resolve("physical-module");
        Path javaFile = writeSource(source.resolve("nested").resolve("deeper").resolve("Handler.java"));
        Path jspFile = writeSource(source.resolve("nested").resolve("deeper").resolve("view.jsp"));
        Path output = tempDir.resolve("output");
        ModuleSelection module = new ModuleSelection(source, "OTHER", "display name");

        for (SourceMode mode : List.of(SourceMode.JAVA, SourceMode.JSP)) {
            JobConfig jobConfig = new JobConfig(mode, "시스템", "author", null, "program", "overview",
                    output, OutputFormat.XLSX, List.of(module));
            List<SourceJob> jobs = new JobScanner().plan(jobConfig);
            String sourceName = mode == SourceMode.JAVA ? "Handler.java" : "view.jsp";
            String outputName = mode == SourceMode.JAVA ? "Handler.xlsx" : "view.xlsx";

            assertEquals(1, jobs.size());
            assertEquals(mode == SourceMode.JAVA ? javaFile.toRealPath() : jspFile.toRealPath(),
                    jobs.get(0).sourceFile());
            assertEquals(Path.of("nested", "deeper", sourceName), jobs.get(0).relativePath());
            assertEquals(output.resolve("시스템").resolve(mode.name().toLowerCase(Locale.ROOT))
                    .resolve("physical-module").resolve("nested").resolve("deeper").resolve(outputName),
                    jobs.get(0).outputFile());
        }
    }

    @Test
    void rejectsSystemModeOutputInsideSourceEvenWhenBaseIsOutside() throws IOException {
        for (SourceMode mode : List.of(SourceMode.JAVA, SourceMode.JSP)) {
            Path output = tempDir.resolve("output-" + mode);
            Path source = output.resolve("SYS1").resolve(mode.name().toLowerCase(Locale.ROOT)).resolve("module1");
            Path sourceFile = writeSource(source.resolve(mode == SourceMode.JAVA ? "A.java" : "A.jsp"));

            assertThrows(IllegalArgumentException.class,
                    () -> new JobScanner().plan(config(mode, output,
                            new ModuleSelection(source, "SYS1", "module1"))));
            assertEquals("class Sample {}", Files.readString(sourceFile));
        }
    }

    @Test
    void rejectsSystemAndModeLinksOutsideOutputOrIntoSource() throws Exception {
        Path source = tempDir.resolve("src").resolve("module1");
        Path sourceFile = writeSource(source.resolve("A.java"));
        Path external = tempDir.resolve("external");
        Files.createDirectories(external);

        for (String segment : List.of("system", "mode")) {
            for (Path target : List.of(external, source)) {
                Path output = tempDir.resolve("output-" + segment + "-" + target.getFileName());
                Path link = segment.equals("system") ? output.resolve("SYS1") : output.resolve("SYS1").resolve("java");
                Files.createDirectories(link.getParent());
                if (isWindows()) {
                    assertTrue(createJunction(link, target), "could not create output junction fixture");
                } else {
                    try {
                        Files.createSymbolicLink(link, target);
                    } catch (UnsupportedOperationException | SecurityException | IOException e) {
                        assumeTrue(false, "symbolic links unavailable: " + e);
                    }
                }

                try {
                    assertThrows(IllegalArgumentException.class,
                            () -> new JobScanner().plan(config(SourceMode.JAVA, output,
                                    new ModuleSelection(source, "SYS1", "module1"))),
                            segment + " link to " + target);
                    assertEquals("class Sample {}", Files.readString(sourceFile));
                } finally {
                    if (isWindows()) {
                        removeJunction(link);
                    } else {
                        Files.deleteIfExists(link);
                    }
                }
            }
        }
    }

    @Test
    void rejectsDerivedDestinationsInsideAnyEmptySourceRoot() throws IOException {
        for (String destination : List.of("SYS1", "SYS1/java", "SYS1/java/other", "SYS1/logs")) {
            Path output = tempDir.resolve("empty-" + destination.replace('/', '-'));
            Path source = output.resolve(destination);
            Path other = tempDir.resolve("other");
            Files.createDirectories(source);
            Files.createDirectories(other);
            assertThrows(IllegalArgumentException.class,
                    () -> new JobScanner().plan(config(SourceMode.JAVA, output,
                            new ModuleSelection(other, "SYS1", "other"),
                            new ModuleSelection(source, "SYS1", "empty"))), destination);
        }
    }

    @Test
    void rejectsDanglingDerivedLinkEvenWithoutMatchingSources() throws IOException {
        Path source = tempDir.resolve("empty-source");
        Path output = tempDir.resolve("dangling-output");
        Files.createDirectories(source);
        Files.createDirectories(output);
        try {
            Files.createSymbolicLink(output.resolve("SYS1"), tempDir.resolve("missing-target"));
        } catch (UnsupportedOperationException | SecurityException | IOException e) {
            assumeTrue(false, "symbolic links unavailable: " + e);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new JobScanner().plan(config(SourceMode.JAVA, output,
                        new ModuleSelection(source, "SYS1", "empty"))));
    }

    @Test
    void rejectsInternalAndDanglingOutputLinksAtEveryDerivedLevel() throws IOException {
        Path source = tempDir.resolve("link-source").resolve("module1");
        writeSource(source.resolve("controller").resolve("A.java"));
        for (String segment : List.of("SYS1", "SYS1/java", "SYS1/java/module1",
                "SYS1/java/module1/controller", "SYS1/java/module1/controller/A.xls")) {
            for (boolean dangling : List.of(false, true)) {
                Path output = tempDir.resolve("links-" + segment.replace('/', '-') + "-" + dangling);
                Path target = output.resolve("internal-target");
                Files.createDirectories(output);
                if (!dangling) {
                    Files.createDirectories(target);
                }
                Path link = output.resolve(segment);
                Files.createDirectories(link.getParent());
                try {
                    Files.createSymbolicLink(link, target);
                } catch (UnsupportedOperationException | SecurityException | IOException e) {
                    assumeTrue(false, "symbolic links unavailable: " + e);
                }
                assertThrows(IllegalArgumentException.class,
                        () -> new JobScanner().plan(config(SourceMode.JAVA, output,
                                new ModuleSelection(source, "SYS1", "module1"))), segment + " dangling=" + dangling);
            }
        }
    }

    @Test
    void rejectsInternalOutputJunctions() throws Exception {
        assumeTrue(isWindows(), "Windows junction filesystem check");
        Path source = tempDir.resolve("junction-source").resolve("module1");
        writeSource(source.resolve("controller").resolve("A.java"));
        for (String segment : List.of("SYS1", "SYS1/java", "SYS1/java/module1",
                "SYS1/java/module1/controller", "SYS1/java/module1/controller/A.xls")) {
            Path output = tempDir.resolve("junction-" + segment.replace('/', '-'));
            Path target = output.resolve("internal-target");
            Files.createDirectories(target);
            Path link = output.resolve(segment);
            Files.createDirectories(link.getParent());
            assertTrue(createJunction(link, target), "could not create output junction fixture");
            try {
                assertThrows(IllegalArgumentException.class,
                        () -> new JobScanner().plan(config(SourceMode.JAVA, output,
                                new ModuleSelection(source, "SYS1", "module1"))), segment);
            } finally {
                removeJunction(link);
            }
        }
    }

    @Test
    void leavesLinkedLogsComponentForRunLog() throws IOException {
        Path source = tempDir.resolve("logs-source").resolve("module1");
        writeSource(source.resolve("A.java"));
        for (boolean dangling : List.of(false, true)) {
            Path output = tempDir.resolve("logs-output-" + dangling);
            Path target = tempDir.resolve("logs-target-" + dangling);
            if (!dangling) {
                Files.createDirectories(target);
            }
            Path logs = output.resolve("SYS1").resolve("logs");
            Files.createDirectories(logs.getParent());
            try {
                Files.createSymbolicLink(logs, target);
            } catch (UnsupportedOperationException | SecurityException | IOException e) {
                assumeTrue(false, "symbolic links unavailable: " + e);
            }
            assertEquals(1, new JobScanner().plan(config(SourceMode.JAVA, output,
                    new ModuleSelection(source, "SYS1", "module1"))).size());
        }
    }

    private JobConfig config(SourceMode mode, Path outputRoot, ModuleSelection... modules) {
        return config(mode, outputRoot, OutputFormat.XLS, modules);
    }

    private JobConfig config(SourceMode mode, Path outputRoot, OutputFormat format, ModuleSelection... modules) {
        return new JobConfig(mode, "SYS1", "author", null, "program", "overview", outputRoot, format,
                List.of(modules));
    }

    private static Path writeSource(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Sample {}");
        return file;
    }

    private static List<String> relativeNames(List<SourceJob> jobs) {
        return jobs.stream().map(job -> job.relativePath().toString()).toList();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static boolean createJunction(Path link, Path target) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("cmd.exe", "/c",
                "mklink /J \"" + link + "\" \"" + target + "\"").redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor() == 0;
    }

    private static void removeJunction(Path junction) throws IOException, InterruptedException {
        if (Files.exists(junction, LinkOption.NOFOLLOW_LINKS)) {
            Process process = new ProcessBuilder("cmd.exe", "/c", "rmdir \"" + junction + "\"")
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            process.waitFor();
        }
    }
}
