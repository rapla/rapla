package org.rapla.rest.server;

import com.sun.deploy.util.SessionState;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.server.RemoteSession;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.List;

@Path("dynamictypes")
public class RaplaDynamicTypesRestPage
{
    final User user;
    RaplaFacade facade;
    @Inject
    public RaplaDynamicTypesRestPage(RaplaFacade facade,RemoteSession session) throws RaplaException
    {
        this.facade = facade;
        user = session.getUser();
    }

    @GET
    public List<DynamicTypeImpl> list(@QueryParam("classificationType") String classificationType) throws RaplaException
    {
        DynamicType[] types = facade.getDynamicTypes(classificationType);
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
