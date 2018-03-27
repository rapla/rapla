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

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class EntityStore implements EntityResolver {
    HashMap<String,Entity> entities = new LinkedHashMap<>();
    HashMap<String,DynamicType> dynamicTypes = new HashMap<>();
    HashMap<ReferenceInfo<User>,String> passwordList = new HashMap<>();

    EntityResolver parent;
    
    public EntityStore(EntityResolver parent) {
        Assert.notNull( parent);
        this.parent = parent;
    //    final Category tryResolve = parent.tryResolve(Category.SUPER_CATEGORY_ID, Category.class);

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
        final Class<? extends Entity> raplaType = entity.getTypeClass();
        if ( raplaType == DynamicType.class)
        {
            DynamicType dynamicType = (DynamicType) entity;
            dynamicTypes.put ( dynamicType.getKey(), dynamicType);
        }
        /*
        if ( raplaType == Category.class)
        {
            CategoryImpl category = (CategoryImpl) entity;
            final ReferenceInfo<Category> parentRef = category.getParentRef();
            if(category.getId().equals(Category.SUPER_CATEGORY_ID))
            {
                superCategory = category;
            }
            else if ( parentRef == null || parentRef.getId().equals(Category.SUPER_CATEGORY_ID))
            {// on load from db we don't have a parent for all categories
                final CategoryImpl superCategory = getSuperCategory();
                final String id1 = category.getId();
                superCategory.addId("childs", id1);
            }
        }
        */
        entities.put(id,entity);
    }

    public void remove(ReferenceInfo ref)
    {
        String id = ref.getId();
        entities.remove(id);
        if ( ref.getType() == DynamicType.class)
        {
            String key;
            final Iterator<Map.Entry<String, DynamicType>> iterator = dynamicTypes.entrySet().iterator();
            while ( iterator.hasNext())
            {
                Map.Entry<String,DynamicType> entry = iterator.next();
                if ( entry.getValue().getReference().equals(ref))
                {
                    iterator.remove();
                }
            }
        }
        if ( ref.getType() == User.class)
        {
            passwordList.remove( ref);
        }
    }


    public DynamicType getDynamicType(String key)
    {
        DynamicType type =  dynamicTypes.get(key);
        if ( type == null )
        {
            type = parent.getDynamicType( key);
        }
        return type;
    }

    public Collection<Entity>getList() {
        return entities.values();
    }

    /*
    public CategoryImpl getSuperCategory()
    {
        return superCategory;
    }
    */

    public void putPassword( ReferenceInfo<User> userid, String password )
    {
        passwordList.put(userid, password);
    }
    
    public String getPassword( ReferenceInfo<User> userid)
    {
        return passwordList.get(userid);
    }

    public <T extends Entity> T resolve(String id,Class<T> entityClass) throws EntityNotFoundException {
        T entity = tryResolve(id, entityClass);
        SimpleEntity.checkResolveResult(id, entityClass, entity);
        return entity;
    }

    @Override
    public <T extends Entity> T tryResolve(ReferenceInfo<T> referenceInfo)
    {
        final Class<T> type = (Class<T>)referenceInfo.getType();
        return tryResolve( referenceInfo.getId(), type);
    }

    @Override
    public <T extends Entity> T resolve(ReferenceInfo<T> referenceInfo) throws EntityNotFoundException
    {
        final Class<T> type = (Class<T>)referenceInfo.getType();
        return resolve(referenceInfo.getId(), type);
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

        /*
        if ( id.equals( superCategory.getId()) && (entityClass == null || isCategoryClass(entityClass)))
        {
            @SuppressWarnings("unchecked")
            T casted = (T) superCategory;
            return casted;
        }
        */
        return tryResolveParent(id, entityClass);
    }

    private <T extends Entity> boolean isCategoryClass(Class<T> entityClass) {
        return entityClass.equals( Category.class) || entityClass.equals( CategoryImpl.class);
    }

    protected <T extends Entity> T tryResolveParent(String id, Class<T> entityClass) {
        T tryResolve = parent.tryResolve(id, entityClass);
        return tryResolve;
    }

}