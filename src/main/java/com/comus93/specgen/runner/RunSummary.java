package com.comus93.specgen.runner;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record RunSummary(List<FileResult> results, Path logFile, String logError) {

    public RunSummary(List<FileResult> results) {
        this(results, null, "");
    }

    public RunSummary {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(logError, "logError");
        results = List.copyOf(results);
    }

    public int totalCount() {
        return results.size();
    }

    public int successCount() {
        return count(FileResult.FileStatus.SUCCESS);
    }

    public int warningCount() {
        return count(FileResult.FileStatus.WARNING);
    }

    public int failureCount() {
        return count(FileResult.FileStatus.FAILED);
    }

    public boolean hasFailures() {
        return failureCount() > 0;
    }

    public List<Path> outputFiles() {
        return results.stream()
                .filter(result -> result.status() != FileResult.FileStatus.FAILED)
                .map(FileResult::outputFile)
                .toList();
    }

    private int count(FileResult.FileStatus status) {
        return (int) results.stream().filter(result -> result.status() == status).count();
    }
}
