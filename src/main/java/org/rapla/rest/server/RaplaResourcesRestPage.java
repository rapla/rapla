package org.rapla.rest.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.jws.WebParam;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.SecurityManager;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;

@Path("resources")
public class RaplaResourcesRestPage  {

	private Collection<String> CLASSIFICATION_TYPES = Arrays.asList(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,
			DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);

	@Inject
	RaplaFacade facade;
	@Inject
	StorageOperator operator;
	@Inject
	RemoteSession session;
	@Inject
	SecurityManager securityManager;

    private final HttpServletRequest request;

	@Inject
	public RaplaResourcesRestPage(@Context HttpServletRequest request) {
        this.request = request;
	}

	public static ClassificationFilter[] getClassificationFilter(RaplaFacade facade,Map<String, String> simpleFilter, Collection<String> selectedClassificationTypes,
            Collection<String> typeNames) throws RaplaException
    {
        ClassificationFilter[] filters = null;
        if (simpleFilter == null && typeNames == null)
        {
            return null;
        }
        {
            DynamicType[] types = facade.getDynamicTypes(null);
            List<ClassificationFilter> filterList = new ArrayList<ClassificationFilter>();
            for (DynamicType type : types)
            {
                String classificationType = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                if (classificationType == null || !selectedClassificationTypes.contains(classificationType))
                {
                    continue;
                }
                ClassificationFilter classificationFilter = type.newClassificationFilter();
                if (typeNames != null)
                {
                    if (!typeNames.contains(type.getKey()))
                    {
                        continue;
                    }
                }
                if (simpleFilter != null)
                {
                    for (String key : simpleFilter.keySet())
                    {
                        Attribute att = type.getAttribute(key);
                        if (att != null)
                        {
                            String value = simpleFilter.get(key);
                            // convert from string to attribute type
                            Object object = att.convertValue(value);
                            if (object != null)
                            {
                                classificationFilter.addEqualsRule(att.getKey(), object);
                            }
                            filterList.add(classificationFilter);
                        }
                    }
                }
                else
                {
                    filterList.add(classificationFilter);
                }
            }
            filters = filterList.toArray(new ClassificationFilter[] {});
        }
        return filters;
    }

	@GET
	public List<AllocatableImpl> list( @QueryParam("resourceTypes") Collection<String> resourceTypes,
			@QueryParam("attributeFilter") Map<String, String> simpleFilter) throws RaplaException {
	    final User user = session.getUser(request);
		ClassificationFilter[] filters = getClassificationFilter(facade, simpleFilter, CLASSIFICATION_TYPES, resourceTypes);
		Collection<Allocatable> resources = operator.getAllocatables(filters);
		List<AllocatableImpl> result = new ArrayList<AllocatableImpl>();
		PermissionController permissionController = facade.getPermissionController();
		for (Allocatable r : resources) {
			if (permissionController.canRead(r, user)) {
				result.add((AllocatableImpl) r);
			}
		}
		return result;
	}

	@GET
	@Path("{id}")
	public AllocatableImpl get( @PathParam("id") String id) throws RaplaException {
        final User user = session.getUser(request);
		AllocatableImpl resource = (AllocatableImpl) operator.resolve(id, Allocatable.class);
		securityManager.checkRead( user, resource);
		return resource;
	}

	@DELETE
	@Path("{id}")
	public void delete( @PathParam("id") String id) throws RaplaException {
		final User user = session.getUser(request);
		AllocatableImpl resource = (AllocatableImpl) operator.resolve(id, Allocatable.class);
		securityManager.checkDeletePermissions(user, resource);
		Collection<ReferenceInfo<Allocatable>> removeObjects = Collections.singleton(resource.getReference());
		List<Allocatable> storeObjects = Collections.emptyList();
		operator.storeAndRemove(storeObjects, removeObjects, user);
	}

	@PUT
	public AllocatableImpl update( AllocatableImpl resource) throws RaplaException {
        final User user = session.getUser(request);
		securityManager.checkWritePermissions(user, resource);
		PermissionController permissionController = facade.getPermissionController();
		if (!permissionController.canModify(resource, user)) {
			throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
		}
		resource.setResolver(operator);
		securityManager.checkWritePermissions( user, resource);
		facade.store(resource);
		AllocatableImpl result = facade.getPersistant(resource);
		return result;
	}

	@POST
	public AllocatableImpl create(AllocatableImpl resource) throws RaplaException {
        final User user = session.getUser(request);
		resource.setResolver(operator);
		Classification classification = resource.getClassification();
		DynamicType type = classification.getType();
		if (!facade.getPermissionController().canCreate(type, user)) {
			throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
		}
		if (resource.getId() != null) {
			throw new RaplaException("Id has to be null for new resources");
		}
		ReferenceInfo<Allocatable> resourceRef = operator.createIdentifier(Allocatable.class, 1)[0];
		resource.setId(resourceRef.getId());
		resource.setResolver(operator);
		resource.setOwner(user);
		facade.storeAndRemove(new Entity[]{resource},Entity.ENTITY_ARRAY, user);
		AllocatableImpl result = facade.getPersistant(resource);
		return result;
	}

}
