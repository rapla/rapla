package org.rapla.storage;

import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.jsonrpc.common.FutureResult;

@Path("locale")
public interface RemoteLocaleService
{
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("{id}")
    FutureResult<LocalePackage> locale(@PathParam("id") String id, @QueryParam("locale") String locale);

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    FutureResult<Map<String, Set<String>>> countries(@QueryParam("languages") Set<String> languages);
}
