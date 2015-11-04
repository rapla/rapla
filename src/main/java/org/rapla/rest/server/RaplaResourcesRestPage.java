package org.rapla.rest.server;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.jws.WebParam;
import javax.ws.rs.*;
import java.util.*;

@Path("resources")
@Singleton
@RemoteJsonMethod
public class RaplaResourcesRestPage extends AbstractRestPage {

	private Collection<String> CLASSIFICATION_TYPES = Arrays.asList(new String[] { DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,
			DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON });
    private final PermissionController permissionController;

	@Inject
	public RaplaResourcesRestPage(ClientFacade facade, Logger logger, PermissionController permissionController) throws RaplaException {
		super(facade, logger, true);
        this.permissionController = permissionController;
	}

	@GET
	public List<AllocatableImpl> list(@QueryParam("user") User user, @QueryParam("resourceTypes") List<String> resourceTypes,
			@WebParam(name = "attributeFilter") Map<String, String> simpleFilter) throws RaplaException {
		ClassificationFilter[] filters = getClassificationFilter(simpleFilter, CLASSIFICATION_TYPES, resourceTypes);
		Collection<Allocatable> resources = operator.getAllocatables(filters);
		List<AllocatableImpl> result = new ArrayList<AllocatableImpl>();
		for (Allocatable r : resources) {
			if (permissionController.canRead(r, user, getEntityResolver())) {
				result.add((AllocatableImpl) r);
			}
		}
		return result;
	}

	@GET
	@Path("{id}")
	public AllocatableImpl get(@QueryParam("user") User user, @PathParam("id") String id) throws RaplaException {
		AllocatableImpl resource = (AllocatableImpl) operator.resolve(id, Allocatable.class);
		if (!permissionController.canRead(resource, user, getEntityResolver())) {
			throw new RaplaSecurityException("User " + user + " can't read  " + resource);
		}
		return resource;
	}

	@PUT
	public AllocatableImpl update(@QueryParam("user") User user, AllocatableImpl resource) throws RaplaException {
		if (!permissionController.canModify(resource, user, getEntityResolver())) {
			throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
		}
		resource.setResolver(operator);
		getModification().store(resource);
		AllocatableImpl result = (AllocatableImpl) getModification().getPersistant(resource);
		return result;
	}

	@POST
	public AllocatableImpl create(@QueryParam("user") User user, AllocatableImpl resource) throws RaplaException {
		resource.setResolver(operator);
		Classification classification = resource.getClassification();
		DynamicType type = classification.getType();
		if (!getQuery().canCreateReservations(type, user)) {
			throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
		}
		if (resource.getId() != null) {
			throw new RaplaException("Id has to be null for new resources");
		}
		String eventId = operator.createIdentifier(Allocatable.TYPE, 1)[0];
		resource.setId(eventId);
		resource.setResolver(operator);
		resource.setCreateDate(operator.getCurrentTimestamp());
		resource.setOwner(user);
		getModification().store(resource);
		AllocatableImpl result = (AllocatableImpl) getModification().getPersistant(resource);
		return result;
	}

}
