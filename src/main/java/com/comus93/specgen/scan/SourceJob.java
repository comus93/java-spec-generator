package com.comus93.specgen.scan;

import java.nio.file.Path;
import java.util.Objects;

import com.comus93.specgen.config.ModuleSelection;

public record SourceJob(ModuleSelection module, Path sourceFile, Path relativePath, Path outputFile) {

    public SourceJob {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(outputFile, "outputFile");
    }
}
