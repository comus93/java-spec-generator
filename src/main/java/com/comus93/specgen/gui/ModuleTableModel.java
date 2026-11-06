package com.comus93.specgen.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.swing.table.AbstractTableModel;

import com.comus93.specgen.config.ModuleSelection;

public final class ModuleTableModel extends AbstractTableModel {

    public static final int SUBSYSTEM_COLUMN = 0;
    public static final int MODULE_COLUMN = 1;
    public static final int SOURCE_FOLDER_COLUMN = 2;
    public static final int REMOVE_COLUMN = 3;

    private static final String[] COLUMN_NAMES = {
            "서브 시스템",
            "모듈",
            "선택한 소스 폴더",
            "제거"
    };

    private final List<Row> rows = new ArrayList<>();
    private String system;

    public ModuleTableModel() {
        this("");
    }

    public ModuleTableModel(String system) {
        this.system = system == null ? "" : system;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == SOURCE_FOLDER_COLUMN ? Path.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row row = rows.get(rowIndex);
        return switch (columnIndex) {
            case SUBSYSTEM_COLUMN -> row.subsystem;
            case MODULE_COLUMN -> row.moduleName;
            case SOURCE_FOLDER_COLUMN -> row.sourceFolder;
            case REMOVE_COLUMN -> "X";
            default -> "";
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == SUBSYSTEM_COLUMN || columnIndex == MODULE_COLUMN;
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        Row row = rows.get(rowIndex);
        String text = value == null ? "" : value.toString();
        switch (columnIndex) {
            case SUBSYSTEM_COLUMN -> {
                row.subsystem = text;
                row.subsystemEdited = true;
            }
            case MODULE_COLUMN -> row.moduleName = text;
            default -> {
                return;
            }
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    public void addFolders(List<Path> folders) {
        Objects.requireNonNull(folders, "folders");
        Set<Path> known = new HashSet<>();
        for (Row row : rows) {
            known.add(row.sourceFolder);
        }
        List<Row> added = new ArrayList<>();
        for (Path folder : folders) {
            Objects.requireNonNull(folder, "folder");
            Path normalized = folder.toAbsolutePath().normalize();
            if (!known.add(normalized)) {
                continue;
            }
            added.add(new Row(normalized, system, defaultModuleName(normalized)));
        }
        if (added.isEmpty()) {
            return;
        }
        int first = rows.size();
        rows.addAll(added);
        fireTableRowsInserted(first, rows.size() - 1);
    }

    public void removeRow(int rowIndex) {
        rows.remove(rowIndex);
        fireTableRowsDeleted(rowIndex, rowIndex);
    }

    public void updateSystem(String system) {
        this.system = system == null ? "" : system;
        if (rows.isEmpty()) {
            return;
        }
        for (Row row : rows) {
            if (!row.subsystemEdited) {
                row.subsystem = this.system;
            }
        }
        fireTableRowsUpdated(0, rows.size() - 1);
    }

    public List<ModuleSelection> toSelections() {
        return rows.stream()
                .map(row -> new ModuleSelection(row.sourceFolder, row.subsystem, row.moduleName))
                .toList();
    }

    private static String defaultModuleName(Path folder) {
        Path fileName = folder.getFileName();
        return fileName == null ? folder.toString() : fileName.toString();
    }

    private static final class Row {
        private final Path sourceFolder;
        private String subsystem;
        private String moduleName;
        private boolean subsystemEdited;

        private Row(Path sourceFolder, String subsystem, String moduleName) {
            this.sourceFolder = sourceFolder;
            this.subsystem = subsystem;
            this.moduleName = moduleName;
        }
    }
}
