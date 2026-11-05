package com.comus93.specgen.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonJobConfigReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Set<String> JOB_KEYS = Set.of(
            "mode",
            "system",
            "author",
            "authoringDateOverride",
            "programName",
            "overview",
            "outputDirectory",
            "outputFormat",
            "modules");

    private static final Set<String> MODULE_KEYS = Set.of(
            "sourceFolder",
            "subsystem",
            "moduleName");

    public JobConfig read(Path json) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(json, StandardCharsets.UTF_8));
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Job configuration root must be a JSON object: " + json);
        }
        rejectUnknownKeys(root, JOB_KEYS, "job");

        SourceMode mode = parseMode(requiredText(root, "mode", "job"));
        String system = requiredText(root, "system", "job");
        String author = optionalText(root, "author", "job");
        LocalDate authoringDateOverride = parseDate(optionalNullableText(root, "authoringDateOverride", "job"));
        String programName = optionalText(root, "programName", "job");
        String overview = optionalText(root, "overview", "job");
        Path outputDirectory = Path.of(requiredText(root, "outputDirectory", "job"));
        OutputFormat outputFormat = parseOutputFormat(optionalNullableText(root, "outputFormat", "job"));

        JsonNode modulesNode = root.get("modules");
        if (modulesNode == null || modulesNode.isNull()) {
            throw new IllegalArgumentException("Missing required JSON key: job.modules");
        }
        if (!modulesNode.isArray() || modulesNode.isEmpty()) {
            throw new IllegalArgumentException("job.modules must be a non-empty array");
        }

        List<ModuleSelection> modules = new ArrayList<>();
        for (int index = 0; index < modulesNode.size(); index++) {
            JsonNode moduleNode = modulesNode.get(index);
            String context = "job.modules[" + index + "]";
            if (moduleNode == null || !moduleNode.isObject()) {
                throw new IllegalArgumentException(context + " must be a JSON object");
            }
            rejectUnknownKeys(moduleNode, MODULE_KEYS, context);

            Path sourceFolder = Path.of(requiredText(moduleNode, "sourceFolder", context));
            String subsystem = optionalNullableText(moduleNode, "subsystem", context);
            if (subsystem == null || subsystem.isBlank()) {
                subsystem = system;
            }
            String moduleName = requiredText(moduleNode, "moduleName", context);
            modules.add(new ModuleSelection(sourceFolder, subsystem, moduleName));
        }

        return new JobConfig(mode, system, author, authoringDateOverride, programName, overview,
                outputDirectory, outputFormat, modules);
    }

    private static SourceMode parseMode(String value) {
        return switch (value) {
            case "JAVA" -> SourceMode.JAVA;
            case "JSP" -> SourceMode.JSP;
            default -> throw new IllegalArgumentException("job.mode must be JAVA or JSP: " + value);
        };
    }

    private static OutputFormat parseOutputFormat(String value) {
        if (value == null) {
            return OutputFormat.XLS;
        }
        return switch (value) {
            case "xls" -> OutputFormat.XLS;
            case "xlsx" -> OutputFormat.XLSX;
            default -> throw new IllegalArgumentException("job.outputFormat must be xls or xlsx: " + value);
        };
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "job.authoringDateOverride must use yyyy-MM-dd: " + value, exception);
        }
    }

    private static String requiredText(JsonNode object, String key, String context) {
        JsonNode value = object.get(key);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing required JSON key: " + context + "." + key);
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(context + "." + key + " must be a string");
        }
        String text = value.textValue();
        if (text.isBlank()) {
            throw new IllegalArgumentException(context + "." + key + " must not be blank");
        }
        return text;
    }

    private static String optionalText(JsonNode object, String key, String context) {
        String value = optionalNullableText(object, key, context);
        return value == null ? "" : value;
    }

    private static String optionalNullableText(JsonNode object, String key, String context) {
        JsonNode value = object.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(context + "." + key + " must be a string or null");
        }
        return value.textValue();
    }

    private static void rejectUnknownKeys(JsonNode object, Set<String> allowed, String context) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("Unknown JSON key in " + context + ": " + name);
            }
        }
    }
}
