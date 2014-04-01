/**
 * 
 */
package org.rapla.storage.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.EntityResolver;

public class EntityStore implements EntityResolver {
    HashMap<String,Entity> entities = new LinkedHashMap<String,Entity>();
    HashSet<String> idsToRemove = new HashSet<String>();
    HashSet<String> idsToStore = new HashSet<String>();
    HashSet<String> idsToReference = new HashSet<String>();
    EntityResolver parent;
    HashMap<String,DynamicType> dynamicTypes = new HashMap<String,DynamicType>();
    HashSet<Allocatable> allocatables = new HashSet<Allocatable>();
    Map<String,Map<String,String>> serverPreferences = new HashMap<String,Map<String,String>>();
    CategoryImpl superCategory;
    HashMap<Object,String> passwordList = new HashMap<Object,String>();
    int repositoryVersion;
    
    public EntityStore(EntityResolver parent,Category superCategory) {
        this.parent = parent;
        this.superCategory = (CategoryImpl) superCategory;
       // put( superCategory);
    }
    
    public void addAll(Collection<? extends Entity>collection) {
        Iterator<? extends Entity>it = collection.iterator();
        while (it.hasNext())
        {
            put(it.next());
        }
    }

    public void put(Entity entity) {
        String id = entity.getId();
        Assert.notNull(id);
        if ( entity.getRaplaType() == DynamicType.TYPE)
        {
            DynamicType dynamicType = (DynamicType) entity;
            dynamicTypes.put ( dynamicType.getKey(), dynamicType);
        }
        if ( entity.getRaplaType() ==  Allocatable.TYPE)
        {
        	Allocatable allocatable = (Allocatable) entity;
            allocatables.add (  allocatable);
        }
        if ( entity.getRaplaType() == Category.TYPE)
        {
        	for (Category child:((Category)entity).getCategories())
        	{
        		put( child);
        	}
        }
        entities.put(id,entity);
    }
    
    public void addRemoveId(String id)
    {
        idsToRemove.add(id);
    }
    
    public void addReferenceId(String id)
    {
        idsToReference.add(id);
    }

    public void addStoreId(String id)
    {
        idsToStore.add(id);
    }
    
    
    public DynamicType getDynamicType(String key)
    {
        // todo super 
        DynamicType type =  dynamicTypes.get( key);
        if ( type == null && parent != null) 
        {
            type = parent.getDynamicType( key);
        }
        return type;
    }
    
    public Collection<Entity>getList() {
        return entities.values();
    }
    
    public Collection<String> getRemoveIds() {
        return  idsToRemove;
    }

    public Collection<String> getStoreIds() {
        return  idsToStore;
    }
   
	public Collection<String> getReferenceIds() {
	    return idsToReference;
	}


    // Implementation of EntityResolver
    public Entity resolve(String id) throws EntityNotFoundException {
        Entity result = tryResolve(id );
        if ( result == null)
        {
            throw new EntityNotFoundException("Object for id " + id.toString() + " not found",  id);
        }
        return result;
    }
    
    public Entity resolveEmail(final String emailArg) throws EntityNotFoundException
    {
    	for (Allocatable entity: allocatables)
    	{
    		final Classification classification = entity.getClassification();
    		final Attribute attribute = classification.getAttribute("email");
    		if ( attribute != null)
    		{
    			final String email = (String)classification.getValue(attribute);
    			if ( email != null && email.equals( emailArg))
    			{
    				return (Entity)entity;
    			}
    		}
        }
    	throw new EntityNotFoundException("Object for email " + emailArg + " not found");
    }

    public CategoryImpl getSuperCategory()
    {
        return superCategory;
    }

    public void putPassword( Object userid, String password )
    {
        passwordList.put(userid, password);
    }
    
    public String getPassword( Object userid)
    {
        return passwordList.get(userid);
    }

    @Override
    public Entity tryResolve( String id )
    {
    	Assert.notNull( id);
        Entity entity = entities.get(id);
        if (entity != null)
            return entity;

        if ( id.equals( superCategory.getId()))
        {
            return superCategory;
        }
      
        if (parent != null)
        {
            return parent.tryResolve(id);
            
        }
        return null;
    }

    public Collection<RaplaObject> getCollection( RaplaType raplaType )
    {
        List<RaplaObject> collection = new ArrayList<RaplaObject>();
        Iterator<Entity>it = entities.values().iterator();
        while (it.hasNext())
        {
            RaplaObject obj = it.next();
            if ( obj.getRaplaType().equals( raplaType))
            {
                collection.add( obj);
            }
        }
        return collection;
    }

    public int getRepositoryVersion()
    {
        return repositoryVersion;
    }

    public void setRepositoryVersion( int repositoryVersion )
    {
        this.repositoryVersion = repositoryVersion;
    }

//	public void putServerPreferences(User user, String configRole, String value) {
//		String userId = user != null ? user.getId() : null;
//		String preferenceIdFromUser = PreferencesImpl.getPreferenceIdFromUser(userId);
//		Map<String, String> map = serverPreferences.get(preferenceIdFromUser);
//		if ( map == null)
//		{
//			map = new HashMap<String,String>();
//			serverPreferences.put(preferenceIdFromUser, map);
//		}
//		map.put(configRole, value);
//	}



        
    
}