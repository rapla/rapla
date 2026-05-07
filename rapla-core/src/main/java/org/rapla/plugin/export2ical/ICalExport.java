package org.rapla.plugin.export2ical;

import org.rapla.framework.RaplaException;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Set;

@Path("ical/export")
public interface ICalExport {
    @POST
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	String export(Set<String> appointmentIds) throws RaplaException;
}