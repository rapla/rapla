package org.rapla.rest;

import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST contract for {@code /api/events}. Single source of truth for
 * path/verb/params; {@code RaplaEventsController} implements it.
 */
@HttpExchange("/api/events")
public interface RaplaEventsService
{
    @GetExchange
    List<ReservationImpl> list(@RequestParam(value = "start", required = false) LocalDateTime start,
                                @RequestParam(value = "end", required = false) LocalDateTime end,
                                @RequestParam(value = "resources", required = false) List<String> resources,
                                @RequestParam(value = "owners", required = false) List<String> owners,
                                @RequestParam(value = "eventTypes", required = false) List<String> eventTypes,
                                @RequestParam(value = "attributeFilter", required = false) Map<String, String> attributeFilter) throws Exception;

    @GetExchange("/{id}")
    ReservationImpl get(@PathVariable("id") String id) throws RaplaException;

    @PatchExchange("/{id}")
    ReservationImpl patch(@PathVariable("id") String id, @RequestBody ReservationImpl event) throws Exception;

    @PutExchange
    ReservationImpl update(@RequestBody ReservationImpl event) throws RaplaException;

    @DeleteExchange("/{id}")
    boolean delete(@PathVariable("id") String id) throws RaplaException;

    @PostExchange
    ReservationImpl create(@RequestBody ReservationImpl event) throws RaplaException;
}
