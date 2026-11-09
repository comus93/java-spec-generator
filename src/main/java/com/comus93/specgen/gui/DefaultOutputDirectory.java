package com.comus93.specgen.gui;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Locale;
import java.util.function.Supplier;

final class DefaultOutputDirectory {
    private DefaultOutputDirectory() {
    }

    static Path resolve() {
        URL location = null;
        try {
            CodeSource source = SpecGeneratorFrame.class.getProtectionDomain().getCodeSource();
            location = source == null ? null : source.getLocation();
        } catch (RuntimeException ex) {
            // An inaccessible code source uses the working directory.
        }
        return resolve(location, () -> System.getProperty("user.dir"));
    }

    static Path resolve(URL location, Supplier<String> workingDirectory) {
        Path fallback;
        try {
            fallback = Path.of(workingDirectory.get()).resolve("output");
        } catch (RuntimeException ex) {
            fallback = Path.of("output");
        }
        return resolveLocation(location, fallback);
    }

    static Path resolve(URL location, Path workingDirectory) {
        return resolveLocation(location, workingDirectory.resolve("output"));
    }

    private static Path resolveLocation(URL location, Path fallback) {
        try {
            if (location != null) {
                Path archive = Path.of(location.toURI()).toAbsolutePath();
                if (Files.isRegularFile(archive) && archive.getParent() != null
                        && archive.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    return archive.getParent().resolve("output");
                }
            }
        } catch (URISyntaxException | RuntimeException ex) {
            // An unavailable or non-file code source uses the working directory.
        }
        return fallback;
    }
}
