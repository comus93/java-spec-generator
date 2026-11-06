package com.comus93.specgen.facts;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Source-local, static JSP facts. Unknown facts are represented by null, not fabricated values. */
public record JspFacts(
        Path sourceFile,
        List<ScreenFieldFact> screenFields,
        List<FlowFact> flows,
        List<EventFact> events,
        List<ConstantFact> constants) implements SourceFacts {

    public JspFacts {
        Objects.requireNonNull(sourceFile, "sourceFile");
        screenFields = List.copyOf(screenFields);
        flows = List.copyOf(flows);
        events = List.copyOf(events);
        constants = List.copyOf(constants);
    }

    public record ScreenFieldFact(
            String fieldName,
            String object,
            String referenceField,
            Boolean editable,
            Map<String, String> rawAttributes,
            Map<String, String> rawAttributeText,
            String fieldType,
            String i18nId,
            int line) {

        public ScreenFieldFact {
            rawAttributes = immutableAttributes(rawAttributes);
            rawAttributeText = immutableAttributes(rawAttributeText);
        }

        public ScreenFieldFact(String fieldName, String object, String referenceField, Boolean editable,
                Map<String, String> rawAttributes, String fieldType, String i18nId, int line) {
            this(fieldName, object, referenceField, editable, rawAttributes, Map.of(), fieldType, i18nId, line);
        }
    }

    public record FlowFact(
            int sequence,
            int line,
            String nodeKind,
            String tagOrFile,
            Map<String, String> attributes,
            Map<String, String> rawAttributeText,
            int depth) {

        public FlowFact {
            attributes = immutableAttributes(attributes);
            rawAttributeText = immutableAttributes(rawAttributeText);
        }

        public FlowFact(int sequence, int line, String nodeKind, String tagOrFile,
                Map<String, String> attributes, int depth) {
            this(sequence, line, nodeKind, tagOrFile, attributes, Map.of(), depth);
        }
    }

    public record EventFact(String type, String name, String description, String returnValue, int line) {
    }

    public record ConstantFact(String name, String type, String description, String note) {
    }

    private static Map<String, String> immutableAttributes(Map<String, String> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values)));
    }
}
