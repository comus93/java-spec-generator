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
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("modules must not be empty");
        }
    }
}
