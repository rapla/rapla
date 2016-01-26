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
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class EntityStore implements EntityResolver {
    HashMap<String,Entity> entities = new LinkedHashMap<String,Entity>();
    HashMap<String,DynamicType> dynamicTypes = new HashMap<String,DynamicType>();
    HashMap<String,Category> categories = new HashMap<String,Category>();
    HashMap<String,String> categoryPath = new HashMap<String,String>();
    HashMap<ReferenceInfo<User>,String> passwordList = new HashMap<ReferenceInfo<User>,String>();
    CategoryImpl superCategory;

    EntityResolver parent;
    
    public EntityStore(EntityResolver parent) {
        Assert.notNull( parent);
        this.parent = parent;
        this.superCategory = new CategoryImpl();
        superCategory.setId(Category.SUPER_CATEGORY_ID);
        superCategory.setResolver( this);
        superCategory.setKey("supercategory");
        superCategory.getName().setName("en", "Root");
        entities.put(Category.SUPER_CATEGORY_ID, superCategory);
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
        if ( raplaType == Category.class)
        {
            CategoryImpl category = (CategoryImpl) entity;
            //final ReferenceInfo<Category> parentRef = category.getParentRef();
            final List<String> pathForCategory = getPathForCategory(category);
            final String keyPathString = CategoryImpl.getKeyPathString(pathForCategory);
            categories.put(keyPathString, category);
            categoryPath.put ( category.getId(), keyPathString);
        }
        entities.put(id,entity);

    }

    public List<String> getPathForCategory(Category searchCategory) throws EntityNotFoundException {
        LinkedList<String> result = new LinkedList<String>();
        Category category = searchCategory;
        Category parent = null;
        int depth = 0;
        while (true) {
            String entry ="category[key='" + category.getKey() +  "']";
            result.addFirst(entry);
            final ReferenceInfo<Category> parentRef = ((CategoryImpl) category).getParentRef();
            if (parentRef == null || parentRef.getId() == Category.SUPER_CATEGORY_ID)
            {
                return result;
            }
            parent = resolve(parentRef);
            category = parent;
            if ( depth > 20)
            {
                throw new EntityNotFoundException("Possible category cycle detected " + result);
            }
            else {
                depth++;
            }
        }
    }

    public Category getCategory(String path)
    {
        return categories.get( path);
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
    
    public CategoryImpl getSuperCategory()
    {
        return superCategory;
    }

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
    public <T extends Entity> T resolve(ReferenceInfo<T> referenceInfo)
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

        if ( id.equals( superCategory.getId()) && (entityClass == null || isCategoryClass(entityClass)))
        {
            @SuppressWarnings("unchecked")
            T casted = (T) superCategory;
            return casted;
        }
        return tryResolveParent(id, entityClass);
    }

    private <T extends Entity> boolean isCategoryClass(Class<T> entityClass) {
        return entityClass.equals( Category.class) || entityClass.equals( CategoryImpl.class);
    }

    protected <T extends Entity> T tryResolveParent(String id, Class<T> entityClass) {
        T tryResolve = parent.tryResolve(id, entityClass);
        return tryResolve;
    }

    public String getPath(ReferenceInfo<Category> category)
    {
        final String id = category.getId();
        return categoryPath.get(id);
    }
}