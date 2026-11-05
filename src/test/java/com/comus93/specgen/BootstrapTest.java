package com.comus93.specgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BootstrapTest {

    private static final List<String> GOLDEN_FILES = List.of(
            "docs/golden/JavaParser_TodoController_ProgramSpec_Grayscale.xlsx",
            "docs/golden/JavaParser_TodoService_ProgramSpec_Grayscale.xlsx",
            "docs/golden/PMD_JSP_todo_ProgramSpec_Grayscale.xlsx",
            "docs/golden/PMD_JSP_list-todos_ProgramSpec_Grayscale.xlsx");

    private static final Path MANIFEST = Path.of("docs", "test", "golden-manifest.sha256");
    private static final Path TEST_RESOURCE_GOLDEN = Path.of("src", "test", "resources", "golden");

    @Test
    void goldenFilesExistWithRecordedSha256() throws IOException {
        Path root = projectRoot();
        Path manifestPath = root.resolve(MANIFEST);
        if (!Files.isRegularFile(manifestPath)) {
            fail("Golden manifest is missing: " + manifestPath);
        }

        Map<String, String> recorded = readManifest(manifestPath);
        List<String> failures = new ArrayList<>();

        for (String relativePath : GOLDEN_FILES) {
            String expectedHash = recorded.get(relativePath);
            if (expectedHash == null) {
                failures.add("Manifest has no SHA-256 entry for " + relativePath);
                continue;
            }

            Path golden = root.resolve(relativePath);
            if (!Files.isRegularFile(golden)) {
                failures.add("Golden file is missing: " + golden);
                continue;
            }

            String actualHash = sha256(golden);
            if (!expectedHash.equals(actualHash)) {
                failures.add("SHA-256 mismatch for " + relativePath
                        + " (expected " + expectedHash + ", actual " + actualHash + ")");
            }
        }

        if (!failures.isEmpty()) {
            fail(String.join(System.lineSeparator(), failures));
        }
    }

    @Test
    void testResourceGoldenCopiesMatchAuthoritativeFiles() throws IOException {
        Path root = projectRoot();
        Path copies = root.resolve(TEST_RESOURCE_GOLDEN);
        List<String> failures = new ArrayList<>();

        if (!Files.isDirectory(copies)) {
            fail("Test-resource Golden directory is missing: " + copies);
        }

        for (String relativePath : GOLDEN_FILES) {
            Path original = root.resolve(relativePath);
            Path copy = copies.resolve(Path.of(relativePath).getFileName());

            if (!Files.isRegularFile(original)) {
                failures.add("Authoritative Golden file is missing: " + original);
                continue;
            }
            if (!Files.isRegularFile(copy)) {
                failures.add("Golden copy is missing: " + copy);
                continue;
            }

            String originalHash = sha256(original);
            String copyHash = sha256(copy);
            if (!originalHash.equals(copyHash)) {
                failures.add("Golden copy differs from authoritative file: " + copy);
            }
        }

        if (!failures.isEmpty()) {
            fail(String.join(System.lineSeparator(), failures));
        }
    }

    @Test
    void testClassesTargetJava17() throws IOException {
        try (InputStream input = BootstrapTest.class.getResourceAsStream("BootstrapTest.class")) {
            if (input == null) {
                fail("BootstrapTest.class resource is unavailable");
            }
            byte[] classFile = input.readAllBytes();
            if (classFile.length < 8 || classFile[0] != (byte) 0xCA
                    || classFile[1] != (byte) 0xFE || classFile[2] != (byte) 0xBA
                    || classFile[3] != (byte) 0xBE) {
                fail("BootstrapTest.class is not a valid class file");
            }
            int majorVersion = ((classFile[6] & 0xFF) << 8) | (classFile[7] & 0xFF);
            assertEquals(61, majorVersion, "Test classes must target Java 17 (class file 61)");
        }
    }

    @Test
    void analyzerApisArePresentAndTargetAtMostJava17() throws IOException {
        String[][] analyzers = {
                {"com.github.javaparser.StaticJavaParser", "JavaParser"},
                {"net.sourceforge.pmd.lang.jsp.JspLanguageModule", "PMD JSP"}
        };

        for (String[] analyzer : analyzers) {
            Class<?> type;
            try {
                type = Class.forName(analyzer[0]);
            } catch (ClassNotFoundException exception) {
                fail(analyzer[1] + " API is missing: " + analyzer[0], exception);
                return;
            }

            String resource = analyzer[0].replace('.', '/') + ".class";
            try (InputStream input = type.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    fail(analyzer[1] + " class resource is unavailable: " + resource);
                }
                byte[] classFile = input.readAllBytes();
                if (classFile.length < 8) {
                    fail(analyzer[1] + " class file is truncated: " + resource);
                }
                int majorVersion = ((classFile[6] & 0xFF) << 8) | (classFile[7] & 0xFF);
                if (majorVersion > 61) {
                    fail(analyzer[1] + " class file requires newer than Java 17: " + resource
                            + " (major version " + majorVersion + ")");
                }
            }
        }
    }

    private static Path projectRoot() {
        String basedir = System.getProperty("basedir");
        if (basedir == null || basedir.isBlank()) {
            basedir = System.getProperty("user.dir");
        }
        return Path.of(basedir).toAbsolutePath().normalize();
    }

    private static Map<String, String> readManifest(Path manifestPath) throws IOException {
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
}
