package org.rapla.plugin.exchangeconnector;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/api/exchange/config")
public interface ExchangeConnectorConfigRemote
{
    @GetExchange("/default")
    DefaultConfiguration getConfig() throws RaplaException;

    @GetExchange("/timezones")
    List<String> getTimezones() throws RaplaException;

    @GetExchange("/user")
    ExchangeUserSettings getUserSettings() throws RaplaException;
}
