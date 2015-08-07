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
package org.rapla.storage.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.SimpleEntity;

public class EntityStore implements EntityResolver {
    HashMap<String,Entity> entities = new LinkedHashMap<String,Entity>();
    HashMap<String,DynamicType> dynamicTypes = new HashMap<String,DynamicType>();
    HashMap<String,String> passwordList = new HashMap<String,String>();
    CategoryImpl superCategory;
    
    EntityResolver parent;
    
    public EntityStore(EntityResolver parent,Category superCategory) {
        this.parent = parent;
        this.superCategory = (CategoryImpl) superCategory;
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

    public void putPassword( String userid, String password )
    {
        passwordList.put(userid, password);
    }
    
    public String getPassword( String userid)
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

        if ( id.equals( superCategory.getId()) && (entityClass == null || isCategoryClass(entityClass)))
        {
            @SuppressWarnings("unchecked")
            T casted = (T) superCategory;
            return casted;
        }
      
        if (parent != null)
        {
            return tryResolveParent(id, entityClass);
            
        }
        return null;
    }

    private <T extends Entity> boolean isCategoryClass(Class<T> entityClass) {
        return entityClass.equals( Category.class) || entityClass.equals( CategoryImpl.class);
    }

    protected <T extends Entity> T tryResolveParent(String id, Class<T> entityClass) {
        T tryResolve = parent.tryResolve(id, entityClass);
        return tryResolve;
    }

    
}