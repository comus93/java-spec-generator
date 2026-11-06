package com.comus93.specgen.excel;

import java.util.List;
import java.util.Objects;

/** Format-independent workbook consumed by the POI writer. */
public record WorkbookModel(List<SheetModel> sheets) {
    public WorkbookModel {
        Objects.requireNonNull(sheets, "sheets");
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("Workbook requires at least one sheet");
        }
        sheets = List.copyOf(sheets);
    }
}
