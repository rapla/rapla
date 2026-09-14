package org.rapla.plugin.externaleventimport.client.swing;

import org.junit.jupiter.api.Test;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaFrame;

import javax.swing.JButton;
import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The wizard's busy glasspane must block the window the action came from (e.g. the
 * reservation edit window for sync), not unconditionally the main window — the
 * {@code DialogUiFactory.busy} fallback always targets the main window, which froze
 * the calendar while the edit window stayed interactive.
 */
class ExternalEventImportBusyTargetTest
{
    @Test
    void ownerWindowResolvesTheWindowContainingThePopupSource()
    {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display for window construction");
        RaplaFrame editFrame = new RaplaFrame();
        javax.swing.JDialog editDialog = new javax.swing.JDialog(editFrame);
        try
        {
            JButton syncButton = new JButton();
            editFrame.getContentPane().add(syncButton);
            assertSame(editFrame, ExternalEventImportDialogImpl.ownerWindow(new SwingPopupContext(syncButton, null)));

            JButton dialogButton = new JButton();
            editDialog.getContentPane().add(dialogButton);
            assertSame(editDialog, ExternalEventImportDialogImpl.ownerWindow(new SwingPopupContext(dialogButton, null)),
                    "a component inside a child dialog (the reservation edit window is a DialogUI) "
                            + "must resolve to that dialog, not the main frame");
        }
        finally
        {
            editDialog.dispose();
            editFrame.dispose();
        }
    }

    @Test
    void ownerWindowFallsBackToNullWithoutASource()
    {
        assertNull(ExternalEventImportDialogImpl.ownerWindow(new SwingPopupContext(null, null)));
        assertNull(ExternalEventImportDialogImpl.ownerWindow(null));
    }
}
