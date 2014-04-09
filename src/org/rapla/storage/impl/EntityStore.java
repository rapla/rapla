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

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.SimpleEntity;

public class EntityStore implements EntityResolver {
    HashMap<String,Entity> entities = new LinkedHashMap<String,Entity>();
    EntityResolver parent;
    HashMap<String,DynamicType> dynamicTypes = new HashMap<String,DynamicType>();
    HashSet<Allocatable> allocatables = new HashSet<Allocatable>();
    CategoryImpl superCategory;
    HashMap<Object,String> passwordList = new HashMap<Object,String>();
    
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
        Assert.notNull( id);
        Entity entity = entities.get(id);
        if (entity != null) {
            @SuppressWarnings("unchecked")
            T casted = (T) entity;
            return casted;
        }

        if ( id.equals( superCategory.getId()) && (entityClass == null || Category.class.isAssignableFrom( entityClass)))
        {
            @SuppressWarnings("unchecked")
            T casted = (T) superCategory;
            return casted;
        }
      
        if (parent != null)
        {
            return parent.tryResolve(id, entityClass);
            
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