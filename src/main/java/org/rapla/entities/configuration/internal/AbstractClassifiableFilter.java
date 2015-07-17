/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Frithjof Kurtz                  |
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
package org.rapla.entities.configuration.internal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.rapla.components.util.iterator.NestedIterable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.ClassificationFilterImpl;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;

public abstract class AbstractClassifiableFilter  implements EntityReferencer, DynamicTypeDependant, Serializable
{
    private static final long serialVersionUID = 1L;
    List<ClassificationFilterImpl> classificationFilters;
    protected transient EntityResolver resolver;
    
    AbstractClassifiableFilter() {
    	classificationFilters = new ArrayList<ClassificationFilterImpl>();
    }
   
    public void setResolver( EntityResolver resolver) {
    	this.resolver = resolver;
        for (ClassificationFilterImpl filter:classificationFilters) {
            filter.setResolver( resolver );
        }
    }

    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() {
        Iterable<ClassificationFilterImpl> classificatonFilterIterator = classificationFilters;
        return new NestedIterable<ReferenceInfo,ClassificationFilterImpl>(classificatonFilterIterator) {
                public Iterable<ReferenceInfo> getNestedIterable(ClassificationFilterImpl obj) {
                    return obj.getReferenceInfo();
                }
            };
    }

    public void setClassificationFilter(List<ClassificationFilterImpl> classificationFilters) {
        if ( classificationFilters != null)
            this.classificationFilters = classificationFilters;
        else
            this.classificationFilters = Collections.emptyList();
    }

    public boolean needsChange(DynamicType type) {
        for (ClassificationFilterImpl filter:classificationFilters)
        {
            if (filter.needsChange(type))
                return true;
        }
        return false;
    }

    public void commitChange(DynamicType type) {
        for (ClassificationFilterImpl filter:classificationFilters)
        {
            if (filter.getType().equals(type))
                filter.commitChange(type);
        }
    }
    
    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException 
    {
    	boolean removed = false;
        List<ClassificationFilterImpl> newFilter = new ArrayList<ClassificationFilterImpl>( classificationFilters);
        for (Iterator<ClassificationFilterImpl> f=newFilter.iterator();f.hasNext();) {
            ClassificationFilterImpl filter = f.next();
            if (filter.getType().equals(type))
            {
            	removed = true;
                f.remove();
            }
        }
        if ( removed)
        {
        	classificationFilters = newFilter;
        }
    }

    public ClassificationFilter[] getFilter() {
        return classificationFilters.toArray( ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
    }
    
    public String toString()
    {
    	return classificationFilters.toString();
    	
    }

}







