package org.rapla.server.spring.web;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.framework.RaplaException;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<Map<String, Object>> handleSecurity(RaplaSecurityException ex)
    {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex)
    {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, IllegalArgumentException.class, AssertionError.class })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Throwable ex)
    {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
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
    public ResponseEntity<Map<String, Object>> handleNewVersion(RaplaNewVersionException ex)
    {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RaplaException.class)
    public ResponseEntity<Map<String, Object>> handleRapla(RaplaException ex)
    {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message)
    {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message != null ? message : status.getReasonPhrase()
        ));
    }
}
