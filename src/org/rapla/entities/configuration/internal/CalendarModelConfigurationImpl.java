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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.ClassificationFilterImpl;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.EntityResolver;


public class CalendarModelConfigurationImpl extends AbstractClassifiableFilter implements CalendarModelConfiguration
{
   // Don't forget to increase the serialVersionUID when you change the fields
   private static final long serialVersionUID = 1;
   List<String> selected;
   String title;
   Date startDate;
   Date endDate;
   Date selectedDate;
   String view;
   Map<String,String> optionMap;
   boolean defaultEventTypes;
   boolean defaultResourceTypes;
    
   public CalendarModelConfigurationImpl( Collection<String> selected, ClassificationFilter[] filter, boolean defaultResourceTypes, boolean defaultEventTypes,String title, Date startDate, Date endDate, Date selectedDate,String view,Map<String,String> extensionMap) {
	   this.selected = selected != null ? new ArrayList<String>(selected) : new ArrayList<String>();
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
       this.optionMap = extensionMap;
       if (optionMap == null)
       {
           this.optionMap= new LinkedHashMap<String,String>();
       }
   }

   CalendarModelConfigurationImpl() {
   }

   public void setResolver( EntityResolver resolver)  {
       super.setResolver( resolver );
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

	public Collection<Entity> getSelected() {
		ArrayList<Entity> result = new ArrayList<Entity>();
		for ( String id: selected)
		{
			Entity entity = resolver.tryResolve(id);
			if ( entity != null)
			{
				result.add( entity);
			}
		}
		return result;
    }

    public Iterable<String> getReferencedIds() {
        Iterable<String> references = super.getReferencedIds();
		return new IteratorChain<String>(references, selected);
    }

    /**
     * @see org.rapla.entities.storage.EntityReferencer#isRefering(org.rapla.entities.storage.Entity)
     */
    public boolean isRefering(String object) {
        if ( selected.contains( object ) )
            return true;
        if ( super.isRefering(object)) {
            return true;
        }
        return false;
    }

    public boolean needsChange(DynamicType type) {
        if ( super.needsChange( type ))
            return true;
        return false;
    }

    public void commitChange(DynamicType type) {
        super.commitChange( type );
    }
    
    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException {
        super.commitRemove( type );
    }

    public Map<String,String> getOptionMap()
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
    
    @SuppressWarnings("unchecked")
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
         dest.selected = (List<String>)((ArrayList<String>)source.selected).clone();
         dest.optionMap = (Map<String, String>) ((HashMap)source.optionMap).clone();
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


	public List<String> getSelectedIds()
	{
		return selected;
	}

}
