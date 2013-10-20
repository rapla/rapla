/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas, Frithjof Kurtz                  |
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.rapla.components.util.iterator.ArrayIterator;
import org.rapla.components.util.iterator.NestedIterator;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.ClassificationFilterImpl;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;

public abstract class AbstractClassifiableFilter  implements EntityReferencer, DynamicTypeDependant, Serializable
{
    private static final long serialVersionUID = 1L;
    ClassificationFilter[] classificationFilters;
    
    AbstractClassifiableFilter() {
    	classificationFilters = new ClassificationFilter[0];
    }
    public void resolveEntities( EntityResolver resolver) throws EntityNotFoundException {
        for (int i=0;i<classificationFilters.length;i++) {
            ((EntityReferencer)classificationFilters[i]).resolveEntities( resolver );
        }
    }

    public boolean isRefering(RefEntity<?> object) {
        for (int i=0;i<classificationFilters.length;i++)
            if (((ClassificationFilterImpl)classificationFilters[i]).isRefering(object))
                return true;
        return false;
    }

    public Iterator<RefEntity<?>> getReferences() {
        Iterator<ClassificationFilter> classificatonFilterIterator = new ArrayIterator<ClassificationFilter>(classificationFilters);
        return new NestedIterator<RefEntity<?>>(classificatonFilterIterator) {
                public Iterator<RefEntity<?>> getNestedIterator(Object obj) {
                    return ((ClassificationFilterImpl)obj).getReferences();
                }
            };
    }


    public void setClassificationFilter(ClassificationFilter[] classificationFilters) {
        if ( classificationFilters != null)
            this.classificationFilters = classificationFilters;
        else
            this.classificationFilters = ClassificationFilter.CLASSIFICATIONFILTER_ARRAY;
    }

    public boolean needsChange(DynamicType type) {
        ClassificationFilter[] filters = getFilter();
        for (int i=0;i<filters.length;i++) {
            ClassificationFilterImpl filter = (ClassificationFilterImpl)filters[i];
            if (filter.needsChange(type))
                return true;
        }
        return false;
    }

    public void commitChange(DynamicType type) {
        ClassificationFilter[] filters = getFilter();
        for (int i=0;i<filters.length;i++) {
            ClassificationFilterImpl filter = (ClassificationFilterImpl)filters[i];
            if (filter.getType().equals(type))
                filter.commitChange(type);
        }
    }
    
    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException 
    {
        ClassificationFilter[] filters = getFilter();
        List<ClassificationFilter> newFilter = new ArrayList<ClassificationFilter>(Arrays.asList( filters));
        for (Iterator<ClassificationFilter> f=newFilter.iterator();f.hasNext();) {
            ClassificationFilter filter = f.next();
            if (filter.getType().equals(type))
            {
                f.remove();
                break;
            }
        }
        classificationFilters = newFilter.toArray( ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
 
    }

    public ClassificationFilter[] getFilter() {
        return classificationFilters;
    }
    
    

}







