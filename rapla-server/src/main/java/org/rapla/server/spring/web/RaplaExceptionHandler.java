package org.rapla.server.spring.web;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.framework.RaplaException;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Centralized exception → HTTP status mapping for every {@code @RestController} in the server.
 *
 * <p>PRD 009 Phase 5. Without this, every {@link RaplaException} bubbles to Spring's
 * default handler and surfaces as 500 — even when the right answer is 404 (entity missing),
 * 403 (permission denied), or 400 (malformed request). The 2026-05-08 endpoint sweep showed
 * 4 endpoints returning 500 on conditions that should be 400 or 404.
 *
 * <p>Mapping table:
 * <pre>
 *   RaplaSecurityException                   → 403 Forbidden
 *   RaplaInvalidTokenException (extends RSE) → 401 Unauthorized
 *   EntityNotFoundException                  → 404 Not Found
 *   MissingServletRequestParameterException  → 400 Bad Request   (Spring's auto-thrown when @RequestParam(required=true) is absent)
 *   IllegalArgumentException, AssertionError → 400 Bad Request   (typical for null/empty IDs reaching Assert.notNull deep in the call chain)
 *   RaplaNewVersionException                 → 409 Conflict       (concurrent modification — another user changed the entity; SPA refreshes and retries)
 *   RaplaException                           → 500 Internal Server Error  (catch-all for unexpected server faults)
 * </pre>
 *
 * <p>Body shape: {@code {"status": <int>, "error": "<reason>", "message": "<exception msg>"}}.
 * Matches Spring Boot's default error attributes so existing client code that reads {@code message}
 * keeps working.
 */
@RestControllerAdvice
public class RaplaExceptionHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RaplaExceptionHandler.class);

    /**
     * Both bad-credentials-on-login and permission-denied throw
     * {@link RaplaSecurityException} in this codebase — there's no separate type
     * to distinguish "401 not authenticated" from "403 authenticated but forbidden".
     * Map both to <b>401 Unauthorized</b>: it's the right answer for the login flow
     * (the dominant case), and on a permission-denied call the client just sees
     * "auth issue" and re-prompts. A finer-grained exception type (e.g.
     * {@code RaplaPermissionDeniedException}) could be added later if 403 ever
     * becomes load-bearing for a real client decision.
     */
    @ExceptionHandler(RaplaSecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(RaplaSecurityException ex, HttpServletRequest request, HttpServletResponse response)
    {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, response);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex, HttpServletRequest request, HttpServletResponse response)
    {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), request, response);
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, IllegalArgumentException.class, AssertionError.class })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Throwable ex, HttpServletRequest request, HttpServletResponse response)
    {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), request, response);
    }

    /**
     * Concurrent-modification: another user changed the same entity between
     * this client's read and write. PRD 026 §B2 — the SPA needs a distinguishable
     * status to drive its "refresh and retry" flow; left as 500 (via the catch-all
     * below) the SPA can't tell this from a real server fault.
     * <p>
     * 409 over 412 because there's no precondition header on the request — this
     * is server-side optimistic-concurrency detection, not client-supplied
     * If-Match. The semantic match is "conflict with current state of the resource".
     */
    @ExceptionHandler(RaplaNewVersionException.class)
    public ResponseEntity<Map<String, Object>> handleNewVersion(RaplaNewVersionException ex, HttpServletRequest request, HttpServletResponse response)
    {
        return body(HttpStatus.CONFLICT, ex.getMessage(), request, response);
    }

    @ExceptionHandler(RaplaException.class)
    public ResponseEntity<Map<String, Object>> handleRapla(RaplaException ex, HttpServletRequest request, HttpServletResponse response)
    {
        // 500 = unexpected server fault. Log the full exception (and its cause) so the
        // failure is diagnosable server-side — the client only receives the wrapper message.
        LOGGER.error("Unhandled server fault mapped to 500: {}", ex.getMessage(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request, response);
    }

    /**
     * A page request (answer already started, a text content type, or a browser preferring HTML) gets a
     * plain-text status line — a Map body is not writable there. Everything else, and /api always, keeps JSON.
     */
    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, HttpServletRequest request, HttpServletResponse response)
    {
        if (!request.getRequestURI().startsWith(request.getContextPath() + "/api/") && pageLike(request, response))
        {
            SafePageError.write(response, status.value(), status.value() + " " + status.getReasonPhrase(), null, null);
            return null;
        }
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message != null ? message : status.getReasonPhrase()
        ));
    }

    private static boolean pageLike(HttpServletRequest request, HttpServletResponse response)
    {
        String contentType = response.getContentType();
        return response.isCommitted() || (contentType != null && contentType.startsWith("text/"))
                || quality(request, MediaType.TEXT_HTML) > quality(request, MediaType.APPLICATION_JSON);
    }

    private static double quality(HttpServletRequest request, MediaType wanted)
    {
        double best = 0;
        try
        {
            for (MediaType accepted : MediaType.parseMediaTypes(request.getHeader(HttpHeaders.ACCEPT)))
            {
                if (accepted.includes(wanted))
                {
                    best = Math.max(best, accepted.getQualityValue());
                }
            }
        }
        catch (InvalidMediaTypeException ignored)
        {
        }
        return best;
    }
}
