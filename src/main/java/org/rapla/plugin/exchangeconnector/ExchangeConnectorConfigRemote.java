package org.rapla.plugin.exchangeconnector;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

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