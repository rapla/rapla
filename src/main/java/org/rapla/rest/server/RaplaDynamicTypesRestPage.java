package org.rapla.rest.server;

import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Path("dynamictypes")
@RemoteJsonMethod
public class RaplaDynamicTypesRestPage extends AbstractRestPage
{
    @Inject
    public RaplaDynamicTypesRestPage(ClientFacade facade, Logger logger) throws RaplaException
    {
        super(facade, logger, true);
    }

    @GET
    public List<DynamicTypeImpl> list(@QueryParam("classificationType") String classificationType) throws RaplaException
    {
        DynamicType[] types = getQuery().getDynamicTypes(classificationType);
        List<DynamicTypeImpl> result = new ArrayList<DynamicTypeImpl>();
        for (DynamicType r : types)
        {
            result.add((DynamicTypeImpl) r);
        }
        return result;
    }

}
