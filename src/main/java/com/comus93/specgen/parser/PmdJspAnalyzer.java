package com.comus93.specgen.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.comus93.specgen.facts.JspFacts;
import com.comus93.specgen.facts.JspFacts.ConstantFact;
import com.comus93.specgen.facts.JspFacts.EventFact;
import com.comus93.specgen.facts.JspFacts.FlowFact;
import com.comus93.specgen.facts.JspFacts.ScreenFieldFact;

import net.sourceforge.pmd.lang.LanguageProcessorRegistry;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.ast.Parser;
import net.sourceforge.pmd.lang.ast.SemanticErrorReporter;
import net.sourceforge.pmd.lang.document.TextDocument;
import net.sourceforge.pmd.lang.document.TextFile;
import net.sourceforge.pmd.lang.jsp.JspLanguageModule;
import net.sourceforge.pmd.lang.jsp.ast.ASTAttribute;
import net.sourceforge.pmd.lang.jsp.ast.ASTAttributeValue;
import net.sourceforge.pmd.lang.jsp.ast.ASTElExpression;
import net.sourceforge.pmd.lang.jsp.ast.ASTElement;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspDirective;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspDirectiveAttribute;
import net.sourceforge.pmd.lang.jsp.ast.ASTText;
import net.sourceforge.pmd.lang.jsp.ast.JspVisitorBase;
import net.sourceforge.pmd.util.log.PmdReporter;

/**
 * Static JSP inspection only. No JavaScript execution, controller resolution, or link inference.
 * Each analyze call owns its parser resources; a parse failure is isolated to its source file.
 */
public final class PmdJspAnalyzer implements SourceAnalyzer<JspFacts> {

    @Override
    public JspFacts analyze(Path sourceFile) throws SourceAnalysisException {
        Objects.requireNonNull(sourceFile, "sourceFile");

        JspLanguageModule language = JspLanguageModule.getInstance();
        try (LanguageProcessorRegistry registry = LanguageProcessorRegistry.create(
                LanguageRegistry.singleton(language),
                Map.of(language, language.newPropertyBundle()), PmdReporter.quiet());
             TextDocument document = TextDocument.create(
                     TextFile.forPath(sourceFile, StandardCharsets.UTF_8, language.getDefaultVersion()))) {

            Parser.ParserTask task = new Parser.ParserTask(
                    document, SemanticErrorReporter.noop(), registry);
            Node root = registry.getProcessor(language).services().getParser().parse(task);
            Collector collector = new Collector();
            root.acceptVisitor(collector, 0);
            return new JspFacts(sourceFile, collector.fields, collector.flows,
                    collector.events, List.<ConstantFact>of());
        } catch (IOException error) {
            throw new SourceAnalysisException(sourceFile,
                    SourceAnalysisException.READ_ERROR, "", error);
        } catch (RuntimeException error) {
            throw new SourceAnalysisException(sourceFile,
                    SourceAnalysisException.PARSE_ERROR, error.getMessage(), error);
        }
    }

    /** Visits PMD's actual JSP syntax tree in source order. */
    private static final class Collector extends JspVisitorBase<Integer, Void> {
        private final List<ScreenFieldFact> fields = new ArrayList<>();
        private final List<FlowFact> flows = new ArrayList<>();
        private final List<EventFact> events = new ArrayList<>();
        private final Deque<TableState> tables = new ArrayDeque<>();
        private final Map<String, String> formLabels = new LinkedHashMap<>();
        private final Set<ASTElement> loopsWithElField =
                Collections.newSetFromMap(new IdentityHashMap<>());

        @Override
        public Void visitNode(Node node, Integer depth) {
            int nextDepth = depth;
            if (node instanceof ASTElement element) {
                String tag = element.getName();
                Map<String, String> attributes = elementAttributes(element);
                if ("table".equalsIgnoreCase(tag)) {
                    tables.push(new TableState());
                }
                if ("th".equalsIgnoreCase(tag) && !tables.isEmpty()) {
                    String label = visibleText(element);
                    if (!label.isBlank()) {
                        tables.peek().headers.add(label);
                    }
                }
                if ("tr".equalsIgnoreCase(tag) && !tables.isEmpty()) {
                    tables.peek().cellIndex = 0;
                }

                flows.add(new FlowFact(flows.size() + 1, element.getBeginLine(),
                        "ELEMENT", tag, attributes, depth));

                if ("td".equalsIgnoreCase(tag) && !tables.isEmpty()) {
                    TableState table = tables.peek();
                    int column = table.cellIndex++;
                    String header = column < table.headers.size() ? table.headers.get(column) : null;
                    collectTableCell(element, header);
                } else {
                    collectField(element, tag, attributes);
                }
                if ("a".equalsIgnoreCase(tag) && attributes.containsKey("href")) {
                    events.add(new EventFact("LINK_CLICK", visibleText(element),
                            attributes.get("href"), null, element.getBeginLine()));
                }
                nextDepth = depth + 1;
            } else if (node instanceof ASTJspDirective directive) {
                Map<String, String> attributes = directiveAttributes(directive);
                flows.add(new FlowFact(flows.size() + 1, directive.getBeginLine(),
                        "DIRECTIVE", attributes.getOrDefault("file", directive.getName()),
                        attributes, depth));
            } else if (node instanceof ASTElExpression expression) {
                if (expression.getParent() instanceof ASTText) {
                    flows.add(new FlowFact(flows.size() + 1, expression.getBeginLine(),
                            "EL", rawSource(expression), Map.of(), depth));
                }
            }
            for (int i = 0; i < node.getNumChildren(); i++) {
                node.getChild(i).acceptVisitor(this, nextDepth);
            }
            if (node instanceof ASTElement element && "table".equalsIgnoreCase(element.getName())) {
                tables.pop();
            }
            return null;
        }

        private void collectTableCell(ASTElement cell, String header) {
            ASTElement loop = ancestorElement(cell, "c:forEach");
            Map<String, String> loopAttributes = loop == null
                    ? Map.of() : elementAttributes(loop);
            String loopVar = loop == null ? null : loopAttributes.get("var");

            List<ASTElExpression> expressions = new ArrayList<>();
            List<ASTElement> formatTags = new ArrayList<>();
            List<ASTElement> anchors = new ArrayList<>();
            collectCellNodes(cell, expressions, formatTags, anchors);

            if (!formatTags.isEmpty()) {
                for (ASTElement format : formatTags) {
                    Map<String, String> attributes = elementAttributes(format);
                    fields.add(new ScreenFieldFact(header, loopVar, attributes.get("value"),
                            null, attributes, format.getName(), null, format.getBeginLine()));
                }
            } else {
                for (ASTElExpression expression : expressions) {
                    Map<String, String> attributes = loop != null && loopsWithElField.add(loop)
                            ? loopAttributes : Map.of();
                    fields.add(new ScreenFieldFact(header, loopVar, rawSource(expression),
                            null, attributes, "EL", null, expression.getBeginLine()));
                }
            }
            for (ASTElement anchor : anchors) {
                addLinkField(anchor);
            }
        }

        private static void collectCellNodes(Node node, List<ASTElExpression> expressions,
                List<ASTElement> formatTags, List<ASTElement> anchors) {
            if (node instanceof ASTElExpression expression && expression.getParent() instanceof ASTText) {
                expressions.add(expression);
            } else if (node instanceof ASTElement element) {
                if ("fmt:formatDate".equalsIgnoreCase(element.getName())) {
                    formatTags.add(element);
                } else if ("a".equalsIgnoreCase(element.getName())
                        && elementAttributes(element).containsKey("href")) {
                    anchors.add(element);
                }
            }
            for (int i = 0; i < node.getNumChildren(); i++) {
                collectCellNodes(node.getChild(i), expressions, formatTags, anchors);
            }
        }

        private void collectField(ASTElement element, String tag, Map<String, String> attrs) {
            String normalized = tag.toLowerCase(Locale.ROOT);
            if ("spring:message".equalsIgnoreCase(tag)) {
                fields.add(new ScreenFieldFact("<" + tag + ">", null, null, null,
                        attrs, tag, attrs.get("code"), element.getBeginLine()));
                return;
            }
            if ("form:label".equalsIgnoreCase(tag)) {
                String path = firstNonBlank(attrs.get("path"), attrs.get("name"));
                if (path != null) {
                    formLabels.put(path, visibleText(element));
                }
                return;
            }
            if ("a".equalsIgnoreCase(tag)) {
                if (ancestorElement(element, "td") == null && attrs.containsKey("href")) {
                    addLinkField(element);
                }
                return;
            }
            if ("input".equalsIgnoreCase(tag)) {
                if ("submit".equalsIgnoreCase(attrs.get("type"))) {
                    fields.add(new ScreenFieldFact(attrs.get("value"), ancestorFormObject(element),
                            null, null, attrs, tag, null, element.getBeginLine()));
                }
                return;
            }

            boolean formField = switch (normalized) {
                case "form:input", "form:hidden", "form:textarea", "form:select",
                     "form:checkbox", "form:checkboxes", "form:radiobutton", "form:radiobuttons",
                     "form:password" -> true;
                default -> false;
            };
            if (!formField) {
                return;
            }
            String path = firstNonBlank(attrs.get("path"), attrs.get("name"));
            if (path == null) {
                return;
            }
            boolean hidden = normalized.equals("form:hidden");
            String fieldName = hidden ? path : firstNonBlank(formLabels.get(path), path);
            fields.add(new ScreenFieldFact(fieldName, ancestorFormObject(element), path,
                    hidden ? Boolean.FALSE : Boolean.TRUE, attrs, tag, null, element.getBeginLine()));
        }

        private void addLinkField(ASTElement anchor) {
            Map<String, String> attributes = elementAttributes(anchor);
            ASTElement loop = ancestorElement(anchor, "c:forEach");
            String loopVar = loop == null ? null : elementAttributes(loop).get("var");
            fields.add(new ScreenFieldFact(visibleText(anchor), loopVar,
                    firstEl(attributes.get("href")), null, attributes,
                    anchor.getName(), null, anchor.getBeginLine()));
        }

        private static String firstEl(String value) {
            if (value == null) {
                return null;
            }
            int start = value.indexOf("${");
            int end = start < 0 ? -1 : value.indexOf('}', start + 2);
            return end < 0 ? null : value.substring(start, end + 1);
        }

        private static String ancestorFormObject(Node node) {
            ASTElement form = ancestorElement(node, "form:form");
            if (form == null) {
                return null;
            }
            Map<String, String> attrs = elementAttributes(form);
            return firstNonBlank(attrs.get("modelAttribute"), attrs.get("commandName"));
        }

        private static ASTElement ancestorElement(Node node, String tag) {
            Node parent = node.getParent();
            while (parent != null) {
                if (parent instanceof ASTElement element && tag.equalsIgnoreCase(element.getName())) {
                    return element;
                }
                parent = parent.getParent();
            }
            return null;
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        private static Map<String, String> elementAttributes(ASTElement element) {
            Map<String, String> result = new LinkedHashMap<>();
            for (int i = 0; i < element.getNumChildren(); i++) {
                Node child = element.getChild(i);
                if (child instanceof ASTAttribute attr) {
                    String value = null;
                    for (int j = 0; j < attr.getNumChildren(); j++) {
                        Node part = attr.getChild(j);
                        if (part instanceof ASTAttributeValue attrValue) {
                            value = attrValue.getValue();
                            if (value == null) {
                                value = rawSource(attrValue);
                            }
                        }
                    }
                    result.put(attr.getName(), unquote(value));
                }
            }
            return result;
        }

        private static Map<String, String> directiveAttributes(ASTJspDirective directive) {
            Map<String, String> result = new LinkedHashMap<>();
            for (int i = 0; i < directive.getNumChildren(); i++) {
                Node child = directive.getChild(i);
                if (child instanceof ASTJspDirectiveAttribute attribute) {
                    result.put(attribute.getName(), unquote(attribute.getValue()));
                }
            }
            return result;
        }

        private static String unquote(String value) {
            if (value != null && value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    return value.substring(1, value.length() - 1);
                }
            }
            return value;
        }

        private static String rawSource(Node node) {
            return node.getTextDocument().sliceOriginalText(node.getTextRegion()).toString();
        }

        private static String visibleText(ASTElement element) {
            StringBuilder label = new StringBuilder();
            appendText(element, label);
            return label.toString().replaceAll("\\s+", " ").trim();
        }

        private static void appendText(Node node, StringBuilder label) {
            if (node instanceof ASTAttribute) {
                return; // href and other attribute EL are not visible link labels
            }
            if (node instanceof ASTText text) {
                label.append(text.getContent());
            } else if (node instanceof ASTElExpression expression) {
                label.append(rawSource(expression));
            }
            for (int i = 0; i < node.getNumChildren(); i++) {
                appendText(node.getChild(i), label);
            }
        }

        private static final class TableState {
            private final List<String> headers = new ArrayList<>();
            private int cellIndex;
        }
    }
}
