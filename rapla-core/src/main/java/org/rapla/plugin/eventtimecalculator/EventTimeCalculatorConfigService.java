package org.rapla.plugin.eventtimecalculator;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * REST surface for the event-time-calculator plugin's configuration.
 *
 * <ul>
 *   <li>{@code /eventtimecalculator/system-config} returns the deployment-wide
 *       default that the admin set (system preferences scope). Any authenticated
 *       user can read it — it's the fallback when a user hasn't overridden the
 *       value.</li>
 *   <li>{@code /eventtimecalculator/user-config} returns the calling user's
 *       per-user override, or an empty configuration if none set.</li>
 * </ul>
 *
 * <p>Replaces direct {@code getSystemPreferences().getEntry(SYSTEM_CONFIG)} and
 * {@code preferences.getEntry(USER_CONFIG)} reads in
 * {@code EventTimeCalculatorUserOption}.
 */
@HttpExchange("/eventtimecalculator")
public interface EventTimeCalculatorConfigService
{
    @GetExchange("/system-config")
    DefaultConfiguration getSystemConfig() throws RaplaException;

    @GetExchange("/user-config")
    DefaultConfiguration getUserConfig() throws RaplaException;
}
