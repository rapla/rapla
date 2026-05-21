package org.rapla.rest;

import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;
import java.util.Map;

/**
 * REST contract for {@code /api/resources}. Single source of truth for
 * path/verb/params; {@code RaplaResourcesController} implements it.
 */
@HttpExchange("/api/resources")
public interface RaplaResourcesService
{
    @GetExchange
    List<AllocatableImpl> list(@RequestParam(value = "resourceTypes", required = false) List<String> resourceTypes,
                                @RequestParam(value = "attributeFilter", required = false) Map<String, String> attributeFilter) throws RaplaException;

    @GetExchange("/{id}")
    AllocatableImpl get(@PathVariable("id") String id) throws RaplaException;

    @DeleteExchange("/{id}")
    void delete(@PathVariable("id") String id) throws RaplaException;

    @PutExchange
    AllocatableImpl update(@RequestBody AllocatableImpl resource) throws RaplaException;

    @PostExchange
    AllocatableImpl create(@RequestBody AllocatableImpl resource) throws RaplaException;
}
