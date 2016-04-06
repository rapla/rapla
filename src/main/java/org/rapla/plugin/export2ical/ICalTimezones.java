package org.rapla.plugin.export2ical;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("ical/timezones")
public interface ICalTimezones 
{
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    List<String> getICalTimezones();

    @GET
    @Path("default")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    String getDefaultTimezone();
}