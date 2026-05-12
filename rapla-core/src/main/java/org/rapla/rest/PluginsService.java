package org.rapla.rest;

import org.rapla.framework.RaplaException;
import org.rapla.rest.dto.PluginInfo;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * Client-side HTTP exchange interface for the generic /plugins REST surface.
 * Server-side counterpart: {@code PluginsController}.
 *
 * <p>Used by Swing factory classes to decide whether their feature is enabled,
 * replacing the previous {@code getSystemPreferences().getEntryAsBoolean(...)}
 * reads. Each factory should cache the result — these methods make an HTTP
 * round-trip on every call.
 */
@HttpExchange("/plugins")
public interface PluginsService
{
    @GetExchange
    List<PluginInfo> list() throws RaplaException;

    @GetExchange("/{id}")
    PluginInfo get(@PathVariable("id") String id) throws RaplaException;
}
