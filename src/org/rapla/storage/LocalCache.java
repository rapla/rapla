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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
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
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.Provider;

public class LocalCache implements EntityResolver
{
    Map<Object,String> passwords = new HashMap<Object,String>();

    Map<Object,Entity> entities;
    Map<String,DynamicTypeImpl> dynamicTypes;
    Map<String,UserImpl> users;
    Map<String,AllocatableImpl> resources;
    Map<String,ReservationImpl> reservations;
    Map<String,CategoryImpl> categories;
    Map<String,PreferencesImpl> preferences;
    
    Map<RaplaType,Map<String,? extends Entity>> entityMap;
    private String clientUserId;
    public LocalCache() {
        
        entityMap = new LinkedHashMap<RaplaType, Map<String,? extends Entity>>();
        entities = new HashMap<Object,Entity>();
        // top-level-entities
        reservations = new LinkedHashMap<String,ReservationImpl>();
        users = new LinkedHashMap<String,UserImpl>();
        resources = new LinkedHashMap<String,AllocatableImpl>();
        dynamicTypes = new LinkedHashMap<String,DynamicTypeImpl>();

        // non-top-level-entities with exception of one super-category
        categories = new LinkedHashMap<String,CategoryImpl>();
        preferences = new LinkedHashMap<String,PreferencesImpl>();
        
        entityMap.put(DynamicType.TYPE,dynamicTypes);
        entityMap.put(Category.TYPE, categories);
        entityMap.put(Allocatable.TYPE,resources);
        entityMap.put(User.TYPE,users);
        entityMap.put(Reservation.TYPE,reservations);
        entityMap.put(Preferences.TYPE, preferences);
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
        bResult = entities.remove(entity.getId()) != null;
        Map<String,? extends Entity> entitySet = entityMap.get(raplaType);
        if (entitySet != null) {
            if (entities.get(entity.getId()) != null)
                bResult = false;
            if (entity.getId() == null)
                return false;

            entitySet.remove( entity.getId() );
            if ( entity instanceof ParentEntity)
            {
            	Collection<Entity> subEntities = ((ParentEntity) entity).getSubEntities();
            	for (Entity child:subEntities)
            	{
            		remove( child);
            	}
            }
        } else {
            //throw new RuntimeException("UNKNOWN TYPE. Can't remove object:" + entity.getRaplaType());
        }
        return bResult;
    }

    public void put(Entity entity) {
        Assert.notNull(entity);
       
        RaplaType raplaType = entity.getRaplaType();
       
        Comparable id = entity.getId();
        if (id == null)
            throw new IllegalStateException("ID can't be null");

        String clientUserId = getClientUserId();
        if ( clientUserId != null )
        {
            if (raplaType == Reservation.TYPE || raplaType == Appointment.TYPE )
            {
                throw new IllegalArgumentException("Can't store reservations or appointments in client cache");
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
        
        entities.put(id,entity);
        @SuppressWarnings("unchecked")
		Map<String,Entity> entitySet =  (Map<String, Entity>) entityMap.get(raplaType);
        if (entitySet != null) {
            entitySet.put( entity.getId() ,entity);
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

    @SuppressWarnings("unchecked")
    private <T extends Entity> Collection<T> getCollection(RaplaType type) {
        Map<String,? extends Entity> entities =  entityMap.get(type);
       
        if (entities != null) {
            return (Collection<T>) entities.values();
        } else {
            throw new RuntimeException("UNKNOWN TYPE. Can't get collection: "
                                       +  type);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T extends RaplaObject> Collection<T> getCollection(Class<T> clazz) {
    	RaplaType type = RaplaType.get(clazz);
		Collection<T> collection = (Collection<T>) getCollection(type);
		return new LinkedHashSet(collection);
    }
	
    public void clearAll() {
        passwords.clear();
        for (Map<String,? extends Entity> set: entityMap.values() )
        {
        	set.clear();
        }
        entities.clear();
        initSuperCategory();
    }
    
    private void initSuperCategory() {
    	CategoryImpl superCategory = new CategoryImpl(null, null);
        superCategory.setId(Category.SUPER_CATEGORY_ID);
        superCategory.setKey("supercategory");
        superCategory.getName().setName("en", "Root");
        entities.put (Category.SUPER_CATEGORY_ID, superCategory);
        categories.put( Category.SUPER_CATEGORY_ID,superCategory );
        Category[] childs = superCategory.getCategories();
        for (int i=0;i<childs.length;i++) {
            superCategory.removeCategory( childs[i] );
        }
    }

    public CategoryImpl getSuperCategory() 
    {
        return (CategoryImpl) get(Category.SUPER_CATEGORY_ID);
    }

    public <T extends Entity> Collection<Entity> getReferers(Class<T> raplaType,Entity object) {
        ArrayList<Entity> result = new ArrayList<Entity>();
        for ( T referer: getCollection( raplaType))
        {
        	if (referer != null && !referer.isIdentical(object) && ((EntityReferencer)referer).isRefering(object.getId()))
        	{
                result.add(referer);
            }
        }
        return result;
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

    public  Set<Entity> getAllEntities() 
    {
    	LinkedHashSet<Entity> result = new LinkedHashSet<Entity>();
    	for ( Map<String,? extends Entity> set:entityMap.values() )
 	   	{
    		result.addAll( set.values());
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


    public String getPassword(Object userId) {
        return  passwords.get(userId);
    }

    public void putPassword(Object userId, String password) {
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
	
	@Deprecated
	public Set<Entry<RaplaType, Map<String,? extends Entity>>> entrySet() {
		return entityMap.entrySet();
	}
	
	public Collection<User> getUsers() 
	{
		return getCollection(User.class);
	}

	public Collection<Allocatable> getAllocatables() {
		return getCollection(Allocatable.class);
	}

	public Collection<Reservation> getReservations() {
		return getCollection( Reservation.class);
	}

	public Collection<DynamicType> getDynamicTypes() {
		return getCollection( DynamicType.class);
	}

}
