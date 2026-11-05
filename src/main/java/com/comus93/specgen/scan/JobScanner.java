package com.comus93.specgen.scan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.config.ModuleSelection;
import com.comus93.specgen.config.OutputFormat;
import com.comus93.specgen.config.SourceMode;

public final class JobScanner {

    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("windows");

    public List<SourceJob> plan(JobConfig config) {
        Objects.requireNonNull(config, "config");
        Path outputRoot = config.outputDirectory().toAbsolutePath().normalize();
        List<ModuleRoot> modules = uniqueModules(config.modules());
        Path realOutputRoot = realPathAllowingMissing(outputRoot);

        for (ModuleRoot module : modules) {
            if (isWithin(realOutputRoot, module.realRoot())) {
                throw new IllegalArgumentException(
                        "Output directory must not be inside a source folder: " + config.outputDirectory());
            }
        }

        List<SourceJob> jobs = new ArrayList<>();
        for (ModuleRoot module : modules) {
            List<SourceJob> moduleJobs = scanModule(module, config.mode(), config.outputFormat(), outputRoot,
                    realOutputRoot);
            moduleJobs.sort(Comparator.comparing(job -> job.relativePath().toString()));
            jobs.addAll(moduleJobs);
        }
        return List.copyOf(jobs);
    }

    private static List<ModuleRoot> uniqueModules(List<ModuleSelection> selections) {
        Set<Path> seen = new HashSet<>();
        List<ModuleRoot> modules = new ArrayList<>();
        for (ModuleSelection selection : selections) {
            Path normalized = selection.sourceFolder().toAbsolutePath().normalize();
            if (!seen.add(normalized)) {
                continue;
            }
            modules.add(new ModuleRoot(selection, realDirectory(normalized)));
        }
        return modules;
    }

    private static List<SourceJob> scanModule(ModuleRoot module, SourceMode mode, OutputFormat format,
            Path outputRoot, Path realOutputRoot) {
        List<SourceJob> jobs = new ArrayList<>();
        Path root = module.realRoot();
        Path moduleFolderName = root.getFileName();
        Path outputModuleRoot = moduleFolderName == null ? outputRoot : outputRoot.resolve(moduleFolderName);
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(root) && isLinkOrReparse(attributes)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (isLinkOrReparse(attributes) || !attributes.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String fileName = file.getFileName().toString();
                    if (!matchesMode(fileName, mode)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path realFile;
                    try {
                        realFile = file.toRealPath();
                    } catch (IOException exception) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!isWithin(realFile, root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relativePath = root.relativize(file);
                    Path outputFile = outputModuleRoot
                            .resolve(relativePath)
                            .resolveSibling(excelFileName(fileName, format));
                    validateOutputFile(outputFile, realOutputRoot);
                    jobs.add(new SourceJob(module.selection(), file, relativePath, outputFile));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to scan source folder: " + module.selection().sourceFolder(),
                    exception);
        }
        return jobs;
    }

    private static void validateOutputFile(Path outputFile, Path realOutputRoot) {
        Path parent = outputFile.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Output file has no parent directory: " + outputFile);
        }
        Path realParent = realPathAllowingMissing(parent);
        if (!isWithin(realParent, realOutputRoot)) {
            throw new IllegalArgumentException("Output path escapes output directory: " + outputFile);
        }
        if (isExistingReparsePoint(outputFile)) {
            throw new IllegalArgumentException("Output file is a link or reparse point: " + outputFile);
        }
    }

    private static boolean isExistingReparsePoint(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            return isLinkOrReparse(attributes);
        } catch (NoSuchFileException exception) {
            return false;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Output path is not accessible: " + path, exception);
        }
    }

    private static boolean isLinkOrReparse(BasicFileAttributes attributes) {
        return attributes.isSymbolicLink() || attributes.isOther();
    }

    private static boolean matchesMode(String fileName, SourceMode mode) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.lastIndexOf('.') <= 0) {
            return false;
        }
        return switch (mode) {
            case JAVA -> lower.endsWith(".java");
            case JSP -> lower.endsWith(".jsp");
        };
    }

    private static String excelFileName(String sourceName, OutputFormat format) {
        String baseName = sourceName.substring(0, sourceName.lastIndexOf('.'));
        return baseName + (format == OutputFormat.XLSX ? ".xlsx" : ".xls");
    }

    private static Path realDirectory(Path path) {
        try {
            Path real = path.toRealPath();
            if (!Files.isDirectory(real)) {
                throw new IllegalArgumentException("Source folder is not a directory: " + path);
            }
            return real;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Source folder is not accessible: " + path, exception);
        }
    }

    private static Path realPathAllowingMissing(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        Deque<Path> missing = new ArrayDeque<>();
        while (existing != null && !Files.exists(existing)) {
            Path name = existing.getFileName();
            if (name == null) {
                return absolute;
            }
            missing.addFirst(name);
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        try {
            Path real = existing.toRealPath();
            for (Path name : missing) {
                real = real.resolve(name);
            }
            return real.normalize();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Output directory is not accessible: " + path, exception);
        }
    }

    private static boolean isWithin(Path candidate, Path root) {
        Path current = candidate.toAbsolutePath().normalize();
        Path target = root.toAbsolutePath().normalize();
        while (current != null) {
            if (pathEquals(current, target)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean pathEquals(Path left, Path right) {
        if (left.equals(right)) {
            return true;
        }
        return WINDOWS && left.toString().equalsIgnoreCase(right.toString());
    }

    private record ModuleRoot(ModuleSelection selection, Path realRoot) {
    }
}
