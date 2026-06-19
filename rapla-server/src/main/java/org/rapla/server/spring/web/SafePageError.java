package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Safe error output for the page controllers (A6). Always renders the body as
 * {@code text/plain; charset=UTF-8} — so a reflected request parameter can never
 * be interpreted as HTML/JS — and NEVER writes a stack trace to the response body
 * (the cause is logged server-side instead).
 *
 * <p>Replaces the ad-hoc {@code response.getWriter().print(message)} +
 * {@code e.printStackTrace(response.getWriter())} pattern in
 * {@link CalendarPageController} / {@link Export2iCalController}, which leaked
 * reflected XSS and internal stack traces to unauthenticated callers
 * ({@code /rapla/calendar}, {@code /ical} are in the public allow-list).
 */
public final class SafePageError
{
    private SafePageError()
    {
    }

    /**
     * Write a plain-text error body with the given status.
     *
     * @param cause optional throwable — logged via {@code logger}, never sent to the client
     */
    public static void write(HttpServletResponse response, int status, String message,
                             Logger logger, Throwable cause)
    {
        if (logger != null)
        {
            if (cause != null)
            {
                logger.warn("page error {} - {}", status, message, cause);
            }
            else
            {
                logger.warn("page error {} - {}", status, message);
            }
        }
        if (response.isCommitted())
        {
            return; // response already started — can't safely replace it
        }
        response.reset();
        response.setStatus(status);
        response.setContentType("text/plain; charset=UTF-8");
        try
        {
            PrintWriter out = response.getWriter();
            out.print(sanitize(message));
            out.flush();
        }
        catch (IOException io)
        {
            if (logger != null)
            {
                logger.warn("failed writing error response", io);
            }
        }
    }

    /**
     * Defensive even for text/plain: neutralise angle brackets so the text can't
     * inject markup if it is later copied into an HTML context (e.g. a log viewer).
     */
    static String sanitize(String message)
    {
        if (message == null)
        {
            return "";
        }
        return message.replace('<', '(').replace('>', ')');
    }
}
