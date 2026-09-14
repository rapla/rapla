package org.rapla.client.swing;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwingSafe
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SwingSafe.class);
    private SwingSafe() {}

    public static void invokeLater(Runnable r)
    {
        SwingUtilities.invokeLater(() -> {
            try { r.run(); }
            catch (Throwable t) { LOGGER.error("Uncaught exception in EDT runnable", t); }
        });
    }
}
