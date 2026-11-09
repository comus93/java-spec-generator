package com.comus93.specgen.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.comus93.specgen.parser.SourceAnalysisException;

/**
 * Adds a bounded excerpt from the original source to parser failure diagnostics.
 * This is local-only error reporting; no source code is uploaded or rewritten.
 */
final class ParseSourceExcerpt {

    private static final Pattern LOCATION = Pattern.compile(
            "(?i)\\bline\\s+(\\d+)\\s*,\\s*column\\s+(\\d+)");
    private static final int CONTEXT_LINES = 2;
    private static final int MAX_LINE_CHARS = 220;

    private ParseSourceExcerpt() {
    }

    static String append(Path sourceFile, String errorCode, String errorMessage) {
        if (!SourceAnalysisException.PARSE_ERROR.equals(errorCode)) {
            return errorMessage;
        }
        Matcher matcher = LOCATION.matcher(errorMessage);
        if (!matcher.find()) {
            return errorMessage;
        }
        try {
            int target = Integer.parseInt(matcher.group(1));
            int column = Integer.parseInt(matcher.group(2));
            if (target < 1 || column < 1) {
                return errorMessage;
            }

            int start = Math.max(1, target - CONTEXT_LINES);
            int end = target > Integer.MAX_VALUE - CONTEXT_LINES
                    ? Integer.MAX_VALUE : target + CONTEXT_LINES;
            List<String> excerpt = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
                String line;
                for (int number = 1; number <= end && (line = reader.readLine()) != null; number++) {
                    if (number >= start) {
                        excerpt.add(line);
                    }
                }
            }
            if (excerpt.isEmpty() || target - start >= excerpt.size()) {
                return errorMessage;
            }

            StringBuilder result = new StringBuilder(errorMessage);
            result.append("\nSource excerpt (original ")
                    .append(sourceFile.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jsp")
                            ? "JSP" : "source")
                    .append(") at line ")
                    .append(target).append(", column ").append(column).append(':');
            for (int i = 0; i < excerpt.size(); i++) {
                int number = start + i;
                String original = excerpt.get(i);
                int left = original.length() > MAX_LINE_CHARS
                        ? (number == target
                                ? Math.min(Math.max(0, column - 81), original.length() - MAX_LINE_CHARS)
                                : 0)
                        : 0;
                int right = Math.min(original.length(), left + MAX_LINE_CHARS);
                String prefix = left > 0 ? "..." : "";
                String suffix = right < original.length() ? "..." : "";
                String visible = prefix + printable(original.substring(left, right)) + suffix;
                result.append("\n").append(number == target ? "> " : "  ")
                        .append(String.format("%4d | ", number)).append(visible);
                if (number == target) {
                    int location = Math.max(0, Math.min(column - 1, original.length()));
                    int relative = Math.max(0, Math.min(location - left, right - left));
                    int expanded = printable(original.substring(left, left + relative)).length();
                    result.append("\n       | ")
                            .append(" ".repeat(Math.min(MAX_LINE_CHARS + 3, prefix.length() + expanded)))
                            .append('^');
                }
            }
            return result.toString();
        } catch (IOException | NumberFormatException | SecurityException ignored) {
            // Never let diagnostic formatting replace the original parse failure.
            return errorMessage;
        }
    }

    private static String printable(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\t') {
                builder.append("    ");
            } else if (Character.isISOControl(ch)) {
                builder.append(' ');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
