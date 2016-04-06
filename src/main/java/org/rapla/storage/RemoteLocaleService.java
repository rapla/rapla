package org.rapla.storage;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.framework.RaplaException;

import javax.ws.rs.GET;
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
    LocalePackage locale(@PathParam("id") String id, @QueryParam("locale") String locale) throws RaplaException;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    Map<String, Set<String>> countries(@QueryParam("languages") Set<String> languages) throws RaplaException;
}
