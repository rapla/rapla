package org.rapla.client.spring;

import java.net.URL;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Security audit PH3 — the JNLP manifest no longer names the server host; the Swing client resolves its server URL
 * against the Web Start codebase. Without Web Start (exec:java, standalone installer) the previous absolute default
 * stays in force, and nothing throws at startup.
 */
class ClientDownloadUrlTest
{
    private static final URL CODEBASE = url("http://rapla.example:8051/");

    private static URL url(String s)
    {
        try
        {
            return new URL(s);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    void relativeValueResolvesAgainstCodebase()
    {
        assertEquals(url("http://rapla.example:8051/"), ClientConfig.resolveDownloadUrl("./", CODEBASE));
    }

    @Test
    void absoluteValueIsUnchanged()
    {
        assertEquals(url("http://server.example:9000/"), ClientConfig.resolveDownloadUrl("http://server.example:9000/", CODEBASE));
    }

    @Test
    void missingValueUsesCodebase()
    {
        assertEquals(CODEBASE, ClientConfig.resolveDownloadUrl(null, CODEBASE));
        assertEquals(CODEBASE, ClientConfig.resolveDownloadUrl("  ", CODEBASE));
    }

    @Test
    void withoutCodebaseMissingValueKeepsTheDefault()
    {
        assertEquals(url("http://localhost:8051/"), ClientConfig.resolveDownloadUrl(null, null));
    }

    @Test
    void withoutCodebaseAbsoluteValueIsUnchanged()
    {
        assertEquals(url("http://server.example:9000/"), ClientConfig.resolveDownloadUrl("http://server.example:9000/", null));
    }

    @Test
    void withoutCodebaseRelativeValueFallsBackToTheDefault()
    {
        assertEquals(url("http://localhost:8051/"), ClientConfig.resolveDownloadUrl("./", null));
    }
}
