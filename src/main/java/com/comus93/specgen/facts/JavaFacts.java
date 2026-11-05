package com.comus93.specgen.facts;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record JavaFacts(
        Path sourceFile,
        String typeKind,
        List<VariableFact> variables,
        List<MethodFact> methods,
        List<CallFact> calls,
        List<TypeRefFact> typeRefs,
        List<ImportFact> imports,
        List<StaticInitFact> staticInitializations) implements SourceFacts {

    public JavaFacts {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(typeKind, "typeKind");
        variables = List.copyOf(variables);
        methods = List.copyOf(methods);
        calls = List.copyOf(calls);
        typeRefs = List.copyOf(typeRefs);
        imports = List.copyOf(imports);
        staticInitializations = List.copyOf(staticInitializations);
    }

    public record VariableFact(
            String kind,
            String name,
            String type,
            String modifiers,
            String lifetime,
            String initializer,
            int line,
            String owner) {

        public VariableFact {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(modifiers, "modifiers");
            Objects.requireNonNull(lifetime, "lifetime");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(owner, "owner");
        }
    }

    public record ParameterFact(String name, String type) {

        public ParameterFact {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    public record MethodFact(
            String name,
            String signature,
            List<ParameterFact> parameters,
            String returnType,
            String visibility,
            int startLine,
            int endLine,
            String javadoc,
            List<StepFact> steps) {

        public MethodFact {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(signature, "signature");
            parameters = List.copyOf(parameters);
            Objects.requireNonNull(returnType, "returnType");
            Objects.requireNonNull(visibility, "visibility");
            Objects.requireNonNull(javadoc, "javadoc");
            steps = List.copyOf(steps);
        }
    }

    public record StepFact(
            int sequence,
            String kind,
            int line,
            String branchPath,
            String sourceSnippet,
            String targetId) {

        public StepFact {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(branchPath, "branchPath");
            Objects.requireNonNull(sourceSnippet, "sourceSnippet");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    public record CallFact(int line, String callerName, String calledName, String targetId) {

        public CallFact {
            Objects.requireNonNull(callerName, "callerName");
            Objects.requireNonNull(calledName, "calledName");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    public record TypeRefFact(int line, String kind, String name, String resolvedClassId) {

        public TypeRefFact {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(name, "name");
        }
    }

    public record ImportFact(String shortName, String importPath, int line) {

        public ImportFact {
            Objects.requireNonNull(shortName, "shortName");
            Objects.requireNonNull(importPath, "importPath");
        }
    }

    public record StaticInitFact(String targetField, String sourceKind, String initializer, int line) {

        public StaticInitFact {
            Objects.requireNonNull(targetField, "targetField");
            Objects.requireNonNull(sourceKind, "sourceKind");
            Objects.requireNonNull(initializer, "initializer");
        }
    }
}
