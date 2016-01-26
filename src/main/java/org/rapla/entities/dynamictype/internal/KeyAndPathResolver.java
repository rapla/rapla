package org.rapla.entities.dynamictype.internal;

import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.storage.impl.EntityStore;

public class KeyAndPathResolver
{
    EntityStore store;

    public KeyAndPathResolver(EntityStore store)
    {
        this.store = store;
    }

    public ReferenceInfo getIdForDynamicType(String keyref)
    {
        final DynamicType dynamicType = store.getDynamicType(keyref);
        if ( dynamicType != null)
        {
            return dynamicType.getReference();
        }
        return null;

    }

    public ReferenceInfo<Category> getIdForCategory(ReferenceInfo<Category> parentCategory,String keyref)
    {
        String path = store.getPath( parentCategory);
        String completePath = path + "/" + keyref;
        final Category category = store.getCategory(completePath);
        if ( category == null)
        {
            return null;
        }
        return category.getReference();
    }

    public ReferenceInfo<Category> getIdForCategory(String keyref)
    {
        if (store.tryResolve(keyref, Category.class) != null)
        {
            return new ReferenceInfo(keyref, Category.class);
        }
        final Category category = store.getCategory(keyref);
        if ( category != null)
        {
            return category.getReference();
        }
        else
        {
            return null;
        }
    }

}
