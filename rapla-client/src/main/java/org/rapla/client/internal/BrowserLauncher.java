package org.rapla.client.internal;

import org.rapla.logger.Logger;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class BrowserLauncher
{
    private BrowserLauncher() {}

    public static void open(URI url, Logger logger) throws IOException
    {
        if (tryDesktopBrowse(url, logger)) return;

        if (isWsl())
        {
            if (tryProcess(logger, "wslview", url.toString())) return;
            if (tryProcess(logger, "cmd.exe", "/c", "start", "", url.toString())) return;
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String[]> attempts;
        if (os.contains("win"))
        {
            attempts = List.<String[]>of(
                    new String[]{"rundll32", "url.dll,FileProtocolHandler", url.toString()},
                    new String[]{"cmd", "/c", "start", "", url.toString()});
        }
        else if (os.contains("mac"))
        {
            attempts = List.<String[]>of(new String[]{"open", url.toString()});
        }
        else
        {
            attempts = List.<String[]>of(
                    new String[]{"xdg-open", url.toString()},
                    new String[]{"gio", "open", url.toString()},
                    new String[]{"kde-open5", url.toString()},
                    new String[]{"sensible-browser", url.toString()},
                    new String[]{"x-www-browser", url.toString()},
                    new String[]{"firefox", url.toString()});
        }
        for (String[] cmd : attempts)
        {
            if (tryProcess(logger, cmd)) return;
        }
        throw new IOException("No usable browser launcher found for URL: " + url);
    }

    private static boolean tryDesktopBrowse(URI url, Logger logger)
    {
        try
        {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            {
                Desktop.getDesktop().browse(url);
                return true;
            }
        }
        catch (Throwable t)
        {
            if (logger != null) logger.debug("Desktop.browse failed: " + t.getMessage());
        }
        return false;
    }

    private static boolean tryProcess(Logger logger, String... cmd)
    {
        try
        {
            new ProcessBuilder(cmd).inheritIO().start();
            return true;
        }
        catch (IOException notFound)
        {
            if (logger != null) logger.debug("browser launch via " + cmd[0] + " failed: " + notFound.getMessage());
            return false;
        }
    }

    public static boolean isWsl()
    {
        return System.getenv("WSL_DISTRO_NAME") != null
                || Files.exists(Path.of("/proc/sys/fs/binfmt_misc/WSLInterop"));
    }
}
