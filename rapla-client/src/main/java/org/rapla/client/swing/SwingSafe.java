package org.rapla.client.swing;

import javax.swing.SwingUtilities;

import org.rapla.logger.Logger;

public final class SwingSafe
{
    private SwingSafe() {}

    public static void invokeLater(Logger logger, Runnable r)
    {
        SwingUtilities.invokeLater(() -> {
            try { r.run(); }
            catch (Throwable t) { logger.error("Uncaught exception in EDT runnable", t); }
        });
    }
}
