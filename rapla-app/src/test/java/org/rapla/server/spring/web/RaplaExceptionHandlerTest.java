package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.framework.RaplaException;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRD 009 Phase 5 — verifies the {@link RaplaExceptionHandler} maps each documented
 * exception to the right HTTP status with the expected body shape.
 *
 * <p>Unit-level test (no Spring context). Exercises the handler methods directly so
 * the mapping is locked in independently of any controller's wiring.
 */
class RaplaExceptionHandlerTest
{
    private final RaplaExceptionHandler handler = new RaplaExceptionHandler();

    @Test
    void securityException_mapsTo401()
    {
        ResponseEntity<Map<String, Object>> response = handler.handleSecurity(
                new RaplaSecurityException("bad credentials"));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertBodyShape(response.getBody(), 401, "Unauthorized", "bad credentials");
    }

    @Test
    void entityNotFoundException_mapsTo404()
    {
        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(
                new EntityNotFoundException("no such resource: ghost"));
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
        assertBodyShape(response.getBody(), 404, "Not Found", "no such resource: ghost");
    }

    @Test
    void missingRequestParameter_mapsTo400()
    {
        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(
                new MissingServletRequestParameterException("userId", "String"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    }

    @Test
    void illegalArgumentException_mapsTo400()
    {
        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(
                new IllegalArgumentException("count must be positive"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
        assertBodyShape(response.getBody(), 400, "Bad Request", "count must be positive");
    }

    @Test
    void assertionError_mapsTo400()
    {
        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(
                new AssertionError("Assertion failed"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    }

    @Test
    void newVersionException_mapsTo409()
    {
        // PRD 026 §B2 — concurrent-modification must be distinguishable from
        // the 500 catch-all so the SPA can drive its refresh-and-retry flow.
        ResponseEntity<Map<String, Object>> response = handler.handleNewVersion(
                new RaplaNewVersionException("Reservation 'My Event' was modified by another user"));
        assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
        assertBodyShape(response.getBody(), 409, "Conflict",
                "Reservation 'My Event' was modified by another user");
    }

    @Test
    void genericRaplaException_mapsTo500()
    {
        ResponseEntity<Map<String, Object>> response = handler.handleRapla(
                new RaplaException("operator unavailable"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
        assertBodyShape(response.getBody(), 500, "Internal Server Error", "operator unavailable");
    }

    @Test
    void nullMessage_fallsBackToReasonPhrase()
    {
        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(
                new EntityNotFoundException(null));
        assertEquals("Not Found", response.getBody().get("message"));
    }

    private static void assertBodyShape(Map<String, Object> body, int status, String error, String message)
    {
        assertNotNull(body, "response body");
        assertEquals(status, body.get("status"));
        assertEquals(error, body.get("error"));
        assertEquals(message, body.get("message"));
        assertInstanceOf(Integer.class, body.get("status"));
    }
}
