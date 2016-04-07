package org.rapla.plugin.export2ical;

import java.util.Set;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.rapla.framework.RaplaException;

@Path("ical/export")
public interface ICalExport {
    @POST
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	String export(Set<String> appointmentIds) throws RaplaException;
}