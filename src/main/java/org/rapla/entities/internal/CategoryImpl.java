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
package org.rapla.entities.internal;

import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final public class CategoryImpl extends SimpleEntity implements Category, ModifiableTimestamp
{
    private MultiLanguageName name = new MultiLanguageName();
    private String key;
    private Date lastChanged;
    private Date createDate;
    private Map<String,String> annotations = new LinkedHashMap<String,String>();
    public CategoryImpl()
    {
        this(new Date(), new Date());
    }
    
    public CategoryImpl(Date createDate, Date lastChanged) {
    	this.createDate = createDate;
    	this.lastChanged = lastChanged;
    }

    @Override
    public void setResolver(EntityResolver resolver) {
    	super.setResolver(resolver);
    }

    public Date getLastChanged() {
        return lastChanged;
    }
    
    public Date getCreateTime() {
        return createDate;
    }

    public void setLastChanged(Date date) {
        checkWritable();
        lastChanged = date;
    }

    @Override public Class<Category> getTypeClass()
    {
        return Category.class;
    }

    void setParent(CategoryImpl parent) {
		putEntity("parent", parent);
	}

    public void setParentId(ReferenceInfo<Category> parent) {
        putId("parent", parent);
    }

    public void removeParent()
    {
        removeWithKey("parent");
    }
    
    public Category getParent()
    {
        return getEntity("parent", Category.class);
    }

    public ReferenceInfo<Category> getParentRef()
    {
        return getRef("parent",Category.class);
    }

    public Category[] getCategories() {
        final Collection<Category> childs = getCategoryList();
        return childs.toArray(Category.CATEGORY_ARRAY);
    }

    public Collection<Category> getCategoryList()
    {
        return getList("childs", Category.class);
    }

    /** returns true if this is a direct or transitive parent of the passed category*/
    public boolean isAncestorOf(Category category) {
        if (category == null)
            return false;
        if (category.getParent() == null)
            return false;
        if (category.getParent().equals(this))
            return true;
        else
            return isAncestorOf(category.getParent());
    }

    public Category getCategory(String key) {
        for (Entity ref: getCategoryList())
        {	
            Category cat = (Category) ref;
            if (cat.getKey().equals(key))
                return cat;
        }
        return null;
    }

    public boolean hasCategory(Category category) {
        return isRefering("childs", category.getId());
    }

    protected Class<? extends Entity> getInfoClass(String key) {
        final Class<? extends Entity> infoClass = super.getInfoClass(key);
        if ( infoClass != null)
        {
            return infoClass;
        }
        if ( key.equals("childs"))
        {
            return Category.class;
        }
        return null;
    }
    public void addCategory(Category category) {
        checkWritable();
        Assert.isTrue(category.getParent() == null || category.getParent().equals(this)
                      ,"Category is already attached to a parent");
        
        CategoryImpl categoryImpl = (CategoryImpl)category;
        if ( resolver != null)
        {
        	Assert.isTrue( !categoryImpl.isAncestorOf( this), "Can't add a parent category to one of its ancestors.");
        }
        addId("childs", category.getId());
        categoryImpl.setParent(this);
    }

    public int getRootPathLength() {
    	Category parent = getParent();
		if ( parent == null)
		{
			return 0;
		}
		else
		{
			int parentDepth = parent.getRootPathLength();
			return parentDepth + 1;
		}
    }
    
    public int getDepth() {
        int max = 0;
        Category[] categories = getCategories();
        for (int i=0;i<categories.length;i++) {
            int depth = categories[i].getDepth();
            if (depth > max)
                max = depth;
        }
        return max + 1;
    }

    public void removeCategory(Category category) {
        checkWritable();
        if ( findCategory( category ) == null)
            return;
        removeId(category.getId());
        //if (category.getParent().equals(this))
        ((CategoryImpl)category).setParent(null);
    }

    public Category findCategory(Category copy) {
        return (Category) super.findEntity(copy);
    }

    public MultiLanguageName getName() {
        return name;
    }

    public void setReadOnly() {
        super.setReadOnly(  );
        name.setReadOnly( );
    }

    public String getName(Locale locale)
    {
        return name.getNameFromLocale(locale);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        checkWritable();
        this.key = key;
    }

    public String getPath(Category rootCategory,Locale locale) {
        StringBuffer buf = new StringBuffer();
        if (rootCategory != null && this.equals(rootCategory))
            return "";
        if (this.getParent() != null) {
            String path = this.getParent().getPath(rootCategory,locale);
            buf.append(path);
            if (path.length()>0)
                buf.append('/');
        }
        buf.append(this.getName(locale));
        return buf.toString();
    }
    
    public List<String> getKeyPath(Category rootCategory) {
    	LinkedList<String> result = new LinkedList<String>();
        if (rootCategory != null && this.equals(rootCategory))
            return result;
       
        Category cat = this;
        while (cat.getParent() != null) 
        {
        	Category parent = cat.getParent();
        	result.addFirst( parent.getKey());
        	cat = parent;
        	if ( parent == this)
        	{
        		throw new IllegalStateException("Parent added as own child");
        	}
        }
        result.add( getKey());
        return result;
    }

    public String toString() {
        MultiLanguageName name = getName();
        if (name != null) {
            return name.toString() + " ID='" + getId() + "' key='" + getKey()+"'" ;
        }  else {
            return getKey()  + " " + getId();
        }
    }


    public String getPathForCategory(Category searchCategory) throws EntityNotFoundException {
    	List<String> keyPath = getPathForCategory(searchCategory, true);
    	return getKeyPathString(keyPath);
    }

	public static String getKeyPathString(List<String> keyPath) {
		StringBuffer buf = new StringBuffer();
		for (String category:keyPath)
    	{
			buf.append('/');
    		buf.append(category);
    	}
    	if ( buf.length() > 0)
    	{
    		buf.deleteCharAt(0);
    	}
		String pathForCategory = buf.toString();
		return pathForCategory ;
	}
    
    public List<String> getPathForCategory(Category searchCategory, boolean fail) throws EntityNotFoundException {
    	LinkedList<String> result = new LinkedList<String>();
    	Category category = searchCategory;
        Category parent = category.getParent();
        if (category == this)
            return result;
        if (parent == null)
            throw new EntityNotFoundException("Category has no parents!");
        while (true) {
        	String entry ="category[key='" + category.getKey() +  "']";
            result.addFirst(entry);
            parent = category.getParent();
            category = parent;
            if (parent == null)
            {
                if ( fail)
                {
                    throw new EntityNotFoundException("Category not found!" + searchCategory);
                }
                return null;
            }
            if (parent.equals(this))
                break;
        }
        return result;
    }

    public Category getCategoryFromPath(String path) throws EntityNotFoundException {
        if ( path.trim().isEmpty() )
        {
            return this;
        }
        int start = 0;
        int end = 0;
        int pos = 0;
        Category category = this;
        while (category != null) {
            start = path.indexOf("'",pos) + 1;
            if (start==0)
                break;
            end = path.indexOf("'",start);
            if (end < 0)
                throw new EntityNotFoundException("Invalid xpath expression: " + path);
            String key = path.substring(start,end);
            category = category.getCategory(key);
            pos = end + 1;
        }
        if ( category == this)
        {
            throw new EntityNotFoundException("could not resolve xpath: " + path);
        }
        if (category == null)
            throw new EntityNotFoundException("could not resolve category xpath expression: " + path);
        return category;
    }

    public Category findCategory(Object copy) {
        return (Category) super.findEntity((Entity)copy);
    }

    public String getAnnotation(String key) {
        return annotations.get(key);
    }

    public String getAnnotation(String key, String defaultValue) {
        String annotation = getAnnotation( key );
        return annotation != null ? annotation : defaultValue;
    }

    public void setAnnotation(String key,String annotation) throws IllegalAnnotationException {
        checkWritable();
        if (annotation == null) {
            annotations.remove(key);
            return;
        }
        annotations.put(key,annotation);
    }

    public String[] getAnnotationKeys() {
        return annotations.keySet().toArray(RaplaObject.EMPTY_STRING_ARRAY);
    }

    @SuppressWarnings("unchecked")
	public Category clone() {
        CategoryImpl clone = new CategoryImpl();
        super.deepClone(clone);
        clone.name = (MultiLanguageName) name.clone();
        clone.annotations = (HashMap<String,String>) ((HashMap<String,String>)annotations).clone();
        clone.key = key;
        clone.lastChanged = lastChanged;
        clone.createDate = createDate;
        return clone;
    }
    
    public int compareTo(Object o) {
        if ( o == this )
        {
            return 0;
        }
        if ( equals( o ))
        {
        	return 0;
        }
        Category c1= this;
        Category c2= (Category) o;
        if ( c1.isAncestorOf( c2))
        {
        	return -1;
        }
        if ( c2.isAncestorOf( c1))
        {
        	return 1;
        }
        while ( c1.getRootPathLength() > c2.getRootPathLength())
        {
        	c1 = c1.getParent();
        }
        while ( c2.getRootPathLength() > c1.getRootPathLength())
        {
        	c2 = c2.getParent();
        }
        while ( c1.getParent() != null && c2.getParent() != null && (!c1.getParent().equals( c2.getParent())))
        {
        	c1 = c1.getParent();
        	c2 = c2.getParent();
        }
        //now the two categories have the same parent
        if ( c1.getParent() == null || c2.getParent() == null)
        {
            // they are not under the same super category, so compare by id
            //return super.compareTo(o);
            return compare_((CategoryImpl)c1, (CategoryImpl)c2);
        }
        CategoryImpl parent = (CategoryImpl) c1.getParent();
        CategoryImpl parent2 = (CategoryImpl) c2.getParent();
        Assert.isTrue(parent.equals( parent2 ));

        Collection<Category> categories =parent.getCategoryList();
        for ( Category category: categories)
        {
            if ( category.equals( c1))
            {
                return -1;
            }
            if ( category.equals( c2))
            {
                return 1;
            }
        }
        return super.compareTo( o);
   }


    /*
    public void replace(Category category) {
		String id = category.getId();
		CategoryImpl existingEntity = (CategoryImpl) findEntityForId(id);
		if (  existingEntity != null)
		{
		    LinkedHashSet<CategoryImpl> newChilds = new LinkedHashSet<CategoryImpl>();
			for ( CategoryImpl child: childs)
			{
				newChilds.add(  ( child != existingEntity) ? child: (CategoryImpl)category);
			}
			childs = newChilds;
		}
	}
	*/

	
}


