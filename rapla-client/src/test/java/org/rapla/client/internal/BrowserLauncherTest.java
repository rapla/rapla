package org.rapla.client.internal;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class BrowserLauncherTest
{
    @Test
    public void wslDetectionMatchesEnvironment()
    {
        boolean expected = System.getenv("WSL_DISTRO_NAME") != null
                || Files.exists(Path.of("/proc/sys/fs/binfmt_misc/WSLInterop"));
        assertEquals(expected, BrowserLauncher.isWsl());
    }

    @Test
    public void wslDetectionIsTrueWhenRunningUnderWsl()
    {
        Assume.assumeTrue("only runs under WSL", System.getenv("WSL_DISTRO_NAME") != null);
        assertTrue(BrowserLauncher.isWsl());
    }
}
