package com.comus93.specgen.runner;

import java.nio.file.Path;
import java.util.Objects;

public record ProgressEvent(
        int completed,
        int total,
        Path sourceFile,
        FileResult.FileStatus status) {

    public ProgressEvent {
        if (completed < 0 || total < 0 || completed > total) {
            throw new IllegalArgumentException("Invalid progress counters: " + completed + "/" + total);
        }
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(status, "status");
    }
}
