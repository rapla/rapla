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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.Provider;

public class LocalCache implements EntityResolver
{
    Map<String,String> passwords = new HashMap<String,String>();
    Map<String,Entity> entities;
    
    Map<String,ConflictImpl> conflicts = new HashMap<String,ConflictImpl>();

    Map<String,DynamicTypeImpl> dynamicTypes;
    Map<String,UserImpl> users;
    Map<String,AllocatableImpl> resources;
    Map<String,ReservationImpl> reservations;
    
    private String clientUserId;
    public LocalCache() {
        entities = new HashMap<String,Entity>();
        // top-level-entities
        reservations = new LinkedHashMap<String,ReservationImpl>();
        users = new LinkedHashMap<String,UserImpl>();
        resources = new LinkedHashMap<String,AllocatableImpl>();
        dynamicTypes = new LinkedHashMap<String,DynamicTypeImpl>();
        initSuperCategory();
    }
    
	public String getClientUserId() {
        return clientUserId;
    }

	/** use this to prohibit reservations and preferences (except from system and current user) to be stored in the cache*/ 
    public void setClientUserId(String clientUserId) {
        this.clientUserId = clientUserId;
    }

    /** @return true if the entity has been removed and false if the entity was not found*/
    public boolean remove(Entity entity) {
        RaplaType raplaType = entity.getRaplaType();
        boolean bResult = true;
        String entityId = entity.getId();
        bResult = entities.remove(entityId) != null;
        Map<String,? extends Entity> entitySet = getMap(raplaType);
        if (entitySet != null) {
            if (entityId == null)
                return false;
            entitySet.remove( entityId );
        }
        if ( entity instanceof ParentEntity)
        {
            Collection<Entity> subEntities = ((ParentEntity) entity).getSubEntities();
            for (Entity child:subEntities)
            {
                remove( child);
            }
        }
        if ( entity instanceof Conflict)
        {
            conflicts.remove( entity.getId());
        }
        return bResult;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Entity> getMap(RaplaType type)
    {
        if ( type == Reservation.TYPE)
        {
            return (Map)reservations;
        }
        if ( type == Allocatable.TYPE)
        {
            return (Map)resources;
        }
        if ( type == Conflict.TYPE)
        {
            return (Map)conflicts;
        }
        if ( type == DynamicType.TYPE)
        {
            return (Map)dynamicTypes;
        }
        if ( type == User.TYPE)
        {
            return (Map)users;
        }
        return null;
    }
    
    public void put(Entity entity) {
        Assert.notNull(entity);
       
        RaplaType raplaType = entity.getRaplaType();
       
        String entityId = entity.getId();
        if (entityId == null)
            throw new IllegalStateException("ID can't be null");

        String clientUserId = getClientUserId();
        if ( clientUserId != null )
        {
            if (raplaType == Reservation.TYPE || raplaType == Appointment.TYPE )
            {
                throw new IllegalArgumentException("Can't store reservations, appointments or conflicts in client cache");
            }
            // we ignore client stores for now
            if ( raplaType == Conflict.TYPE)
            {
                return;
            }
            if (raplaType == Preferences.TYPE  )
            {
                String owner = ((PreferencesImpl)entity).getId("owner");
                if ( owner != null && !owner.equals( clientUserId))
                {
                    throw new IllegalArgumentException("Can't store non system preferences for other users in client cache"); 
                }
            }
        }
            
        // first remove the old children from the map
        Entity oldEntity = entities.get( entity);
        if (oldEntity != null && oldEntity instanceof ParentEntity)
        {
        	Collection<Entity> subEntities = ((ParentEntity) oldEntity).getSubEntities();
        	for (Entity child:subEntities)
        	{
        		remove( child);
        	}
        }
        
        entities.put(entityId,entity);
		Map<String,Entity> entitySet =  getMap(raplaType);
        if (entitySet != null) {
            boolean enabledForAll = false;
            if ( entity instanceof Conflict ) 
            {
                Conflict conflict = (Conflict) entity;
                enabledForAll = conflict.isAppointment1Enabled() && conflict.isAppointment2Enabled();
                if ( enabledForAll)
                {
                    conflicts.remove( entityId);
                }
                else
                {
                    conflicts.put( entityId, (ConflictImpl)conflict);
                }
            }
            if ( !enabledForAll)
            {
                entitySet.put( entityId ,entity);
            }
        } 
        else 
        {
            //throw new RuntimeException("UNKNOWN TYPE. Can't store object in cache: " + entity.getRaplaType());
        }
        // then put the new children
        if ( entity instanceof ParentEntity)
        {
        	Collection<Entity> subEntities = ((ParentEntity) entity).getSubEntities();
        	for (Entity child:subEntities)
        	{
        		put( child);
        	}
        }
    }


    public Entity get(Comparable id) {
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
	
    public void clearAll() {
        passwords.clear();
        reservations.clear();
        users.clear();
        resources.clear();
        dynamicTypes.clear();
        entities.clear();
        conflicts.clear();
        initSuperCategory();
    }
    
    private void initSuperCategory() {
    	CategoryImpl superCategory = new CategoryImpl(null, null);
        superCategory.setId(Category.SUPER_CATEGORY_ID);
        superCategory.setKey("supercategory");
        superCategory.getName().setName("en", "Root");
        entities.put (Category.SUPER_CATEGORY_ID, superCategory);
        Category[] childs = superCategory.getCategories();
        for (int i=0;i<childs.length;i++) {
            superCategory.removeCategory( childs[i] );
        }
    }

    public CategoryImpl getSuperCategory() 
    {
        return (CategoryImpl) get(Category.SUPER_CATEGORY_ID);
    }

    public UserImpl getUser(String username) {
        for (UserImpl user:users.values())
        {
            if (user.getUsername().equals(username))
                return user;
        }
        for (UserImpl user:users.values())
        {
            if (user.getUsername().equalsIgnoreCase(username))
                return user;
        }
        return null;
    }

    public PreferencesImpl getPreferencesForUserId(String userId) {
		String preferenceId = PreferencesImpl.getPreferenceIdFromUser(userId);
		PreferencesImpl pref = (PreferencesImpl) tryResolve( preferenceId, Preferences.class);
		return pref; 	
    }
    
   
    public DynamicType getDynamicType(String elementKey) {
        for (DynamicType dt:dynamicTypes.values()) {
            if (dt.getKey().equals(elementKey))
                return dt;
        }
        return null;
    }

    public List<Entity> getVisibleEntities(final User user) {
        List<Entity> result = new ArrayList<Entity>();
        result.add( getSuperCategory());
        result.addAll(getDynamicTypes());
        result.addAll(getUsers());
        for (Allocatable alloc: getAllocatables())
        {
            if (user == null || user.isAdmin() || alloc.canReadOnlyInformation( user))
            {
                result.add( alloc);
            }
        }
        // add system preferences
        {
            PreferencesImpl preferences = getPreferencesForUserId( null );
            if ( preferences != null)
            {
                result.add( preferences);
            }
        }
        // add user preferences
        if ( user != null)
        {
            String userId = user.getId();
            Assert.notNull( userId);
            PreferencesImpl preferences = getPreferencesForUserId( userId );
            if ( preferences != null)
            {
                result.add( preferences);
            }
        }
        return result;
    }

    // Implementation of EntityResolver
    @Override
    public Entity resolve(String id) throws EntityNotFoundException {
        return resolve(id, null);
    }
    
    public <T extends Entity> T resolve(String id,Class<T> entityClass) throws EntityNotFoundException {
        T entity = tryResolve(id, entityClass);
        SimpleEntity.checkResolveResult(id, entityClass, entity);
        return entity;
    }
    
    @Override
    public Entity tryResolve(String id) {
        return tryResolve(id, null);
    }
    
    @Override
    public <T extends Entity> T tryResolve(String id,Class<T> entityClass)  {
        if (id == null)
            throw new RuntimeException("id is null");
        Entity entity = entities.get(id);
        @SuppressWarnings("unchecked")
        T casted = (T) entity;
        return casted;
    }

    public String getPassword(String userId) {
        return  passwords.get(userId);
    }

    public void putPassword(String userId, String password) {
        passwords.put(userId,password);
    }

    public void putAll( Collection<? extends Entity> list )
    {
    	for ( Entity entity: list)
    	{
    		put( entity);
    	}
    }

	public Provider<Category> getSuperCategoryProvider() {
		return new Provider<Category>() {

			public Category get() {
				return getSuperCategory();
			}
		};
	}

	@SuppressWarnings("unchecked")
    public Collection<User> getUsers() 	{
		return (Collection)users.values();
	}
	
	public void fillConflictDisableInformation( User user, Conflict conflict) {
        String id = conflict.getId();
        Conflict disabledConflict = conflicts.get( id);
        if (disabledConflict != null)
        {
            ((ConflictImpl)conflict).setAppointment1Enabled( disabledConflict.isAppointment1Enabled() );
            ((ConflictImpl)conflict).setAppointment2Enabled( disabledConflict.isAppointment2Enabled() );
        }
        EntityResolver cache = this;
        if ( user != null)
        {
            ((ConflictImpl)conflict).setAppointment1Editable( RaplaComponent.canModifyEvent(conflict.getReservation1(), user, cache));
            ((ConflictImpl)conflict).setAppointment2Editable( RaplaComponent.canModifyEvent(conflict.getReservation2(), user, cache));
        }
    }

	
	@SuppressWarnings("unchecked")
    public Collection<Conflict> getConflicts()  {
	    return (Collection) conflicts.values();
	}

	@SuppressWarnings("unchecked")
	public Collection<String> getConflictIds()  {
	        return (Collection) conflicts.keySet();
	}
	
	@SuppressWarnings("unchecked")
    public Collection<Allocatable> getAllocatables() {
	    return (Collection)resources.values();
	}

	@SuppressWarnings("unchecked")
    public Collection<Reservation> getReservations() {
	    return (Collection)reservations.values();
	}

	@SuppressWarnings("unchecked")
    public Collection<DynamicType> getDynamicTypes() {
		return (Collection)dynamicTypes.values();
	}

    public Iterable<Conflict> getDisabledConflicts() {
        List<Conflict> disabled = new ArrayList<Conflict>();
        for ( Conflict conflict:getConflicts())
        {
            if ( conflict.isAppointment1Enabled() && conflict.isAppointment2Enabled())
            {
                continue;
            }
            disabled.add( conflict);
        }
        return disabled;
    }

}
