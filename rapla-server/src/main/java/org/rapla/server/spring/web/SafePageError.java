package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.plugin.urlencryption.UrlEncryption;
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
     * Which link a "not found" log line is about: path, encrypted or plain, and the decoded lookup parameters.
     * Never the key itself — an encrypted link is a capability, so only its length.
     */
    public static String describe(HttpServletRequest request)
    {
        StringBuilder description = new StringBuilder("path=").append(logSafe(request.getRequestURI()));
        String key = request.getParameter(UrlEncryption.ENCRYPTED_PARAMETER_NAME);
        description.append(key == null ? ", plain link" : ", encrypted link (key " + key.length() + " chars)");
        for (String name : new String[] { "user", "file", "allocatable_id" })
        {
            String value = request.getParameter(name);
            if (value != null)
            {
                description.append(", ").append(name).append('=').append(logSafe(value));
            }
        }
        return description.toString();
    }

    /** 404 whose log line names the reason and the request ({@link #describe}); the body stays the message alone. */
    public static void notFound(HttpServletRequest request, HttpServletResponse response, String message, String reason, Logger logger)
    {
        logger.warn("page error 404 - {} [{}; {}]", message, reason, describe(request));
        write(response, 404, message, null, null);
    }

    private static String logSafe(String value)
    {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", "_");
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
