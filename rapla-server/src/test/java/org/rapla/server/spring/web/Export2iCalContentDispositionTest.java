package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * H5 (security): the Content-Disposition value is built from a user-controlled
 * calendar name; it must be quoted and free of CR/LF/quote injection.
 */
class Export2iCalContentDispositionTest
{
    @Test
    void normalNameIsQuoted()
    {
        assertEquals("attachment; filename=\"weekly.ics\"",
                Export2iCalController.contentDispositionAttachment("weekly"));
    }

    @Test
    void crlfAndQuotesAreStripped()
    {
        String v = Export2iCalController.contentDispositionAttachment("a\r\nSet-Cookie: x=1\"b");
        assertFalse(v.contains("\r"), v);
        assertFalse(v.contains("\n"), v);
        // the only quotes are the wrapping pair
        assertEquals(2, v.chars().filter(c -> c == '"').count(), v);
    }
}
