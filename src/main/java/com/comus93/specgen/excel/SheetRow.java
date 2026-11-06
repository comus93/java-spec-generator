package com.comus93.specgen.excel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Ordered cells. Null means a blank cell and is intentionally preserved. */
public record SheetRow(RowKind role, List<Object> values) {
    public SheetRow {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(values, "values");
        for (Object value : values) {
            if (value != null && !(value instanceof String) && !(value instanceof Integer)
                    && !(value instanceof Long) && !(value instanceof LocalDate)) {
                throw new IllegalArgumentException("Unsupported workbook cell type: " + value.getClass());
            }
        }
        values = Collections.unmodifiableList(new ArrayList<>(values));
        if (role == RowKind.EMPTY && !values.isEmpty()) {
            throw new IllegalArgumentException("EMPTY rows cannot contain values");
        }
    }
}
