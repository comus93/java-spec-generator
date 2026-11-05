package com.comus93.specgen.config;

import java.nio.file.Path;
import java.util.Objects;

public record ModuleSelection(Path sourceFolder, String subsystem, String moduleName) {

    public ModuleSelection {
        Objects.requireNonNull(sourceFolder, "sourceFolder");
        Objects.requireNonNull(subsystem, "subsystem");
        Objects.requireNonNull(moduleName, "moduleName");
    }
}
