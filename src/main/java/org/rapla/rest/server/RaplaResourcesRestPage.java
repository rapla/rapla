package org.rapla.rest.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.jws.WebParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.storage.PermissionController;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

@Path("resources")
@RemoteJsonMethod
public class RaplaResourcesRestPage extends AbstractRestPage {

	private Collection<String> CLASSIFICATION_TYPES = Arrays.asList(new String[] { DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,
			DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON });
	private final User user;

	@Inject
	public RaplaResourcesRestPage(ClientFacade facade, RemoteSession session) throws RaplaException {
		super(facade);
		this.user = session.getUser();
	}

	@GET
	public List<AllocatableImpl> list( @QueryParam("resourceTypes") List<String> resourceTypes,
			@WebParam(name = "attributeFilter") Map<String, String> simpleFilter) throws RaplaException {
		ClassificationFilter[] filters = getClassificationFilter(simpleFilter, CLASSIFICATION_TYPES, resourceTypes);
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
		AllocatableImpl resource = (AllocatableImpl) operator.resolve(id, Allocatable.class);
		PermissionController permissionController = facade.getPermissionController();
		if (!permissionController.canRead(resource, user)) {
			throw new RaplaSecurityException("User " + user + " can't read  " + resource);
		}
		return resource;
	}

	@PUT
	public AllocatableImpl update( AllocatableImpl resource) throws RaplaException {
		PermissionController permissionController = facade.getPermissionController();
		if (!permissionController.canModify(resource, user)) {
			throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
		}
		resource.setResolver(operator);
		getModification().store(resource);
		AllocatableImpl result = (AllocatableImpl) getModification().getPersistant(resource);
		return result;
	}

	@POST
	public AllocatableImpl create(AllocatableImpl resource) throws RaplaException {
		resource.setResolver(operator);
		Classification classification = resource.getClassification();
		DynamicType type = classification.getType();
		if (!facade.getPermissionController().canCreate(type, user)) {
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
