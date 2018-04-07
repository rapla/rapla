package org.rapla.plugin.tableview.client.swing;

import org.rapla.client.swing.toolkit.DisabledGlassPane;

import java.awt.event.MouseAdapter;

import javax.swing.*;

public class MyGlassPaneWrapper extends JLayeredPane {
    private DisabledGlassPane glassPanel = new DisabledGlassPane();

    public MyGlassPaneWrapper(JComponent myPanel) {
        glassPanel.setOpaque(false);
        glassPanel.setVisible(false);
        glassPanel.addMouseListener(new MouseAdapter() {});
        glassPanel.setFocusable(true);

        myPanel.setSize(myPanel.getPreferredSize());
        add(myPanel, JLayeredPane.DEFAULT_LAYER);
        add(glassPanel, JLayeredPane.PALETTE_LAYER);

        glassPanel.setPreferredSize(myPanel.getPreferredSize());
        glassPanel.setSize(myPanel.getPreferredSize());
        setPreferredSize(myPanel.getPreferredSize());
    }

    public void activateGlassPane(boolean activate) {
        glassPanel.setVisible(activate);
        if (activate) {
            glassPanel.requestFocusInWindow();
            glassPanel.setFocusTraversalKeysEnabled(false);
        }
    }

}