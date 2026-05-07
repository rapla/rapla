package org.rapla.plugin.exchangeconnector;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;

@Path("exchange/config")
public interface ExchangeConnectorConfigRemote
{
    @GET
    @Path("default")
    DefaultConfiguration getConfig() throws RaplaException;

    @GET
    @Path("timezones")
    List<String> getTimezones() throws RaplaException;

}