/*--------------------------------------------------------------------------*
| Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.ClassificationFilterImpl;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.EntityResolver;


/**
 *
 * @author ckohlhaas
 * @version 1.00.00
 * @since 2.03.00
 */
public class CalendarModelConfigurationImpl extends AbstractClassifiableFilter implements CalendarModelConfiguration
{
   // Don't forget to increase the serialVersionUID when you change the fields
   private static final long serialVersionUID = 1;

   transient RaplaMapImpl<RaplaObject> selected;
   String title;
   Date startDate;
   Date endDate;
   Date selectedDate;
   String view;
   RaplaMapImpl<String> optionMap;
   boolean defaultEventTypes;
   boolean defaultResourceTypes;

   @SuppressWarnings("unchecked")
   public CalendarModelConfigurationImpl( RaplaMap<? extends RaplaObject> selected, ClassificationFilter[] filter, boolean defaultResourceTypes, boolean defaultEventTypes,String title, Date startDate, Date endDate, Date selectedDate,String view,RaplaMap<String> extensionMap) {
       this.view = view;
       this.defaultEventTypes = defaultEventTypes;
       this.defaultResourceTypes = defaultResourceTypes;
       this.title = title;
       this.startDate = startDate;
       this.endDate = endDate;
       this.selectedDate = selectedDate;
       List<ClassificationFilterImpl> filterList = new ArrayList<ClassificationFilterImpl>();
       if ( filter != null)
       {
	       for ( ClassificationFilter f:filter)
	       {
	    	   filterList.add((ClassificationFilterImpl)f);
	       }
       }
       super.setClassificationFilter( filterList );
       this.selected = (RaplaMapImpl<RaplaObject>)selected;
       if (selected == null)
       {
           this.selected = new RaplaMapImpl<RaplaObject>();
       }
       this.optionMap = (RaplaMapImpl<String>)extensionMap;
       if (optionMap == null)
       {
           this.optionMap= new RaplaMapImpl<String>();
       }
   }

   private CalendarModelConfigurationImpl() {
   }

   public void setResolver( EntityResolver resolver)  {
       super.setResolver( resolver );
       selected.setResolver( resolver );
       optionMap.setResolver( resolver );
   }


    public RaplaType<CalendarModelConfiguration> getRaplaType() {
        return TYPE;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public Date getSelectedDate() {
        return selectedDate;
    }

    public String getTitle() {
        return title;
    }

    public String getView() {
        return view;
    }

	public Collection<RaplaObject> getSelected() {
    	Collection<RaplaObject> values = selected.values();
    	return Arrays.asList(values.toArray(new RaplaObject[] {}));
    }

    public RaplaMap<RaplaObject> getSelectedMap() {
        return selected;
    }

    
    public Iterable<String> getReferencedIds() {
        Iterable<String> references = super.getReferencedIds();
		return new IteratorChain<String>(references, selected.getReferencedIds());
    }

    /**
     * @see org.rapla.entities.storage.EntityReferencer#isRefering(org.rapla.entities.storage.Entity)
     */
    public boolean isRefering(String object) {
        if ( selected.isRefering( object ) )
            return true;
        if ( super.isRefering(object)) {
            return true;
        }
        return false;
    }

    public boolean needsChange(DynamicType type) {
        if ( super.needsChange( type ))
            return true;
        return selected.needsChange( type );
    }

    public void commitChange(DynamicType type) {
        super.commitChange( type );
        selected.commitChange( type );
    }
    
    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException {
        super.commitRemove( type );
        selected.commitRemove( type );
    }

    public RaplaMap<String> getOptionMap()
    {
        return optionMap;
    }

    public boolean isDefaultEventTypes() 
    {
        return defaultEventTypes;
    }

    public boolean isDefaultResourceTypes() 
    {
        return defaultResourceTypes;
    }
    
    static private void copy(CalendarModelConfigurationImpl source,CalendarModelConfigurationImpl dest) {
    	 dest.view = source.view;
         dest.defaultEventTypes = source.defaultEventTypes;
         dest.defaultResourceTypes = source.defaultResourceTypes;
         dest.title = source.title;
         dest.startDate = source.startDate;
         dest.endDate = source.endDate;
         dest.selectedDate = source.selectedDate;
         List<ClassificationFilterImpl> newFilter = new ArrayList<ClassificationFilterImpl>();
         for ( ClassificationFilterImpl f: source.classificationFilters)
         {
        	 ClassificationFilterImpl clone = f.clone();
        	 newFilter.add( clone);
         }
         dest.setClassificationFilter(newFilter  );
         dest.selected = (RaplaMapImpl<RaplaObject>)source.selected.deepClone();
         dest.optionMap = (RaplaMapImpl<String>)source.optionMap.deepClone();
    }

	public void copy(CalendarModelConfiguration obj) 
	{
		copy((CalendarModelConfigurationImpl)obj, this);
		
	}

	public CalendarModelConfiguration deepClone() 
	{
		CalendarModelConfigurationImpl clone = new CalendarModelConfigurationImpl();
		copy(this,clone);
		return clone;
	}

	public CalendarModelConfiguration clone() {
		return deepClone();
	}



}
