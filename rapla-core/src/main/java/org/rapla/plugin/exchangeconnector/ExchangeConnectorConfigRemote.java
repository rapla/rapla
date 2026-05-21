package org.rapla.plugin.exchangeconnector;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.RaplaException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/api/exchange/config")
public interface ExchangeConnectorConfigRemote
{
    @GetExchange("/default")
    RaplaConfiguration getConfig() throws RaplaException;

    @GetExchange("/timezones")
    List<String> getTimezones() throws RaplaException;

    @GetExchange("/user")
    ExchangeUserSettings getUserSettings() throws RaplaException;
}
