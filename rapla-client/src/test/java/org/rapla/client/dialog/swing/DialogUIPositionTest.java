package org.rapla.client.dialog.swing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.i18n.SwingBundleManager;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.i18n.BundleManager;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.scheduler.CommandScheduler;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogUIPositionTest
{
    @Test
    void dialogAnchorsOnOwnerWhenCapturedParentIsDetached() throws Exception
    {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "Swing positioning requires a real display");

        Logger logger = RaplaBootstrapLogger.createRaplaLogger();
        BundleManager bundleManager = new SwingBundleManager(logger);
        RaplaResources i18n = new RaplaResources(bundleManager);
        CommandScheduler scheduler = new DefaultScheduler(logger);
        DialogUiFactoryInterface factory =
                new DialogUI.DialogUiFactory(i18n, scheduler, bundleManager, logger);

        AtomicReference<JFrame> frameRef = new AtomicReference<>();
        AtomicReference<DialogInterface> dialogRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("anchor test");
            frame.setBounds(300, 200, 600, 400);
            JPanel inner = new JPanel();
            frame.getContentPane().add(inner);
            frame.setVisible(true);
            frameRef.set(frame);

            PopupContext popupContext = new SwingPopupContext(inner, null);
            DialogInterface dlg = factory.createTextDialog(
                    popupContext, "anchor test", "body", new String[]{"ok"});
            dialogRef.set(dlg);

            frame.getContentPane().remove(inner);
            frame.revalidate();
            assertFalse(inner.isShowing(), "Inner panel should be detached for the race-test");

            dlg.start(true);
        });

        try
        {
            DialogUI ui = (DialogUI) dialogRef.get();
            Point loc = ui.getLocation();
            Rectangle bounds = frameRef.get().getBounds();
            int frameCx = bounds.x + bounds.width / 2;
            int frameCy = bounds.y + bounds.height / 2;
            int dx = Math.abs(loc.x - frameCx);
            int dy = Math.abs(loc.y - frameCy);
            assertTrue(dx < bounds.width && dy < bounds.height,
                    "Dialog should land near the centre of the owner frame ("
                            + frameCx + "," + frameCy + ") but landed at " + loc);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> {
                dialogRef.get().close();
                frameRef.get().dispose();
            });
        }
    }
}
