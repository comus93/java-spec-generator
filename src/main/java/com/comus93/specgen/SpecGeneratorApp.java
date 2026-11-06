package com.comus93.specgen;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.config.JsonJobConfigReader;
import com.comus93.specgen.gui.GuiLauncher;
import com.comus93.specgen.runner.FileResult;
import com.comus93.specgen.runner.JobRunner;
import com.comus93.specgen.runner.ProgressEvent;
import com.comus93.specgen.runner.RunSummary;

public final class SpecGeneratorApp {

    private static final String USAGE =
            "Usage: spec-generator gui | spec-generator generate --config <job.json>";

    private SpecGeneratorApp() {
    }

    public static void main(String[] args) {
        if (args != null && args.length == 1 && "gui".equals(args[0])) {
            GuiLauncher.launch();
            return;
        }
        System.exit(runCli(args, System.out, System.err));
    }

    public static int runCli(String[] args, PrintStream out, PrintStream err) {
        return runCli(args, out, err, new JobRunner());
    }

    public static int runCli(String[] args, PrintStream out, PrintStream err, JobRunner runner) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        Objects.requireNonNull(runner, "runner");

        if (args == null || args.length == 0) {
            err.println(USAGE);
            return 2;
        }
        if ("gui".equals(args[0])) {
            err.println("GUI is only available through the desktop entry point.");
            return 2;
        }
        if (!"generate".equals(args[0])) {
            err.println("Unknown command: " + args[0]);
            err.println(USAGE);
            return 2;
        }
        if (args.length != 3 || !"--config".equals(args[1])) {
            err.println(USAGE);
            return 2;
        }

        Path configPath;
        try {
            configPath = Path.of(args[2]);
        } catch (RuntimeException exception) {
            err.println("Invalid configuration path: " + args[2]);
            return 2;
        }

        JobConfig config;
        try {
            config = new JsonJobConfigReader().read(configPath);
        } catch (IOException exception) {
            err.println("Cannot read job configuration: " + configPath + " (" + concise(exception) + ")");
            return 2;
        } catch (RuntimeException exception) {
            err.println("Invalid job configuration: " + configPath + " (" + concise(exception) + ")");
            return 2;
        }

        RunSummary summary;
        try {
            summary = runner.run(config, event -> printProgress(out, event));
        } catch (RuntimeException exception) {
            err.println("Job failed: " + concise(exception));
            return 2;
        }

        for (FileResult result : summary.results()) {
            if (result.status() == FileResult.FileStatus.FAILED) {
                err.println("FAILED " + result.sourceFile() + ": " + result.message());
            } else if (result.status() == FileResult.FileStatus.WARNING) {
                out.println("WARNING " + result.sourceFile() + ": " + result.message());
            }
        }
        out.println("Processed " + summary.totalCount() + " file(s): "
                + summary.successCount() + " succeeded, "
                + summary.warningCount() + " warning(s), "
                + summary.failureCount() + " failed.");
        return summary.hasFailures() ? 1 : 0;
    }

    private static void printProgress(PrintStream out, ProgressEvent event) {
        out.println("[" + event.completed() + "/" + event.total() + "] "
                + event.status() + " " + event.sourceFile());
    }

    private static String concise(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replaceAll("\\R+", " ").strip();
    }
}
