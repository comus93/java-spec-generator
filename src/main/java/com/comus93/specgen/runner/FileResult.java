package com.comus93.specgen.runner;

import java.nio.file.Path;
import java.util.Objects;

public record FileResult(
        Path sourceFile,
        Path outputFile,
        FileStatus status,
        String errorCode,
        String message) {

    public FileResult {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(outputFile, "outputFile");
        Objects.requireNonNull(status, "status");
        errorCode = errorCode == null ? "" : errorCode;
        message = message == null ? "" : message;
    }

    public enum FileStatus {
        SUCCESS,
        WARNING,
        FAILED
    }
}
