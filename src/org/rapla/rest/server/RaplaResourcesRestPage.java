package org.rapla.rest.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.servletpages.RaplaPageGenerator;
import org.rapla.storage.RaplaSecurityException;

@WebService
public class RaplaResourcesRestPage extends AbstractRestPage implements RaplaPageGenerator
{
    private Collection<String> CLASSIFICATION_TYPES = Arrays.asList(new String[] {DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON});

    public RaplaResourcesRestPage(RaplaContext context) throws RaplaException 
    {
        super(context);
    }
    
    public List<AllocatableImpl> list(@WebParam(name="user") User user,@WebParam(name="resourceTypes") List<String> resourceTypes, @WebParam(name="attributeFilter") Map<String,String> simpleFilter ) throws RaplaException
    {
        ClassificationFilter[] filters = getClassificationFilter(simpleFilter, CLASSIFICATION_TYPES, resourceTypes);
        Collection<Allocatable> resources = operator.getAllocatables(filters);
        List<AllocatableImpl> result = new ArrayList<AllocatableImpl>();
        for ( Allocatable r:resources)
        {
            if ( canRead(r, user, getEntityResolver()))
            {
                result.add((AllocatableImpl) r);
            }
        }
        return result;
    }

    
    public AllocatableImpl get(@WebParam(name="user") User user, @WebParam(name="id")String id) throws RaplaException
    {
        AllocatableImpl resource = (AllocatableImpl) operator.resolve(id, Allocatable.class);
        if (!canRead(resource, user, getEntityResolver()))
        {
            throw new RaplaSecurityException("User " + user + " can't read  " + resource);
        }
        return resource;
    }
    
    public AllocatableImpl update(@WebParam(name="user") User user, AllocatableImpl resource) throws RaplaException
    {
        if (!canModify(resource, user, getEntityResolver()))
        {
            throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
        }
        resource.setResolver( operator);
        getModification().store( resource );
        AllocatableImpl result =(AllocatableImpl) getModification().getPersistant( resource);
        return result;
    }
    
    public AllocatableImpl create(@WebParam(name="user") User user, AllocatableImpl resource) throws RaplaException
    {
        resource.setResolver( operator);
        Classification classification = resource.getClassification();
        DynamicType type = classification.getType();
        if (!getQuery().canCreateReservations(type, user))
        {
            throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
        }
        if (resource.getId() != null)
        {
            throw new RaplaException("Id has to be null for new resources");
        }
        String eventId = operator.createIdentifier(Allocatable.TYPE, 1)[0];
        resource.setId( eventId);
        resource.setResolver( operator);
        resource.setCreateDate( operator.getCurrentTimestamp());
        resource.setOwner( user );
        getModification().store( resource);
        AllocatableImpl result =(AllocatableImpl) getModification().getPersistant( resource);
        return result;
    }
   
}
