package org.rapla.plugin.export2ical;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/ical/config")
public interface ICalConfigService
{
    @GetExchange
    DefaultConfiguration getConfig() throws RaplaException;

    @GetExchange("/default")
    DefaultConfiguration getUserDefaultConfig() throws RaplaException;

    @GetExchange("/user")
    UserICalSettings getUserSettings() throws RaplaException;
}