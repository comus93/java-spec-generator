package com.comus93.specgen.runner;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Best-effort run diagnostics; logging failures never interrupt file processing. */
final class RunLog {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Path file;
    private BufferedWriter writer;
    private String error = "";

    RunLog(Path outputDirectory) {
        LocalDateTime started = LocalDateTime.now();
        try {
            Path directory = outputDirectory.resolve("logs");
            Files.createDirectories(directory);
            String name = "specgen-" + FILE_TIME.format(started);
            for (int suffix = 0; ; suffix++) {
                Path candidate = directory.resolve(name + (suffix == 0 ? "" : "-" + suffix) + ".log");
                try {
                    writer = Files.newBufferedWriter(candidate, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    file = candidate;
                    break;
                } catch (FileAlreadyExistsException ignored) {
                    // Another run started in the same second.
                }
            }
            write("Started: " + started + "\n");
        } catch (IOException | SecurityException exception) {
            recordError(exception);
        }
    }

    void result(FileResult result) {
        write("\nSource: " + result.sourceFile() + "\nStatus: " + result.status()
                + "\nOutput: " + result.outputFile() + "\nError code: " + result.errorCode()
                + "\nMessage: " + result.message() + "\n");
    }

    void finish(RunSummary summary) {
        write("\nSummary: total=" + summary.totalCount() + ", success=" + summary.successCount()
                + ", warning=" + summary.warningCount() + ", failed=" + summary.failureCount() + "\n");
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException | SecurityException exception) {
                recordError(exception);
            }
            writer = null;
        }
    }

    Path file() {
        return file;
    }

    String error() {
        return error;
    }

    private void write(String text) {
        if (writer == null || !error.isEmpty()) {
            return;
        }
        try {
            writer.write(text);
            writer.flush();
        } catch (IOException | SecurityException exception) {
            recordError(exception);
        }
    }

    private void recordError(Exception exception) {
        String detail = exception.getMessage();
        String message = exception.getClass().getSimpleName()
                + (detail == null || detail.isBlank() ? "" : ": " + detail);
        error = error.isEmpty() ? message : error + "; " + message;
    }
}
