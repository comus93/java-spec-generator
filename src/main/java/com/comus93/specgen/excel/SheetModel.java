package com.comus93.specgen.excel;

import java.util.List;
import java.util.Objects;

/** A worksheet and its rows in their final, human-facing order. */
public record SheetModel(String name, List<SheetRow> rows) {
    public SheetModel {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Sheet name is required");
        }
        Objects.requireNonNull(rows, "rows");
        rows = List.copyOf(rows);
    }
}
