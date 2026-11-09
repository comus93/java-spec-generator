package com.comus93.specgen.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.config.SourceMode;
import com.comus93.specgen.excel.PoiWorkbookWriter;
import com.comus93.specgen.excel.WorkbookModel;
import com.comus93.specgen.facts.JavaFacts;
import com.comus93.specgen.facts.JspFacts;
import com.comus93.specgen.mapper.JavaSpecMapper;
import com.comus93.specgen.mapper.JspSpecMapper;
import com.comus93.specgen.parser.JavaParserAnalyzer;
import com.comus93.specgen.parser.PmdJspAnalyzer;
import com.comus93.specgen.parser.SourceAnalysisException;
import com.comus93.specgen.parser.SourceAnalyzer;
import com.comus93.specgen.scan.JobScanner;
import com.comus93.specgen.scan.SourceJob;

public final class JobRunner {

    public static final String DATE_METADATA_ERROR = "DATE_METADATA_ERROR";
    public static final String WRITE_ERROR = "WRITE_ERROR";
    public static final String PROCESSING_ERROR = "PROCESSING_ERROR";

    private final JobScanner scanner;
    private final SourceAnalyzer<JavaFacts> javaAnalyzer;
    private final SourceAnalyzer<JspFacts> jspAnalyzer;
    private final JavaSpecMapper javaMapper;
    private final JspSpecMapper jspMapper;
    private final PoiWorkbookWriter writer;

    public JobRunner() {
        this(new JobScanner(), new JavaParserAnalyzer(), new PmdJspAnalyzer(),
                new JavaSpecMapper(), new JspSpecMapper(), new PoiWorkbookWriter());
    }

    public JobRunner(
            JobScanner scanner,
            SourceAnalyzer<JavaFacts> javaAnalyzer,
            SourceAnalyzer<JspFacts> jspAnalyzer,
            JavaSpecMapper javaMapper,
            JspSpecMapper jspMapper,
            PoiWorkbookWriter writer) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.javaAnalyzer = Objects.requireNonNull(javaAnalyzer, "javaAnalyzer");
        this.jspAnalyzer = Objects.requireNonNull(jspAnalyzer, "jspAnalyzer");
        this.javaMapper = Objects.requireNonNull(javaMapper, "javaMapper");
        this.jspMapper = Objects.requireNonNull(jspMapper, "jspMapper");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public RunSummary run(JobConfig config, Consumer<ProgressEvent> progress) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(progress, "progress");

        List<SourceJob> jobs = scanner.plan(config);
        List<FileResult> results = new ArrayList<>(jobs.size());
        RunLog log = new RunLog(config.outputDirectory());
        int total = jobs.size();
        int completed = 0;
        for (SourceJob job : jobs) {
            FileResult result = process(config, job);
            results.add(result);
            log.result(result);
            completed++;
            progress.accept(new ProgressEvent(completed, total, job.sourceFile(), result.status()));
        }
        log.finish(new RunSummary(results));
        return new RunSummary(results, log.file(), log.error());
    }

    private FileResult process(JobConfig config, SourceJob job) {
        try {
            WorkbookModel model;
            boolean dateMetadataUnreadable;
            String recoveryDiagnostic = null;
            if (config.mode() == SourceMode.JAVA) {
                JavaFacts facts = javaAnalyzer.analyze(job.sourceFile());
                dateMetadataUnreadable = isDateMetadataUnreadable(config, job);
                model = javaMapper.map(facts, config, job);
            } else {
                JspFacts facts = jspAnalyzer.analyze(job.sourceFile());
                recoveryDiagnostic = facts.recoveryDiagnostic();
                dateMetadataUnreadable = isDateMetadataUnreadable(config, job);
                model = jspMapper.map(facts, config, job);
            }
            writer.write(model, job.outputFile(), config.outputFormat(), config.outputDirectory());
            if (recoveryDiagnostic != null) {
                String message = ParseSourceExcerpt.append(job.sourceFile(),
                        SourceAnalysisException.PARSE_ERROR, recoveryDiagnostic);
                if (dateMetadataUnreadable) {
                    message += "\nSource modification time is unavailable.";
                }
                return new FileResult(job.sourceFile(), job.outputFile(),
                        FileResult.FileStatus.WARNING, SourceAnalysisException.PARSE_ERROR, message);
            }
            if (dateMetadataUnreadable) {
                return new FileResult(job.sourceFile(), job.outputFile(),
                        FileResult.FileStatus.WARNING, DATE_METADATA_ERROR,
                        "Source modification time is unavailable.");
            }
            return new FileResult(job.sourceFile(), job.outputFile(),
                    FileResult.FileStatus.SUCCESS, "", "");
        } catch (SourceAnalysisException exception) {
            return failed(job, exception.errorCode(), ParseSourceExcerpt.append(
                    job.sourceFile(), exception.errorCode(), exception.getMessage()));
        } catch (IOException exception) {
            return failed(job, WRITE_ERROR, concise(exception));
        } catch (RuntimeException exception) {
            return failed(job, PROCESSING_ERROR, concise(exception));
        }
    }

    private static boolean isDateMetadataUnreadable(JobConfig config, SourceJob job) {
        if (config.authoringDateOverride() != null) {
            return false;
        }
        try {
            Files.getLastModifiedTime(job.sourceFile());
            return false;
        } catch (IOException | SecurityException exception) {
            return true;
        }
    }

    private static FileResult failed(SourceJob job, String errorCode, String message) {
        return new FileResult(job.sourceFile(), job.outputFile(),
                FileResult.FileStatus.FAILED, errorCode, message);
    }

    private static String concise(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String singleLine = message.replaceAll("\\R+", " ").strip();
        return singleLine.length() <= 240 ? singleLine : singleLine.substring(0, 240) + "...";
    }
}
