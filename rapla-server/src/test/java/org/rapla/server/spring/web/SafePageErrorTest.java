package org.rapla.server.spring.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
    void describeNamesPathLinkKindAndLookupParametersButNeverTheKey()
    {
        MockHttpServletRequest plain = new MockHttpServletRequest("GET", "/rapla/calendar");
        plain.setParameter("user", "homer");
        plain.setParameter("file", "week\r\nforged");
        plain.setParameter("allocatable_id", "r1");
        assertEquals("path=/rapla/calendar, plain link, user=homer, file=week__forged, allocatable_id=r1", SafePageError.describe(plain));

        MockHttpServletRequest encrypted = new MockHttpServletRequest("GET", "/rapla/ical");
        encrypted.setParameter("key", "c2VjcmV0LWNhcGFiaWxpdHk");
        encrypted.setParameter("salt", "s4lt");
        assertEquals("path=/rapla/ical, encrypted link (key 23 chars)", SafePageError.describe(encrypted));
    }

    @Test
    void notFoundLogsReasonAndRequestButAnswersOnlyTheMessage() throws Exception
    {
        Logger logger = (Logger) LoggerFactory.getLogger("rapla.404.test");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rapla/calendar");
        request.setParameter("user", "homer");
        request.setParameter("file", "week");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SafePageError.notFound(request, response, "404 Calendar not found for u1", "calendar not found", logger);

        assertEquals("page error 404 - 404 Calendar not found for u1 [calendar not found; path=/rapla/calendar, plain link, user=homer, file=week]",
                appender.list.get(0).getFormattedMessage());
        assertEquals(404, response.getStatus());
        assertEquals("404 Calendar not found for u1", response.getContentAsString());
    }

    @Test
    void sanitizeHandlesNull()
    {
        assertEquals("", SafePageError.sanitize(null));
        assertEquals("(a)", SafePageError.sanitize("<a>"));
    }
}
