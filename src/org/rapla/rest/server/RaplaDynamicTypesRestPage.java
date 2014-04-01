package org.rapla.rest.server;

import java.util.ArrayList;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.servletpages.RaplaPageGenerator;

@WebService
public class RaplaDynamicTypesRestPage extends AbstractRestPage implements RaplaPageGenerator
{
    public RaplaDynamicTypesRestPage(RaplaContext context) throws RaplaException {
        super(context);
    }
    
    public List<DynamicTypeImpl> list(@WebParam(name="classificationType") String classificationType ) throws RaplaException
    {
        DynamicType[] types = getQuery().getDynamicTypes(classificationType);
        List<DynamicTypeImpl> result = new ArrayList<DynamicTypeImpl>();
        for ( DynamicType r:types)
        {
            result.add((DynamicTypeImpl) r);
        }
        return result;
    }

    
   
}
