package org.rapla.plugin.export2ical;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

@Path("ical/config")
public interface ICalConfigService
{
    @GET
    DefaultConfiguration getConfig() throws RaplaException;

    @GET
    @Path("default")
    DefaultConfiguration getUserDefaultConfig() throws RaplaException;
}