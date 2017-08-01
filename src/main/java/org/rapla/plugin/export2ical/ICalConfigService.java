package org.rapla.plugin.export2ical;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("ical/config")
public interface ICalConfigService
{
    @GET
    DefaultConfiguration getConfig() throws RaplaException;

    @GET
    @Path("default")
    DefaultConfiguration getUserDefaultConfig() throws RaplaException;
}