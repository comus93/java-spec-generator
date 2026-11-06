package com.comus93.specgen.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.config.ModuleSelection;

class ModuleTableModelTest {

    @TempDir
    Path tempDir;

    @Test
    void accumulatesFoldersAndRemovesOnlyExactNormalizedDuplicates() {
        Path firstModule = tempDir.resolve("one").resolve("shared");
        Path secondModule = tempDir.resolve("two").resolve("shared");
        Path childModule = firstModule.resolve("child");
        ModuleTableModel model = new ModuleTableModel();

        model.updateSystem("SYS1");
        model.addFolders(List.of(firstModule, secondModule, firstModule.resolve("."), childModule, firstModule));

        assertEquals(3, model.getRowCount());
        assertEquals(List.of(
                firstModule.toAbsolutePath().normalize(),
                secondModule.toAbsolutePath().normalize(),
                childModule.toAbsolutePath().normalize()),
                moduleFolders(model));
        assertEquals("shared", model.getValueAt(0, ModuleTableModel.MODULE_COLUMN));
        assertEquals("shared", model.getValueAt(1, ModuleTableModel.MODULE_COLUMN));
    }

    @Test
    void updateSystemRefreshesOnlyUneditedSubsystemsAndPreservesModuleEdits() {
        Path firstModule = tempDir.resolve("first");
        Path secondModule = tempDir.resolve("second");
        ModuleTableModel model = new ModuleTableModel();

        model.addFolders(List.of(firstModule, secondModule));
        model.updateSystem("SYS1");
        model.setValueAt("CUSTOM", 0, ModuleTableModel.SUBSYSTEM_COLUMN);
        model.setValueAt("edited-module", 0, ModuleTableModel.MODULE_COLUMN);
        model.setValueAt("renamed-second", 1, ModuleTableModel.MODULE_COLUMN);
        model.updateSystem("SYS2");

        List<ModuleSelection> selections = model.toSelections();
        assertEquals("CUSTOM", selections.get(0).subsystem());
        assertEquals("edited-module", selections.get(0).moduleName());
        assertEquals("SYS2", selections.get(1).subsystem());
        assertEquals("renamed-second", selections.get(1).moduleName());
    }

    @Test
    void removeRowDoesNotDeleteTheSourceFolder() throws IOException {
        Path module = tempDir.resolve("module");
        Path source = module.resolve("A.java");
        Files.createDirectories(module);
        Files.writeString(source, "class A {}");
        ModuleTableModel model = new ModuleTableModel();
        model.addFolders(List.of(module));

        model.removeRow(0);

        assertEquals(0, model.getRowCount());
        assertTrue(Files.isDirectory(module));
        assertTrue(Files.isRegularFile(source));
    }

    @Test
    void exposesAbsoluteReadOnlySourcePathsAndEditableNameColumns() {
        Path relative = Path.of("relative-module");
        ModuleTableModel model = new ModuleTableModel();

        model.addFolders(List.of(relative));

        Path sourceFolder = (Path) model.getValueAt(0, ModuleTableModel.SOURCE_FOLDER_COLUMN);
        assertTrue(sourceFolder.isAbsolute());
        assertEquals(relative.toAbsolutePath().normalize(), sourceFolder);
        assertTrue(model.isCellEditable(0, ModuleTableModel.SUBSYSTEM_COLUMN));
        assertTrue(model.isCellEditable(0, ModuleTableModel.MODULE_COLUMN));
        assertFalse(model.isCellEditable(0, ModuleTableModel.SOURCE_FOLDER_COLUMN));
        assertFalse(model.isCellEditable(0, ModuleTableModel.REMOVE_COLUMN));
    }

    private static List<Path> moduleFolders(ModuleTableModel model) {
        return model.toSelections().stream().map(ModuleSelection::sourceFolder).toList();
    }
}
