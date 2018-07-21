/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.storage;

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;

import javax.inject.Provider;
import java.util.*;

public class LocalCache implements EntityResolver
{
    Map<String, String> passwords = new HashMap<>();
    Map<String, Entity> entities;

    //Map<String,ConflictImpl> disabledConflicts = new HashMap<String,ConflictImpl>();
    Map<String,ReferenceInfo<Appointment>> disabledConflictApp1 = new HashMap<>();
    Map<String,ReferenceInfo<Appointment>> disabledConflictApp2 = new HashMap<>();
    Map<String, Date> conflictLastChanged = new HashMap<>();

    Map<String, DynamicTypeImpl> dynamicTypes;
    Map<String, UserImpl> users;
    Map<String, AllocatableImpl> resources;
    Map<String, ReservationImpl> reservations;
    Map<ReferenceInfo<Allocatable>, GraphNode> graph = new LinkedHashMap<>();

    private String clientUserId;
    private final PermissionController permissionController;

    public LocalCache(PermissionController permissionController)
    {
        this.permissionController = permissionController;
        entities = new HashMap<>();
        // top-level-entities
        reservations = new LinkedHashMap<>();
        users = new LinkedHashMap<>();
        resources = new LinkedHashMap<>();
        dynamicTypes = new LinkedHashMap<>();
        //initSuperCategory();
    }

    public String getClientUserId()
    {
        return clientUserId;
    }

    /** use this to prohibit reservations and preferences (except from system and current user) to be stored in the cache*/
    public void setClientUserId(String clientUserId)
    {
        this.clientUserId = clientUserId;
    }

    /** @return true if the entity has been removed and false if the entity was not found*/
    public boolean remove(Entity entity)
    {
        if (entity instanceof ParentEntity)
        {
            Collection<Entity> subEntities = ((ParentEntity) entity).getSubEntities();
            for (Entity child : subEntities)
            {
                remove(child);
            }
        }
        return removeWithId(entity.getReference());
    }

    /** WARNING child entities will not be removed if you use this method */
    public boolean removeWithId(ReferenceInfo info)
    {
        String entityId = info.getId();
        Class<? extends Entity> typeClass = info.getType();
        boolean bResult = true;
        bResult = entities.remove(entityId) != null;
        Map<String, ? extends Entity> entitySet = getMap(typeClass);
        if (entitySet != null)
        {
            if (entityId == null)
                return false;
            entitySet.remove(entityId);
        }
        else if (typeClass == Conflict.class)
        {
            disabledConflictApp1.remove(entityId);
            disabledConflictApp2.remove(entityId);
        }
        if (typeClass == Allocatable.class)
        {
            GraphNode oldNode = graph.get(info);
            if (oldNode != null)
            {
                graph.remove(info);
                boolean onlyOutgoing = false;
                oldNode.removeConnections(onlyOutgoing);
            }
        }
        return bResult;
    }

    @SuppressWarnings("unchecked") private Map<String, Entity> getMap(Class<? extends Entity> type)
    {
        if (type == Reservation.class)
        {
            return (Map) reservations;
        }
        if (type == Allocatable.class)
        {
            return (Map) resources;
        }
        if (type == DynamicType.class)
        {
            return (Map) dynamicTypes;
        }
        if (type == User.class)
        {
            return (Map) users;
        }
        return null;
    }

    public void put(Entity entity)
    {
        Assert.notNull(entity);

        Class<? extends Entity> typeClass = entity.getTypeClass();

        String entityId = entity.getId();
        if (entityId == null)
            throw new IllegalStateException("ID can't be null");

        String clientUserId = getClientUserId();
        if (clientUserId != null)
        {
            if (typeClass == Reservation.class || typeClass == Appointment.class)
            {
                throw new IllegalArgumentException("Can't store reservations, appointments or conflicts in client cache");
            }
            // we ignore client stores for now
            if (typeClass == Conflict.class)
            {
                return;
            }
            if (typeClass == Preferences.class)
            {
                String owner = ((PreferencesImpl) entity).getId("owner");
                if (owner != null && !owner.equals(clientUserId))
                {
                    throw new IllegalArgumentException("Can't store non system preferences for other users in client cache");
                }
            }
        }

        if (entity.getTypeClass() == Allocatable.class)
        {
            updateDependencies(entity);
        }
        // first remove the old children from the map
        Entity oldEntity = entities.get(entity);
        if (oldEntity != null && oldEntity instanceof ParentEntity)
        {
            Collection<Entity> subEntities = ((ParentEntity) oldEntity).getSubEntities();
            for (Entity child : subEntities)
            {
                remove(child);
            }
        }

        entities.put(entityId, entity);
        Map<String, Entity> entitySet = getMap(typeClass);
        if (entitySet != null)
        {
            entitySet.put(entityId, entity);
        }
        else if (entity instanceof Conflict)
        {
            Conflict conflict = (Conflict) entity;
            if (conflict.isAppointment1Enabled())
            {
                disabledConflictApp1.remove(entityId);
            }
            else
            {
                disabledConflictApp1.put(entityId, conflict.getAppointment1());
            }
            if (conflict.isAppointment2Enabled())
            {
                disabledConflictApp2.remove(entityId);
            }
            else
            {
                disabledConflictApp2.put(entityId, conflict.getAppointment2());
            }
            final Date lastChanged = conflict.getLastChanged();
            conflictLastChanged.put(entityId, lastChanged);
            if (conflict.isAppointment1Enabled() && conflict.isAppointment2Enabled())
            {
                conflictLastChanged.remove(entityId);
            }
        }
        else
        {
            //throw new RuntimeException("UNKNOWN TYPE. Can't store object in cache: " + entity.getRaplaType());
        }
        // then put the new children
        if (entity instanceof ParentEntity)
        {
            Collection<Entity> subEntities = ((ParentEntity) entity).getSubEntities();
            for (Entity child : subEntities)
            {
                put(child);
            }
        }
    }

    public Entity get(Comparable id)
    {
        if (id == null)
            throw new RuntimeException("id is null");
        return entities.get(id);
    }

    //    @SuppressWarnings("unchecked")
    //    private <T extends Entity> Collection<T> getCollection(RaplaType type) {
    //        Map<String,? extends Entity> entities =  entityMap.get(type);
    //
    //        if (entities != null) {
    //            return (Collection<T>) entities.values();
    //        } else {
    //            throw new RuntimeException("UNKNOWN TYPE. Can't get collection: "
    //                                       +  type);
    //        }
    //    }
    //
    //    @SuppressWarnings("unchecked")
    //    private <T extends RaplaObject> Collection<T> getCollection(Class<T> clazz) {
    //    	RaplaType type = RaplaType.get(clazz);
    //		Collection<T> collection = (Collection<T>) getCollection(type);
    //		return new LinkedHashSet(collection);
    //    }

    public void clearAll()
    {
        passwords.clear();
        reservations.clear();
        users.clear();
        resources.clear();
        dynamicTypes.clear();
        entities.clear();
        disabledConflictApp1.clear();
        disabledConflictApp2.clear();
        conflictLastChanged.clear();
        graph.clear();
    }

    public CategoryImpl getSuperCategory()
    {
        return (CategoryImpl) get(Category.SUPER_CATEGORY_REF.getId());
    }

    public UserImpl getUser(String username)
    {
        for (UserImpl user : users.values())
        {
            if (user.getUsername().equals(username))
                return user;
        }
        for (UserImpl user : users.values())
        {
            if (user.getUsername().equalsIgnoreCase(username))
                return user;
        }
        return null;
    }

    public PreferencesImpl getPreferencesForUserId(String userId)
    {
        ReferenceInfo<Preferences> preferenceId = PreferencesImpl.getPreferenceIdFromUser(userId);
        PreferencesImpl pref = (PreferencesImpl) tryResolve(preferenceId);
        return pref;
    }

    public DynamicType getDynamicType(String elementKey)
    {
        for (DynamicType dt : dynamicTypes.values())
        {
            if (dt.getKey().equals(elementKey))
                return dt;
        }
        return null;
    }

    public List<Entity> getVisibleEntities(final User forUser)
    {
        List<Entity> result = new ArrayList<>();
        final CategoryImpl superCategory = getSuperCategory();
        result.addAll(CategoryImpl.getRecursive(superCategory));
        result.addAll(getDynamicTypes());
        final Collection<Category> adminGroups = forUser != null && !forUser.isAdmin() ?  PermissionController.getGroupsToAdmin(forUser) : Collections.emptyList();
        for (User user : getUsers())
        {
            boolean add = forUser == null || forUser.isAdmin() || forUser.getId().equals(user.getId());
            if (!add && !user.isAdmin())
            {
                for (Category adminGroup : adminGroups)
                {
                    if (((UserImpl) user).isMemberOf(adminGroup) )
                    {
                        add = true;
                        break;
                    }
                }
            }
            if (add)
            {
                result.add(user);
            }
        }
        for (Allocatable alloc : getAllocatables())
        {
            if (forUser == null || forUser.isAdmin() || permissionController.canReadOnlyInformation(alloc, forUser))
            {
                result.add(alloc);
            }
        }
        // add system preferences
        {
            PreferencesImpl preferences = getPreferencesForUserId(null);
            if (preferences != null)
            {
                result.add(preferences);
            }
        }
        // add forUser preferences
        if (forUser != null)
        {
            String userId = forUser.getId();
            Assert.notNull(userId);
            PreferencesImpl preferences = getPreferencesForUserId(userId);
            if (preferences != null)
            {
                result.add(preferences);
            }
        }
        return result;
    }

    @Override public <T extends Entity> T tryResolve(ReferenceInfo<T> referenceInfo)
    {
        final Class<T> type = (Class<T>) referenceInfo.getType();
        return tryResolve(referenceInfo.getId(), type);
    }

    @Override public <T extends Entity> T resolve(ReferenceInfo<T> referenceInfo) throws EntityNotFoundException
    {
        final Class<T> type = (Class<T>) referenceInfo.getType();
        return resolve(referenceInfo.getId(), type);
    }

    public <T extends Entity> T resolve(String id, Class<T> entityClass) throws EntityNotFoundException
    {
        T entity = tryResolve(id, entityClass);
        SimpleEntity.checkResolveResult(id, entityClass, entity);
        return entity;
    }

    @Override public <T extends Entity> T tryResolve(String id, Class<T> entityClass)
    {
        if (id == null)
            throw new RuntimeException("id is null");
        Entity entity = entities.get(id);
        @SuppressWarnings("unchecked") T casted = (T) entity;
        return casted;
    }

    public String getPassword(ReferenceInfo<User> userReferenceInfo)
    {
        return passwords.get(userReferenceInfo.getId());
    }

    public void putPassword(ReferenceInfo<User> userReferenceInfo, String password)
    {
        passwords.put(userReferenceInfo.getId(), password);
    }

    public void putAll(Collection<? extends Entity> list)
    {
        for (Entity entity : list)
        {
            put(entity);
        }
    }

    public Provider<Category> getSuperCategoryProvider()
    {
        return () -> getSuperCategory();
    }

    @SuppressWarnings("unchecked") public Collection<User> getUsers()
    {
        return (Collection) users.values();
    }

    public Conflict fillConflictDisableInformation(User user, Conflict orig)
    {
        ConflictImpl conflict = (ConflictImpl) orig.clone();
        String id = conflict.getId();
        conflict.setAppointment1Enabled(!disabledConflictApp1.containsKey(id));
        conflict.setAppointment2Enabled(!disabledConflictApp2.containsKey(id));
        Date lastChangedInCache = conflictLastChanged.get(id);
        Date origLastChanged = conflict.getLastChanged();

        Date lastChanged = origLastChanged;
        if (lastChanged == null || (lastChangedInCache != null && lastChangedInCache.after(lastChanged)))
        {
            lastChanged = lastChangedInCache;
        }
        if (lastChanged == null)
        {
            lastChanged = new Date();
        }
        conflict.setLastChanged(lastChanged);
        EntityResolver cache = this;
        if (user != null)
        {

            final ReferenceInfo<Reservation> reservation1Id = conflict.getReservation1();
            final ReferenceInfo<Reservation> reservation2Id = conflict.getReservation2();
            Reservation reservation1 = tryResolve(reservation1Id);
            Reservation reservation2 = tryResolve(reservation2Id);
            final boolean appointment1Editable = reservation1 != null && permissionController.canModify(reservation1, user);
            conflict.setAppointment1Editable(appointment1Editable);
            final boolean appointment2Editable = reservation2 != null && permissionController.canModify(reservation2, user);
            conflict.setAppointment2Editable(appointment2Editable);
        }
        return conflict;
    }

    @SuppressWarnings("unchecked") public Collection<String> getDisabledConflictIds()
    {
        final HashSet result = new HashSet(disabledConflictApp1.keySet());
        result.addAll(disabledConflictApp2.keySet());
        return result;
    }

    @SuppressWarnings("unchecked") public Collection<Allocatable> getAllocatables()
    {
        return (Collection) resources.values();
    }

    @SuppressWarnings("unchecked") public Collection<Reservation> getReservations()
    {
        return (Collection) reservations.values();
    }

    @SuppressWarnings("unchecked") public Collection<DynamicType> getDynamicTypes()
    {
        return (Collection) dynamicTypes.values();
    }

    public Collection<Conflict> getDisabledConflicts()
    {
        List<Conflict> disabled = new ArrayList<>();
        for (String conflictId : getDisabledConflictIds())
        {
            Date lastChanged = conflictLastChanged.get(conflictId);
            if (lastChanged == null)
            {
                lastChanged = new Date();
            }
            Conflict conflict;
            try
            {
                conflict = new ConflictImpl(conflictId, lastChanged, lastChanged);
            }
            catch (RaplaException ex)
            {
                continue;
            }
            Conflict conflictClone = fillConflictDisableInformation(null, conflict);
            disabled.add(conflictClone);
        }
        return disabled;
    }

    static class GraphNode
    {
        private final ReferenceInfo<Allocatable> alloc;

        public GraphNode(ReferenceInfo<Allocatable> alloc)
        {
            this.alloc = alloc;
        }

        public void removeConnection(GraphNode node)
        {
            connections.remove(node);
        }

        public void addConnection(GraphNode node, ConnectionType type)
        {
            connections.put(node, type);
        }

        public void removeConnections(boolean onlyOutgoing)
        {
            for (Map.Entry<GraphNode, ConnectionType> entry : connections.entrySet())
            {
                GraphNode connection = entry.getKey();
                ConnectionType connectionType = entry.getValue();
                if ( !onlyOutgoing || connectionType.isOutgoing())
                {
                    connection.removeConnection(this);
                }
            }
        }

        enum ConnectionType
        {
            BelongsTo,
            BelongsToTarget,
            Packages,
            PackagesTarget;

            public ConnectionType getOpposite()
            {
                switch ( this)
                {
                    case BelongsTo: return BelongsToTarget;
                    case BelongsToTarget: return BelongsTo;
                    case Packages: return PackagesTarget;
                    case PackagesTarget: return Packages;
                }
                throw new IllegalStateException("ConnectionType not found in case");
            }

            public boolean isOutgoing()
            {
                return this == Packages || this == BelongsTo;
            }
        }

        Map<GraphNode, ConnectionType> connections = new LinkedHashMap<>();

        @Override public String toString()
        {
            StringBuilder builder = new StringBuilder();
            builder.append(alloc.getId());
            builder.append(" ");
            for (Map.Entry<GraphNode, ConnectionType> entry : connections.entrySet())
            {
                builder.append(entry.getValue() + " " + entry.getKey().alloc.getId());
            }
            return builder.toString();
        }
    }

    private void updateDependencies(Entity entity)
    {
        if (entity instanceof Allocatable)
        {
            Allocatable alloc = (Allocatable) entity;
            ReferenceInfo<Allocatable> ref = alloc.getReference();
            GraphNode oldNode = graph.get(ref);
            if (oldNode != null)
            {
                boolean onlyOutgoing = true;
                oldNode.removeConnections(onlyOutgoing);
            }
            final ClassificationImpl classification = (ClassificationImpl)alloc.getClassification();
            final DynamicTypeImpl type = classification.getType();
            addConnection(ref, classification, type.getBelongsToAttribute(), GraphNode.ConnectionType.BelongsTo);
            addConnection(ref, classification, type.getPackagesAttribute(), GraphNode.ConnectionType.Packages);
        }
    }

    private void addConnection(ReferenceInfo<Allocatable> ref, ClassificationImpl classification, Attribute attribute, GraphNode.ConnectionType sourceType)
    {
        if ( attribute == null)
        {
            return;
        }
        if (attribute != null )
        {
            final Collection<String> valuesUnresolvedStrings = classification.getValuesUnresolvedStrings(attribute);
            if ( valuesUnresolvedStrings != null)
            {
                for (String id : valuesUnresolvedStrings)
                {
                    if (id != null)
                    {
                        ReferenceInfo<Allocatable> targetReference = new ReferenceInfo<>(id, Allocatable.class);
                        final GraphNode node = getOrCreate(ref);
                        final GraphNode targetNode = getOrCreate(targetReference);
                        node.addConnection(targetNode, sourceType);
                        targetNode.addConnection(node, sourceType.getOpposite());
                    }
                }
            }
        }
    }

    private GraphNode getOrCreate(ReferenceInfo<Allocatable> allocatable)
    {
        GraphNode graphNode = graph.get(allocatable);
        if (graphNode == null)
        {
            graphNode = new GraphNode(allocatable);
            graph.put(allocatable, graphNode);
        }
        return graphNode;
    }


    public Set<ReferenceInfo<Allocatable>> getDependentRef(ReferenceInfo<Allocatable> allocatableRef)
    {
        Set<ReferenceInfo<Allocatable>> allocatableIds = new LinkedHashSet<>();
        if (allocatableRef != null)
        {
            fillDependent(allocatableIds, allocatableRef);
        }
        return allocatableIds;
    }

    public Set<ReferenceInfo<Allocatable>> getDependent(final Collection<Allocatable> allocatables)
    {
        Set<ReferenceInfo<Allocatable>> allocatableIds = new LinkedHashSet<>();
        for (Allocatable allocatable : allocatables)
        {
            ReferenceInfo<Allocatable> allocatableRef = allocatable.getReference();
            fillDependent(allocatableIds, allocatableRef);
        }
        return allocatableIds;
    }

    private void fillDependent(Set<ReferenceInfo<Allocatable>> allocatableIds, ReferenceInfo<Allocatable> allocatableRef)
    {
        GraphNode node = graph.get(allocatableRef);
        if (node != null)
        {
            fillDependent(node, allocatableIds, 0, true);
        }
        else
        {
            allocatableIds.add( allocatableRef);
        }
    }

    private void fillDependent(GraphNode node, final Set<ReferenceInfo<Allocatable>> dependentAllocatables, final int depth, boolean goDown)
    {
        if (depth > 20)
        {
            throw new IllegalStateException("Cycle in dependencies detected");
        }
        if (!dependentAllocatables.add(node.alloc))
        {
            return;
        }
        for (Map.Entry<GraphNode, GraphNode.ConnectionType> entry : node.connections.entrySet())
        {
            GraphNode.ConnectionType type = entry.getValue();
            GraphNode connectionNode = entry.getKey();
            if (goDown && (type == GraphNode.ConnectionType.Packages || type == GraphNode.ConnectionType.BelongsToTarget))
            {
                fillDependent(connectionNode, dependentAllocatables, depth + 1, true);
            }
            if (type == GraphNode.ConnectionType.PackagesTarget || type == GraphNode.ConnectionType.BelongsTo)
            {
                fillDependent(connectionNode, dependentAllocatables, depth + 1, false);
            }
        }
    }

}
