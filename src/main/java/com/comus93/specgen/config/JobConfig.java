package com.comus93.specgen.config;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record JobConfig(
        SourceMode mode,
        String system,
        String author,
        LocalDate authoringDateOverride,
        String programName,
        String overview,
        Path outputDirectory,
        OutputFormat outputFormat,
        List<ModuleSelection> modules) {

    public JobConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(programName, "programName");
        Objects.requireNonNull(overview, "overview");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(outputFormat, "outputFormat");
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
        validateSystem(system);
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("modules must not be empty");
        }
    }

    private static void validateSystem(String system) {
        if (system.isBlank() || system.endsWith(".")
                || isSpace(system.codePointBefore(system.length()))
                || system.codePoints().anyMatch(c -> Character.isISOControl(c)
                        || "<>:\"/\\|?*".indexOf(c) >= 0)) {
            throw new IllegalArgumentException("system must be a safe directory name: " + system);
        }
        String device = system.split("\\.", 2)[0].stripTrailing().toUpperCase(java.util.Locale.ROOT);
        if (device.equals("CON") || device.equals("PRN") || device.equals("AUX") || device.equals("NUL")
                || device.equals("CONIN$") || device.equals("CONOUT$")
                || device.matches("(COM|LPT)[1-9¹²³]")) {
            throw new IllegalArgumentException("system must not be a reserved directory name: " + system);
        }
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
