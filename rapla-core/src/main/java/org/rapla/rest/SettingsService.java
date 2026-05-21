package org.rapla.rest;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.rest.dto.SystemSettings;
import org.rapla.rest.dto.UserSettings;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

/**
 * REST contract for {@code /api/settings/*}. After PRD 049 this is the single
 * source of truth for routing; {@code SettingsController} implements it.
 *
 * <p>Swing reads via the GET methods (proxied through {@code HttpServiceProxyFactory})
 * but never calls the PUTs — Swing's Option panels go through
 * {@code facade.store(...)} for atomic-save semantics. The PUTs exist for the
 * Angular SPA, which doesn't share Swing's atomic-save dialog architecture.
 */
@HttpExchange("/api/settings")
public interface SettingsService
{
    @GetExchange("/system")
    SystemSettings getSystem() throws RaplaException;

    @PutExchange("/system")
    SystemSettings setSystem(@RequestBody SystemSettings body) throws RaplaException;

    @GetExchange("/calendar")
    RaplaConfiguration getCalendar() throws RaplaException;

    @PutExchange("/calendar")
    RaplaConfiguration setCalendar(@RequestBody RaplaConfiguration body) throws RaplaException;

    @GetExchange("/me")
    UserSettings getMe() throws RaplaException;

    @PutExchange("/me")
    UserSettings setMe(@RequestBody UserSettings body) throws RaplaException;
}
