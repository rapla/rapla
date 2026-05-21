package org.rapla.rest;

import org.rapla.framework.RaplaException;
import org.rapla.rest.dto.PluginInfo;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

/**
 * REST contract for the generic /plugins surface. After PRD 049 this interface
 * is the single source of truth for path/verb/params; the matching
 * {@code PluginsController} in rapla-server implements it directly.
 */
@HttpExchange("/api/plugins")
public interface PluginsService
{
    record EnabledRequest(boolean enabled) {}

    @GetExchange
    List<PluginInfo> list() throws RaplaException;

    @GetExchange("/{id}")
    PluginInfo get(@PathVariable("id") String id) throws RaplaException;

    @PutExchange("/{id}/enabled")
    PluginInfo setEnabled(@PathVariable("id") String id,
                           @RequestBody EnabledRequest body) throws RaplaException;
}
