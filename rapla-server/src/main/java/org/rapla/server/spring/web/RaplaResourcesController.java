package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.rest.RaplaResourcesService;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.SecurityManager;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class RaplaResourcesController implements RaplaResourcesService
{
    private static final Collection<String> CLASSIFICATION_TYPES = Arrays.asList(
            DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,
            DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);

    private final RaplaFacade facade;
    private final StorageOperator operator;
    private final RemoteSession session;
    private final SecurityManager securityManager;
    private final HttpServletRequest request;

    public RaplaResourcesController(RaplaFacade facade,
                                    StorageOperator operator,
                                    RemoteSession session,
                                    SecurityManager securityManager,
                                    HttpServletRequest request)
    {
        this.facade = facade;
        this.operator = operator;
        this.session = session;
        this.securityManager = securityManager;
        this.request = request;
    }

    @Override
    public List<AllocatableImpl> list(List<String> resourceTypes, Map<String, String> attributeFilter) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        if (resourceTypes == null) resourceTypes = Collections.emptyList();
        if (attributeFilter == null) attributeFilter = Collections.emptyMap();
        ClassificationFilter[] filters = ClassificationFilterUtil.getClassificationFilter(
                facade, attributeFilter, CLASSIFICATION_TYPES, resourceTypes);
        Collection<Allocatable> resources = operator.getAllocatables(filters);
        List<AllocatableImpl> result = new ArrayList<>();
        PermissionController permissionController = facade.getPermissionController();
        for (Allocatable r : resources)
        {
            if (permissionController.canRead(r, user))
            {
                result.add((AllocatableImpl) r);
            }
        }
        return result;
    }

    @Override
    public AllocatableImpl get(String id) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        AllocatableImpl resource = (AllocatableImpl) operator.resolve(id, Allocatable.class);
        securityManager.checkRead(user, resource);
        return resource;
    }

    @Override
    public void delete(String id) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        AllocatableImpl resource = (AllocatableImpl) operator.resolve(id, Allocatable.class);
        securityManager.checkDeletePermissions(user, resource);
        Collection<ReferenceInfo<Allocatable>> removeObjects = Collections.singleton(resource.getReference());
        List<Allocatable> storeObjects = Collections.emptyList();
        operator.storeAndRemove(storeObjects, removeObjects, user, false);
    }

    @Override
    public AllocatableImpl update(AllocatableImpl resource) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        securityManager.checkWritePermissions(user, resource);
        PermissionController permissionController = facade.getPermissionController();
        if (!permissionController.canModify(resource, user))
        {
            throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
        }
        resource.setResolver(operator);
        securityManager.checkWritePermissions(user, resource);
        facade.store(resource);
        return facade.getPersistent(resource);
    }

    @Override
    public AllocatableImpl create(AllocatableImpl resource) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        resource.setResolver(operator);
        Classification classification = resource.getClassification();
        DynamicType type = classification.getType();
        if (!facade.getPermissionController().canCreate(type, user))
        {
            throw new RaplaSecurityException("User " + user + " can't modify  " + resource);
        }
        if (resource.getId() != null)
        {
            throw new RaplaException("Id has to be null for new resources");
        }
        ReferenceInfo<Allocatable> resourceRef = operator.createIdentifier(Allocatable.class, 1).get(0);
        resource.setId(resourceRef.getId());
        resource.setResolver(operator);
        resource.setOwner(user);
        facade.storeAndRemove(new Entity[]{resource}, Entity.ENTITY_ARRAY, user);
        return facade.getPersistent(resource);
    }
}
