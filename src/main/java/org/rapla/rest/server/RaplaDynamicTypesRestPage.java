package org.rapla.rest.server;

import org.rapla.entities.User;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import java.util.ArrayList;
import java.util.List;

@Path("dynamictypes")
public class RaplaDynamicTypesRestPage
{
    @Inject
    RemoteSession session;
    @Inject
    RaplaFacade facade;
    private final HttpServletRequest request;
    @Inject
    public RaplaDynamicTypesRestPage(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @GET
    public List<DynamicTypeImpl> list(@QueryParam("classificationType") String classificationType) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        DynamicType[] types = facade.getDynamicTypes(classificationType);
        List<DynamicTypeImpl> result = new ArrayList<>();
        final PermissionController controller  =   facade.getPermissionController();
        for (DynamicType type : types)
        {
            if ( controller.canRead( type, user))
            {
                result.add((DynamicTypeImpl) type);
            }
        }
        return result;
    }

}
