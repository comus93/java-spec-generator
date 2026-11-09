package com.comus93.specgen.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultOutputDirectoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesDecodedJarParent() throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("한글 and spaces"));
        Path jar = Files.createFile(directory.resolve("spec generator.jar"));

        assertEquals(directory.resolve("output"),
                DefaultOutputDirectory.resolve(jar.toUri().toURL(), temporaryDirectory));
    }

    @Test
    void initializesEditableFieldAndRetainsUserValue() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            SpecGeneratorFrame frame = new SpecGeneratorFrame();
            try {
                JTextField output = field(frame, "outputDirectoryField", JTextField.class);
                assertEquals(DefaultOutputDirectory.resolve().toString(), output.getText());
                assertTrue(output.isEditable());
                output.setText("custom output");
                field(frame, "systemField", JTextField.class).setText("system");
                field(frame, "jspMode", JRadioButton.class).doClick();
                assertEquals("custom output", output.getText());
            } finally {
                frame.dispose();
            }
        });
    }

    private static <T> T field(SpecGeneratorFrame frame, String name, Class<T> type) {
        try {
            java.lang.reflect.Field field = SpecGeneratorFrame.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(frame));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
