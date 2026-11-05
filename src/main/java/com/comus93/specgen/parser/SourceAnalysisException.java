package com.comus93.specgen.parser;

import java.nio.file.Path;
import java.util.Objects;

public class SourceAnalysisException extends Exception {

    public static final String READ_ERROR = "READ_ERROR";
    public static final String PARSE_ERROR = "PARSE_ERROR";

    private final Path sourceFile;
    private final String errorCode;
    private final String location;

    public SourceAnalysisException(Path sourceFile, String errorCode, String location) {
        this(sourceFile, errorCode, location, null);
    }

    public SourceAnalysisException(Path sourceFile, String errorCode, String location, Throwable cause) {
        super(message(sourceFile, errorCode, location), cause);
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.location = location == null ? "" : location;
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public String errorCode() {
        return errorCode;
    }

    public String location() {
        return location;
    }

    private static String message(Path sourceFile, String errorCode, String location) {
        String suffix = location == null || location.isBlank() ? "" : " at " + location;
        return "Failed to analyze " + sourceFile + " [" + errorCode + "]" + suffix;
    }
}
