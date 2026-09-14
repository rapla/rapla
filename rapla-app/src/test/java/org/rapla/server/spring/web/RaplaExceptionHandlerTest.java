package org.rapla.server.spring.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.framework.RaplaException;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void genericRaplaException_isLoggedAtErrorWithStackTrace()
    {
        // A 500 is an unexpected server fault — the wrapped cause must hit the server log
        // with its stack trace, or the failure is undiagnosable in production. The wrapper
        // message alone (which is all the client sees) is not enough.
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(RaplaExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try
        {
            handler.handleRapla(new RaplaException("Failed to create reservations from Dualis import",
                    new IllegalStateException("underlying cause")));
        }
        finally
        {
            logbackLogger.detachAppender(appender);
        }
        assertEquals(1, appender.list.size(), "exactly one log event expected");
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertNotNull(event.getThrowableProxy(), "the exception (with its cause/stack) must be logged");
    }

    @Test
    void clientFaultExceptions_areNotLoggedAtError()
    {
        // 4xx are expected client faults — logging them at ERROR would be noise.
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(RaplaExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try
        {
            handler.handleSecurity(new RaplaSecurityException("forbidden"));
            handler.handleNotFound(new EntityNotFoundException("ghost"));
            handler.handleBadRequest(new IllegalArgumentException("bad"));
        }
        finally
        {
            logbackLogger.detachAppender(appender);
        }
        assertFalse(appender.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR),
                "client-fault (4xx) exceptions must not be logged at ERROR");
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
