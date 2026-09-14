package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.eventimport.ParsedTemplateResult;
import org.rapla.plugin.eventimport.TemplateImport;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Server-side wrapper exposing the {@link TemplateImport} implementation
 *  (registered as a {@code @Bean} in {@code ServerServiceConfig}) as REST.
 *
 *  <p>Thin delegator with explicit mappings — the skill's exception category 2:
 *  the impl stays its own bean, so the controller cannot {@code implements
 *  TemplateImport} without creating a duplicate-bean conflict.
 *
 *  <p>Gated on "may create events" (AGENTS.md §12): the call dumps a deployment-specific
 *  database view row by row, and its only purpose is to create events from it. Rapla 2.0
 *  had no check here at all; requiring {@code isAdmin()} would lock out the planning
 *  account that actually runs the import. */
@RestController
@RequestMapping(value = "/api/templateimport", produces = "application/json")
@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.eventimport", matchIfMissing = true)
public class TemplateImportController
{
    private final TemplateImport service;
    private final CachableStorageOperator operator;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public TemplateImportController(TemplateImport service, CachableStorageOperator operator, RemoteSession session,
            HttpServletRequest request)
    {
        this.service = service;
        this.operator = operator;
        this.session = session;
        this.request = request;
    }

    @PostMapping("/importFromServer")
    public ParsedTemplateResult importFromServer() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!operator.getPermissionController().canCreateReservation(user))
        {
            throw new RaplaSecurityException("Importing event templates requires permission to create events");
        }
        return service.importFromServer();
    }

    /** An authenticated caller without the permission → 403; the global handler maps
     *  {@link RaplaSecurityException} to 401, which would send the Swing client
     *  into a re-login loop. Body intentionally empty (§12). */
    @ExceptionHandler(RaplaSecurityException.class)
    public ResponseEntity<Void> forbidden()
    {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
