package org.rapla.rest;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;

/** Batch lookup of the {@code externalid} annotation (import/sync stamp) against
 *  the server's in-memory index.
 *
 *  <p>Clients that reconcile an external source with rapla otherwise have to pull
 *  events over a date window and group them themselves — which misses everything
 *  outside the window. */
@HttpExchange("/api/externalids")
public interface ExternalIdLookup
{
    /** Maps each known external id to the ids of ALL reservations carrying it —
     *  the annotation is not unique in practice: one seminar number can carry every event
     *  copied from a template, i.e. dozens of them. Ids that don't exist — and ids whose reservations the caller
     *  may not read — are simply absent from the result; the two cases are
     *  indistinguishable by design (AGENTS.md §12). */
    @PostExchange("/resolve")
    Map<String, List<String>> resolve(@RequestBody List<String> externalIds) throws RaplaException;
}
