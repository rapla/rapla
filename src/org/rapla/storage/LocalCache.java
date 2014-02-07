/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Provider;
import org.rapla.framework.RaplaException;

public class LocalCache implements EntityResolver
{
    Map<Object,String> passwords = new HashMap<Object,String>();

    Map<Object,RefEntity<?>> entities;
    Set<DynamicTypeImpl> dynamicTypes;
    Set<UserImpl> users;
    Set<AllocatableImpl> resources;
    Set<ReservationImpl> reservations;
    Set<PeriodImpl> periods;

    Set<CategoryImpl> categories;
    Set<AppointmentImpl> appointments;
    Set<AttributeImpl> attributes;
    Set<PreferencesImpl> preferences;
    
    Map<RaplaType,Set<? extends RefEntity<?>>> entityMap;
        
    class IdComparator implements Comparator<RefEntity<?>> {
        public int compare(RefEntity<?> o1,RefEntity<?> o2) {
            Comparable id1 = o1.getId();
            Comparable id2 = o2.getId();
            return id1.compareTo( id2);
        }
    }

    private CategoryImpl superCategory = new CategoryImpl();

    public LocalCache() {
        superCategory.setId(LocalCache.SUPER_CATEGORY_ID);
        superCategory.setKey("supercategory");
        superCategory.getName().setName("en", "Root");
        Comparator<RefEntity<?>> comp = new IdComparator();
        
        entityMap = new HashMap<RaplaType, Set<? extends RefEntity<?>>>();
        entities = new HashMap<Object, RefEntity<?>>();
        // top-level-entities
        reservations = new TreeSet<ReservationImpl>(comp);
        periods = new TreeSet<PeriodImpl>(comp);
        users = new TreeSet<UserImpl>(comp);
        resources = new TreeSet<AllocatableImpl>(comp);
        dynamicTypes = new TreeSet<DynamicTypeImpl>(comp);

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
        entityMap.put(Period.TYPE,periods);
        entityMap.put(Reservation.TYPE,reservations);
        entityMap.put(Appointment.TYPE,appointments);
        entityMap.put(Preferences.TYPE, preferences);


        initSuperCategory();
    }
    
    @Override
    public RefEntity<?> tryResolve(String id) {
        if (id == null)
            throw new RuntimeException("id is null");
        return entities.get(id);
    }

    /** @return true if the entity has been removed and false if the entity was not found*/
    public boolean remove(RefEntity<?> entity) {
        RaplaType raplaType = entity.getRaplaType();
        Set<? extends RefEntity<?>> entitySet = entityMap.get(raplaType);
        boolean bResult = true;
        if (entitySet != null) {
            if (entities.get(entity.getId()) != null)
                bResult = false;
            if (entity.getId() == null)
                return false;



            entities.remove(entity.getId());
            entitySet.remove( entity );
        } else {
            throw new RuntimeException("UNKNOWN TYPE. Can't remove object:" + entity.getRaplaType());
        }
        return bResult;
    }

    public void put(RefEntity<?> entity) {
        Assert.notNull(entity);
        // We don't add the superCategory
        if (entity == superCategory)
        {
        	return;
        }
  
        RaplaType raplaType = entity.getRaplaType();
       
        Comparable id = entity.getId();
        if (id == null)
            throw new IllegalStateException("ID can't be null");

        @SuppressWarnings("unchecked")
		Set<RefEntity<?>> entitySet =  (Set<RefEntity<?>>) entityMap.get(raplaType);
        if (entitySet != null) {
            entities.put(id,entity);
            entitySet.remove( entity );
			entitySet.add( entity );
        } 
        else 
        {
            throw new RuntimeException("UNKNOWN TYPE. Can't store object in cache: " + entity.getRaplaType());
        }
    }


    public RefEntity<?> get(Comparable id) {
        if (id == null)
            throw new RuntimeException("id is null");
        return entities.get(id);
    }

    @SuppressWarnings("unchecked")
	public <T extends RefEntity<?>> Collection<T> getCollection(RaplaType type) {
        Set<? extends RefEntity<?>> entities =  entityMap.get(type);

        if ( Period.TYPE.equals( type)) {
            entities = new TreeSet<RefEntity<?>>( entities);
        }

        if (entities != null) {
            return (Collection<T>) entities;
        } else {
            throw new RuntimeException("UNKNOWN TYPE. Can't get collection: "
                                       +  type);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends RaplaObject> Collection<T> getCollection(Class<T> clazz) {
    	RaplaType type = RaplaType.get(clazz);
		Collection<T> collection = (Collection<T>) getCollection(type);
		return new LinkedHashSet(collection);
    }
	

    public void clearAll() {
        passwords.clear();
        for (Set<? extends RefEntity<?>> set: entityMap.values() )
        {
        	set.clear();
        }
        entities.clear();
        initSuperCategory();

    }
    private void initSuperCategory() {
        entities.put (LocalCache.SUPER_CATEGORY_ID, superCategory);
        superCategory.setReadOnly( false );
        categories.add( superCategory );
        Category[] childs = superCategory.getCategories();
        for (int i=0;i<childs.length;i++) {
            superCategory.removeCategory( childs[i] );
        }
    }

    public static String SUPER_CATEGORY_ID = Category.TYPE.getId(0);
    public CategoryImpl getSuperCategory() {
        return (CategoryImpl) get(SUPER_CATEGORY_ID);
    }

    public <T extends RaplaObject> Collection<RefEntity<?>> getReferers(Class<T> raplaType,RefEntity<?> object) {
        ArrayList<RefEntity<?>> result = new ArrayList<RefEntity<?>>();
        for ( T obj: getCollection( raplaType))
        {
        	RefEntity<?> referer = (RefEntity<?>) obj;
        	if (referer != null && !referer.isIdentical(object) && referer.isRefering(object))
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

    public PreferencesImpl getPreferences(User user) {
        for (PreferencesImpl pref: preferences)
        {
            User owner = pref.getOwner();
            if ( user == null && owner == null ) {
                return pref;
            }
            if (user!= null && pref.getOwner() != null && user.equals(pref.getOwner())) {
                return pref;
            }

        }
        return null;
    }

   
    public DynamicType getDynamicType(String elementKey) {
        for (DynamicType dt:dynamicTypes) {
            if (dt.getElementKey().equals(elementKey))
                return dt;
        }
        return null;
    }

   public Collection<RefEntity<?>> getVisibleEntities(final User user) {
	   Collection<RefEntity<?>> result = new ArrayList<RefEntity<?>>();
	   for ( Map.Entry<RaplaType,Set<? extends RefEntity<?>>> entry:entityMap.entrySet() )
	   {
		   RaplaType raplaType = entry.getKey();
		   if (   Conflict.TYPE.equals( raplaType ))
		   {
			   continue;
           }
		   Set<RefEntity<?>> set =  (Set<RefEntity<?>>) entry.getValue();
		   if (   Appointment.TYPE.equals( raplaType )  || Reservation.TYPE.equals( raplaType))
		   {
			   for ( RefEntity<?> obj: set)
			   {
            		if ( RaplaComponent.isTemplate(obj))
            		{
            			result.add( obj);
					}
				}
            }
            if (user == null )
            {
            	result.addAll( set);
            }
            else
            {
                if (   Preferences.TYPE.equals( raplaType )  )
                {
                	{
                		PreferencesImpl preferences = getPreferences( null );
                        if ( preferences != null)
                        {
                        	result.add( preferences);
                        }
                    }
                    {
                        PreferencesImpl preferences = getPreferences( user );
                        if ( preferences != null)
                        {
                            result.add( preferences);
                        }
                    }
                }
                else if (   Allocatable.TYPE.equals( raplaType )  )
                {
                	for ( RefEntity<?> obj: set)
                	{
                    	Allocatable alloc = (Allocatable) obj;
						if (user.isAdmin() || alloc.canReadOnlyInformation( user))
						{
	            			result.add( obj);
						}
					}
                }
                else
                {
                	result.addAll( set);
                }
            }
       	}
	   	return result;
    }

    public  Set<RefEntity<?>> getAllEntities() 
    {
    	HashSet<RefEntity<?>> result = new HashSet<RefEntity<?>>();
    	for ( Set<? extends RefEntity<?>> set:entityMap.values() )
 	   	{
    		result.addAll( set);
 	   	}
    	return result;
    }

    // Implementation of EntityResolver
    @Override
    public RefEntity<?> resolve(String id) throws EntityNotFoundException {
        RefEntity<?> entity =  tryResolve(id);

        if (entity == null)
            throw new EntityNotFoundException("Object for id [" + id.toString() + "] not found", id);
        return entity;
    }

    public String getPassword(Object userId) {
        return  passwords.get(userId);
    }

    public void putPassword(Object userId, String password) {
        passwords.put(userId,password);
    }

    public void putAll( Collection<? extends RefEntity<?>> list )
    {
    	for ( RefEntity<?> entity: list)
    	{
    		put( entity);
    	}
    }

	public RefEntity<?> resolveEmail(final String emailArg) throws EntityNotFoundException
    {
		Set<? extends RefEntity<?>> entities = entityMap.get(Allocatable.TYPE);
    	for (RefEntity<?> entity: entities)
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
	
	static public String getId(String string) throws RaplaException {
		int index = string.lastIndexOf("_") + 1;
		if ( index <= 0)
		{
            throw new RaplaException("invalid rapla-id '" + string + "' Type is missing and not passed as argument.");
		}
		String typeName = string.substring(0, index -1);
		RaplaType raplaType = RaplaType.find(typeName);
		return getId( raplaType, string);
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

}
