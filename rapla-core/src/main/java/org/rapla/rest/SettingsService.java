package org.rapla.rest;

import org.rapla.framework.RaplaException;
import org.rapla.rest.dto.SystemSettings;
import org.rapla.rest.dto.UserSettings;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Client-side HTTP exchange interface for {@code /settings/*}.
 * Server-side counterpart: {@code SettingsController}.
 *
 * <p>Used by Swing option panels ({@code RaplaStartOption},
 * {@code WarningsOption}) to read fresh values from the dedicated
 * endpoint instead of the bulk {@code /storage/resources} preference
 * cache. Save path is unchanged — the dialog framework's
 * {@code facade.store(clone)} still routes through
 * {@code /storage/dispatch} (also REST, just a different shape).
 *
 * <p>Only the GET side is exposed here. The dialog framework's
 * atomic-save contract over the shared preference clone makes a direct
 * PUT path risky (clone-overwrite issue — see SettingsController javadoc).
 */
@HttpExchange("/settings")
public interface SettingsService
{
    @GetExchange("/system")
    SystemSettings getSystem() throws RaplaException;

    @GetExchange("/me")
    UserSettings getMe() throws RaplaException;
}
