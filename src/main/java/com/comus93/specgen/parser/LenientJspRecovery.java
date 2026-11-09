package com.comus93.specgen.parser;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.comus93.specgen.facts.JspFacts;
import com.comus93.specgen.facts.JspFacts.EventFact;
import com.comus93.specgen.facts.JspFacts.FlowFact;
import com.comus93.specgen.facts.JspFacts.ScreenFieldFact;

/** Extracts isolated lexical evidence, never a reconstructed JSP tree. */
final class LenientJspRecovery {
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_:.-]*");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\s+([A-Za-z_:][A-Za-z0-9_:.-]*)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'))?");
    private static final Pattern CONDITIONAL = Pattern.compile("(?i)</?([a-z_][a-z0-9_.-]*:[a-z_][a-z0-9_.:-]*)\\b[^>]*>");
    private static final Pattern INLINE = Pattern.compile(
            "(?is)<c:if\\b([^<>]*\\btest\\s*=\\s*(?:\"[^\"]*\"|'[^']*')[^<>]*)>(\\s*[A-Za-z_:][A-Za-z0-9_:.-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*')(?:\\s+[A-Za-z_:][A-Za-z0-9_:.-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))*\\s*)</c:if\\s*>");

    private LenientJspRecovery() { }

    static JspFacts recover(Path path, String source, String parseMessage) {
        List<ScreenFieldFact> fields = new ArrayList<>();
        List<FlowFact> flows = new ArrayList<>();
        List<EventFact> events = new ArrayList<>();
        Deque<String> conditionalTags = new ArrayDeque<>();
        int i = 0;
        while (i < source.length()) {
            if (source.startsWith("<!--", i) || source.startsWith("<%--", i)) {
                String end = source.startsWith("<%--", i) ? "--%>" : "-->";
                int close = source.indexOf(end, i + 4);
                if (close < 0) break;
                i = close + end.length();
                continue;
            }
            if (source.startsWith("<%", i) && !source.startsWith("<%@", i)) {
                int close = source.indexOf("%>", i + 2);
                if (close < 0) break;
                i = close + 2;
                continue;
            }
            if (source.startsWith("<%@", i)) {
                int close = source.indexOf("%>", i + 3);
                if (close < 0) break;
                String body = source.substring(i + 3, close).stripLeading();
                Matcher name = NAME.matcher(body);
                if (conditionalTags.isEmpty() && name.lookingAt()) {
                    Attributes attrs = attributes(body.substring(name.end()));
                    if (attrs != null) {
                        flows.add(new FlowFact(flows.size() + 1, line(source, i), "DIRECTIVE",
                                attrs.values.getOrDefault("file", name.group()), attrs.values, 0));
                    }
                }
                i = close + 2;
                continue;
            }
            if (source.charAt(i) == '<') {
                boolean closing = i + 1 < source.length() && source.charAt(i + 1) == '/';
                int start = i + (closing ? 2 : 1);
                Matcher name = NAME.matcher(source);
                name.region(start, source.length());
                if (!name.lookingAt()) { i++; continue; }
                String tag = name.group();
                int end = tagEnd(source, name.end());
                if (end < 0) {
                    if (tag.equalsIgnoreCase("script") || tag.equalsIgnoreCase("style")) break;
                    // A nested '<' makes this opening tag ambiguous. Resume only at the
                    // next line, and only if all conditional tags on the damaged line balance.
                    int next = source.indexOf('\n', i);
                    if (next < 0) break;
                    String damaged = source.substring(i, next);
                    Matcher conditional = CONDITIONAL.matcher(damaged);
                    Deque<String> damagedTags = new ArrayDeque<>();
                    boolean unbalanced = false;
                    while (conditional.find()) {
                        String matched = conditional.group();
                        String conditionalName = conditional.group(1).toLowerCase(Locale.ROOT);
                        if (matched.startsWith("</")) {
                            if (damagedTags.isEmpty() || !damagedTags.peek().equals(conditionalName)) {
                                unbalanced = true;
                                break;
                            }
                            damagedTags.pop();
                        } else if (!matched.substring(0, matched.length() - 1).stripTrailing().endsWith("/")) {
                            damagedTags.push(conditionalName);
                        }
                    }
                    if (unbalanced || !damagedTags.isEmpty()) break;
                    if (conditionalTags.isEmpty()) {
                        Matcher inline = INLINE.matcher(damaged);
                        while (inline.find()) {
                            flows.add(new FlowFact(flows.size() + 1, line(source, i + inline.start()),
                                    "CONDITIONAL_ATTRIBUTE", "c:if", Map.of(
                                            "condition", inline.group(1).trim(),
                                            "attributeFragment", inline.group(2).trim()), 0));
                        }
                    }
                    i = next + 1;
                    continue;
                }
                String lower = tag.toLowerCase(Locale.ROOT);
                if (lower.equals("script") || lower.equals("style")) {
                    if (closing) { i = end + 1; continue; }
                    Matcher close = Pattern.compile("(?i)</" + lower + "\\s*>").matcher(source);
                    if (!close.find(end + 1)) break;
                    i = close.end();
                    continue;
                }
                boolean customTag = lower.contains(":");
                boolean selfClosing = source.substring(name.end(), end).stripTrailing().endsWith("/");
                if (customTag && closing) {
                    if (conditionalTags.isEmpty() || !conditionalTags.peek().equals(lower)) break;
                    conditionalTags.pop();
                    i = end + 1;
                    continue;
                }
                if (lower.startsWith("c:")) {
                    if (!selfClosing) conditionalTags.push(lower);
                    i = end + 1;
                    continue;
                }
                if (!closing && conditionalTags.isEmpty()) {
                    String body = source.substring(name.end(), end);
                    if (selfClosing) body = body.stripTrailing().substring(0, body.stripTrailing().length() - 1);
                    Attributes attrs = attributes(body);
                    if (attrs != null && (lower.equals("input") || lower.equals("select")
                            || lower.equals("textarea") || lower.equals("a") || tag.contains(":"))) {
                        int line = line(source, i);
                        flows.add(new FlowFact(flows.size() + 1, line, "ELEMENT", tag,
                                attrs.values, attrs.raw, 0));
                        addField(fields, tag, lower, attrs, line);
                        if (lower.equals("a") && attrs.values.containsKey("href")) {
                            int close = source.indexOf("</a>", end + 1);
                            if (close >= 0) {
                                String text = source.substring(end + 1, close);
                                if (!text.contains("<") && !text.contains("${") && !text.contains("#{")) {
                                    String label = text.replaceAll("\\s+", " ").trim();
                                    events.add(new EventFact("LINK_CLICK", label, attrs.values.get("href"), null, line));
                                    fields.add(new ScreenFieldFact(label, null, null, null,
                                            attrs.values, attrs.raw, tag, null, line));
                                }
                            }
                        }
                    }
                }
                if (customTag && !selfClosing) conditionalTags.push(lower);
                i = end + 1;
                continue;
            }
            if (conditionalTags.isEmpty() && (source.startsWith("${", i) || source.startsWith("#{", i))) {
                int end = source.indexOf('}', i + 2);
                if (end >= 0 && source.substring(i, end).indexOf('<') < 0
                        && source.substring(i, end).indexOf('\n') < 0) {
                    flows.add(new FlowFact(flows.size() + 1, line(source, i), "EL",
                            source.substring(i, end + 1), Map.of(), 0));
                    i = end + 1;
                    continue;
                }
            }
            i++;
        }
        if (fields.isEmpty() && flows.isEmpty() && events.isEmpty()) return null;
        String diagnostic = "PMD parse error: " + String.valueOf(parseMessage)
                + "\nRecovery method: conservative source-anchored lexical extraction. "
                + "Limitations: isolated complete evidence only; hierarchy, conditional descendants, "
                + "ambiguous markup, script, comments and scriptlets are not reconstructed.";
        return new JspFacts(path, fields, flows, events, List.of(), diagnostic);
    }

    private static int tagEnd(String source, int start) {
        char quote = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '<') return -1;
            else if (c == '>') return i;
        }
        return -1;
    }

    private static Attributes attributes(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> raw = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(text);
        int pos = 0;
        while (pos < text.length()) {
            matcher.region(pos, text.length());
            if (!matcher.lookingAt()) {
                if (text.substring(pos).isBlank()) break;
                return null;
            }
            String name = matcher.group(1);
            if (values.containsKey(name)) return null;
            String value = matcher.group(2);
            if (value != null && value.contains("<%")) return null;
            values.put(name, value == null ? null : value.substring(1, value.length() - 1));
            raw.put(name, matcher.group().stripLeading());
            pos = matcher.end();
        }
        return new Attributes(values, raw);
    }

    private static void addField(List<ScreenFieldFact> fields, String tag, String lower,
            Attributes attrs, int line) {
        boolean plain = lower.equals("input") || lower.equals("select") || lower.equals("textarea");
        boolean form = switch (lower) {
            case "form:input", "form:hidden", "form:textarea", "form:select", "form:checkbox",
                    "form:checkboxes", "form:radiobutton", "form:radiobuttons", "form:password" -> true;
            default -> false;
        };
        if (!plain && !form) {
            if (lower.equals("spring:message")) fields.add(new ScreenFieldFact("<" + tag + ">", null,
                    null, null, attrs.values, attrs.raw, tag, attrs.values.get("code"), line));
            return;
        }
        String name = first(form ? attrs.values.get("path") : attrs.values.get("name"),
                form ? attrs.values.get("name") : attrs.values.get("id"), attrs.values.get("path"));
        String type = attrs.values.get("type");
        if (plain && "submit".equalsIgnoreCase(type)) {
            fields.add(new ScreenFieldFact(attrs.values.get("value"), null, null, null,
                    attrs.values, attrs.raw, tag, null, line));
        } else if (name != null) {
            boolean hidden = lower.equals("form:hidden") || "hidden".equalsIgnoreCase(type);
            fields.add(new ScreenFieldFact(name, null, attrs.values.get("path"), !hidden,
                    attrs.values, attrs.raw, form ? tag : first(type, tag), null, line));
        }
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static int line(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) if (source.charAt(i) == '\n') line++;
        return line;
    }

    private record Attributes(Map<String, String> values, Map<String, String> raw) { }
}
