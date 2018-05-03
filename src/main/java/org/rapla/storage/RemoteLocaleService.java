package org.rapla.storage;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.scheduler.Promise;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.Set;

@Path("locale")
public interface RemoteLocaleService
{
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("{id}")
    Promise<LocalePackage> locale(@PathParam("id") String id, @QueryParam("locale") String locale);

    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    Promise<Map<String, Set<String>>> countries( Set<String> languages);
}
