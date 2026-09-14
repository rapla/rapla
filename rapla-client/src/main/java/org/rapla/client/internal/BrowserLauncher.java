package org.rapla.client.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class BrowserLauncher
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserLauncher.class);
    private BrowserLauncher() {}

    public static void open(URI url) throws IOException
    {
        if (tryDesktopBrowse(url)) return;

        if (isWsl())
        {
            if (tryProcess("wslview", url.toString())) return;
            if (tryProcess("cmd.exe", "/c", "start", "", url.toString())) return;
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
            if (tryProcess(cmd)) return;
        }
        throw new IOException("No usable browser launcher found for URL: " + url);
    }

    private static boolean tryDesktopBrowse(URI url)
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
            LOGGER.debug("Desktop.browse failed: {}", t.getMessage());
        }
        return false;
    }

    private static boolean tryProcess(String... cmd)
    {
        try
        {
            new ProcessBuilder(cmd).inheritIO().start();
            return true;
        }
        catch (IOException notFound)
        {
            LOGGER.debug("browser launch via {} failed: {}", cmd[0], notFound.getMessage());
            return false;
        }
    }

    public static boolean isWsl()
    {
        return System.getenv("WSL_DISTRO_NAME") != null
                || Files.exists(Path.of("/proc/sys/fs/binfmt_misc/WSLInterop"));
    }
}
