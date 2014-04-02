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
import java.util.HashSet;
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
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.framework.Provider;
import org.rapla.framework.RaplaException;

public class LocalCache implements EntityResolver
{
    Map<Object,String> passwords = new HashMap<Object,String>();

    Map<Object,Entity> entities;
    Set<DynamicTypeImpl> dynamicTypes;
    Set<UserImpl> users;
    Set<AllocatableImpl> resources;
    Set<ReservationImpl> reservations;
    Set<CategoryImpl> categories;
    Set<AppointmentImpl> appointments;
    Set<AttributeImpl> attributes;
    Set<PreferencesImpl> preferences;
    
    Map<RaplaType,Set<? extends Entity>> entityMap;
    
    public LocalCache() {
        
        entityMap = new LinkedHashMap<RaplaType, Set<? extends Entity>>();
        entities = new HashMap<Object,Entity>();
        // top-level-entities
        reservations = new LinkedHashSet<ReservationImpl>();
        users = new LinkedHashSet<UserImpl>();
        resources = new LinkedHashSet<AllocatableImpl>();
        dynamicTypes = new LinkedHashSet<DynamicTypeImpl>();

        // non-top-level-entities with exception of one super-category
        categories = new HashSet<CategoryImpl>();
        appointments = new HashSet<AppointmentImpl>();
        preferences = new HashSet<PreferencesImpl>();
        attributes = new HashSet<AttributeImpl>();
        
        entityMap.put(DynamicType.TYPE,dynamicTypes);
        entityMap.put(Attribute.TYPE, attributes);
        entityMap.put(Category.TYPE, categories);
        entityMap.put(Allocatable.TYPE,resources);
        entityMap.put(User.TYPE,users);
        entityMap.put(Reservation.TYPE,reservations);
        entityMap.put(Appointment.TYPE,appointments);
        entityMap.put(Preferences.TYPE, preferences);
        initSuperCategory();
    }
    
	@Override
    public Entity tryResolve(String id) {
        if (id == null)
            throw new RuntimeException("id is null");
        return entities.get(id);
    }

    /** @return true if the entity has been removed and false if the entity was not found*/
    public boolean remove(Entity entity) {
        RaplaType raplaType = entity.getRaplaType();
        Set<? extends Entity> entitySet = entityMap.get(raplaType);
        boolean bResult = true;
        if (entitySet != null) {
            if (entities.get(entity.getId()) != null)
                bResult = false;
            if (entity.getId() == null)
                return false;

            entities.remove(entity.getId());
            entitySet.remove( entity );
            
            if ( entity instanceof ParentEntity)
            {
            	Collection<Entity> subEntities = ((ParentEntity) entity).getSubEntities();
            	for (Entity child:subEntities)
            	{
            		remove( child);
            	}
            }
        } else {
            throw new RuntimeException("UNKNOWN TYPE. Can't remove object:" + entity.getRaplaType());
        }
        return bResult;
    }

    public void put(Entity entity) {
        Assert.notNull(entity);
       
        RaplaType raplaType = entity.getRaplaType();
       
        Comparable id = entity.getId();
        if (id == null)
            throw new IllegalStateException("ID can't be null");

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
        
        @SuppressWarnings("unchecked")
		Set<Entity>entitySet =  (Set<Entity>) entityMap.get(raplaType);
        if (entitySet != null) {
            entities.put(id,entity);
            entitySet.remove( entity );
			entitySet.add( entity );
        } 
        else 
        {
            throw new RuntimeException("UNKNOWN TYPE. Can't store object in cache: " + entity.getRaplaType());
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
    <T extends Entity> Collection<T> getCollection(RaplaType type) {
        Set<? extends Entity> entities =  entityMap.get(type);
       
        if (entities != null) {
            return (Collection<T>) entities;
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
        for (Set<? extends Entity> set: entityMap.values() )
        {
        	set.clear();
        }
        entities.clear();
        initSuperCategory();
    }
    
    private void initSuperCategory() {
    	CategoryImpl superCategory = new CategoryImpl(null, null);
        superCategory.setId(LocalCache.SUPER_CATEGORY_ID);
        superCategory.setKey("supercategory");
        superCategory.getName().setName("en", "Root");
        entities.put (LocalCache.SUPER_CATEGORY_ID, superCategory);
        categories.add( superCategory );
        Category[] childs = superCategory.getCategories();
        for (int i=0;i<childs.length;i++) {
            superCategory.removeCategory( childs[i] );
        }
    }

    public static String SUPER_CATEGORY_ID = Category.TYPE.getId(0);
    
    public CategoryImpl getSuperCategory() 
    {
        return (CategoryImpl) get(SUPER_CATEGORY_ID);
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
        for (UserImpl user:users)
        {
            if (user.getUsername().equals(username))
                return user;
        }
        for (UserImpl user:users)
        {
            if (user.getUsername().equalsIgnoreCase(username))
                return user;
        }
        return null;
    }

    public PreferencesImpl getPreferencesForUserId(String userId) {
		String preferenceId = PreferencesImpl.getPreferenceIdFromUser(userId);
		PreferencesImpl pref = (PreferencesImpl) tryResolve( preferenceId);
		return pref; 	
    }
    
   
    public DynamicType getDynamicType(String elementKey) {
        for (DynamicType dt:dynamicTypes) {
            if (dt.getKey().equals(elementKey))
                return dt;
        }
        return null;
    }

    public  Set<Entity> getAllEntities() 
    {
    	HashSet<Entity> result = new HashSet<Entity>();
    	for ( Set<? extends Entity> set:entityMap.values() )
 	   	{
    		result.addAll( set);
 	   	}
    	return result;
    }

    // Implementation of EntityResolver
    @Override
    public Entity resolve(String id) throws EntityNotFoundException {
        Entity entity =  tryResolve(id);

        if (entity == null)
            throw new EntityNotFoundException("Object for id [" + id + "] not found", id);
        return entity;
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

	public Entity resolveEmail(final String emailArg) throws EntityNotFoundException
    {
		Set<? extends Entity> entities = entityMap.get(Allocatable.TYPE);
    	for (Entity entity: entities)
    	{
    		final Classification classification = ((Allocatable) entity).getClassification();
    		final Attribute attribute = classification.getAttribute("email");
    		if ( attribute != null)
    		{
    			final String email = (String)classification.getValue(attribute);
    			if ( email != null && email.equals( emailArg))
    			{
    				return entity;
    			}
    		}
        }
    	throw new EntityNotFoundException("Object for email " + emailArg + " not found");
    }

	public Provider<Category> getSuperCategoryProvider() {
		return new Provider<Category>() {

			public Category get() {
				return getSuperCategory();
			}
		};
	}
	
	 static public String getId(RaplaType type,String str) throws RaplaException {
	    	if (str == null)
	    		throw new RaplaException("Id string for " + type + " can't be null");
	    	int index = str.lastIndexOf("_") + 1;
	        if (index>str.length())
	            throw new RaplaException("invalid rapla-id '" + str + "'");
	        try {
	        	return type.getId(Integer.parseInt(str.substring(index)));
	        } catch (NumberFormatException ex) {
	            throw new RaplaException("invalid rapla-id '" + str + "'");
	        }
	    }


	static public boolean isTextId( RaplaType type,String content )
	{
	    if ( content == null)
	    {
	        return false;
	    }
	    content = content.trim();
	    if ( isNumeric( content))
	    {
	    	return true;
	    }
	    String KEY_START = type.getLocalName() + "_";
	    boolean idContent = (content.indexOf( KEY_START ) >= 0  && content.length() > 0);
	    return idContent;
	}

	static public boolean isNumeric(String text)
	{
		int length = text.length();
		if ( length == 0)
		{
			return false;
		}
		for ( int i=0;i<length;i++)
	    {
	    	char ch = text.charAt(i);
			if (!Character.isDigit(ch))
	    	{
	    		return false;
	    	}
	    }
		return true;
	}

	@Deprecated
	public Set<Entry<RaplaType, Set<? extends Entity>>> entrySet() {
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
