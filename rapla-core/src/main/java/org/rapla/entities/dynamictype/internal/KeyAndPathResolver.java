package org.rapla.entities.dynamictype.internal;

import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.storage.impl.EntityStore;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class KeyAndPathResolver
{
    EntityStore store;

    HashMap<String,Category> categories = new HashMap<>();
    HashMap<String,String> categoryPath = new HashMap<>();

    public KeyAndPathResolver(EntityStore store, Category superCategory) throws EntityNotFoundException
    {
        this.store = store;
        fillCategoriesAndPaths(superCategory, 0);
    }

    private void fillCategoriesAndPaths(Category category, int depth) throws EntityNotFoundException
    {
        if ( depth > 40)
        {
            throw new IllegalStateException("Category cycle detected in " + category );
        }
        addCategory(category);
        for(Category cat : category.getCategories())
        {
            final Category parent = cat.getParent();
            if(parent.getId().equals(category.getId()))
            {
                fillCategoriesAndPaths(cat, depth + 1);
            }
        }
    }

    public void addCategory(Category category) throws EntityNotFoundException
    {
        //final ReferenceInfo<Category> parentRef = category.getParentRef();
        final List<String> pathForCategory = getPathForCategory(category);
        final String keyPathString = CategoryImpl.getKeyPathString(pathForCategory);
        categories.put(keyPathString, category);
        final String id1 = category.getId();
        categoryPath.put (id1, keyPathString);
    }

    private List<String> getPathForCategory(Category searchCategory) throws EntityNotFoundException {
        LinkedList<String> result = new LinkedList<>();
        Category category = searchCategory;
        Category parent = null;
        int depth = 0;
        while (true) {
            String entry ="category[key='" + category.getKey() +  "']";
            result.addFirst(entry);
            final ReferenceInfo<Category> parentRef = ((CategoryImpl) category).getParentRef();
            if (parentRef == null || parentRef.equals(Category.SUPER_CATEGORY_REF))
            {
                return result;
            }
            parent = store.resolve(parentRef);
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

    private Category getCategory(String path)
    {
        return categories.get( path);
    }

    public Category getCategoryForId(ReferenceInfo referenceInfo)
    {
        final String s = categoryPath.get(referenceInfo.getId());
        if ( s == null)
        {
            return  null;
        }
        else
        {
            final Category category = categories.get(s);
            return category;
        }
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

    public ReferenceInfo<Category> getIdForCategory(ReferenceInfo<Category> parentCategory,String keyref)throws RaplaException
    {
        StringBuilder builder = new StringBuilder();
        if ( !parentCategory.equals( Category.SUPER_CATEGORY_REF))
        {
            builder.append(CategoryImpl.getKeyPathString(getPathForCategory(store.resolve(parentCategory))));
            builder.append("/");
        }
        builder.append(keyref);
        String completePath = builder.toString();
        final Category category = getCategory(completePath);
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
        final Category category = getCategory(keyref);
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
