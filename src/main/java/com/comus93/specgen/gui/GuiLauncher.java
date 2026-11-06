package com.comus93.specgen.gui;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class GuiLauncher {

    private GuiLauncher() {
    }

    public static void launch() {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("GUI is unavailable in a headless environment.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new SpecGeneratorFrame().setVisible(true);
        });
    }
}
