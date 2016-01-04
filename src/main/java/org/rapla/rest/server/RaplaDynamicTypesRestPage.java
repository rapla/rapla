package org.rapla.rest.server;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.rapla.entities.User;
import org.rapla.storage.PermissionController;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.server.RemoteSession;

@Path("dynamictypes")
@RemoteJsonMethod
public class RaplaDynamicTypesRestPage extends AbstractRestPage
{
    final User user;
    @Inject
    public RaplaDynamicTypesRestPage(ClientFacade facade,RemoteSession session) throws RaplaException
    {
        super(facade);
        user = session.getUser();
    }

    @GET
    public List<DynamicTypeImpl> list(@QueryParam("classificationType") String classificationType) throws RaplaException
    {
        DynamicType[] types = getQuery().getDynamicTypes(classificationType);
        List<DynamicTypeImpl> result = new ArrayList<DynamicTypeImpl>();
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
