package com.comus93.specgen.parser;

import java.nio.file.Path;

import com.comus93.specgen.facts.SourceFacts;

public interface SourceAnalyzer<T extends SourceFacts> {

    T analyze(Path sourceFile) throws SourceAnalysisException;
}
