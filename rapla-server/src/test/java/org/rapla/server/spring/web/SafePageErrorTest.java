package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A6: the page-error helper must render text/plain with neutralised markup and
 * must never write a stack trace into the response body.
 */
class SafePageErrorTest
{
    @Test
    void reflectedMarkupIsNeutralisedAndPlainText() throws Exception
    {
        StringWriter body = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(false);
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        SafePageError.write(response, 404,
                "The calendar '<script>alert(1)</script>' for user <img src=x onerror=alert(1)> not found",
                null, null);

        verify(response).setStatus(404);
        verify(response).setContentType("text/plain; charset=UTF-8");
        String out = body.toString();
        assertFalse(out.contains("<"), "angle brackets must be neutralised: " + out);
        assertFalse(out.contains(">"), out);
    }

    @Test
    void stackTraceIsNeverWrittenToBody() throws Exception
    {
        StringWriter body = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(false);
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        Exception cause = new IllegalStateException("secret internal detail");
        SafePageError.write(response, 500, "An error occurred.", null, cause);

        String out = body.toString();
        assertFalse(out.contains("IllegalStateException"), out);
        assertFalse(out.contains("secret internal detail"), out);
        assertFalse(out.contains("at "), "no stack frames in body: " + out);
        assertEquals("An error occurred.", out);
    }

    @Test
    void committedResponseIsNotRewritten()
    {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(true);
        assertDoesNotThrow(() -> SafePageError.write(response, 500, "x", null, null));
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void sanitizeHandlesNull()
    {
        assertEquals("", SafePageError.sanitize(null));
        assertEquals("(a)", SafePageError.sanitize("<a>"));
    }
}
