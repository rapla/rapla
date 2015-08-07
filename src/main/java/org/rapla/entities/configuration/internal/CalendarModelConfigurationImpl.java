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
package org.rapla.entities.configuration.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.iterator.IterableChain;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.ClassificationFilterImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.framework.RaplaException;


public class CalendarModelConfigurationImpl extends AbstractClassifiableFilter implements CalendarModelConfiguration
{
   // Don't forget to increase the serialVersionUID when you change the fields
   private static final long serialVersionUID = 1;
   List<String> selected;
   List<String> typeList;
   String title;
   Date startDate;
   Date endDate;
   Date selectedDate;
   String view;
   Map<String,String> optionMap;
   boolean defaultEventTypes;
   boolean defaultResourceTypes;
   boolean resourceRootSelected;
    
   public CalendarModelConfigurationImpl( Collection<String> selected,Collection<RaplaType> idTypeList,boolean resourceRootSelected, ClassificationFilter[] filter, boolean defaultResourceTypes, boolean defaultEventTypes,String title, Date startDate, Date endDate, Date selectedDate,String view,Map<String,String> extensionMap) {
	   if (selected != null)
	   {
	       this.selected = Collections.unmodifiableList(new ArrayList<String>(selected));
	       typeList = new ArrayList<String>();
	       for ( RaplaType type:idTypeList)
	       {
	           typeList.add(type.getLocalName());
	       }
	   }
	   else
	   {
	       this.selected = Collections.emptyList();
	       typeList = Collections.emptyList();
	   }
	   
       this.view = view;
       this.resourceRootSelected = resourceRootSelected;
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
       Map<String,String> map= new LinkedHashMap<String,String>();
       if ( extensionMap != null)
       {
           map.putAll(extensionMap);
       }
       this.optionMap = Collections.unmodifiableMap( map);
   }
   

   CalendarModelConfigurationImpl() {
   }
   
   @Override
   public boolean isResourceRootSelected() {
	   return resourceRootSelected;
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

    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() {
        Iterable<ReferenceInfo> references = super.getReferenceInfo();
        List<ReferenceInfo> selectedInfo = new ArrayList<ReferenceInfo>();
        int size = selected.size();
        for ( int i = 0;i<size;i++)
        {
            String id = selected.get(0);
            String localname = typeList.get(0);
            Class<? extends Entity> type = null;
            RaplaType raplaType;
            try {
                raplaType = RaplaType.find(localname);
            }
            catch (RaplaException ex)
            {
                throw new IllegalArgumentException(ex.getMessage());
            }
            try {
                Class typeClass = raplaType.getTypeClass();
                //if ( Entity.class.isAssignableFrom(typeClass ))
                {
                    @SuppressWarnings("unchecked")
                    Class<? extends Entity> casted = (Class<? extends Entity>)typeClass;
                    type = casted;
                }
                ReferenceInfo referenceInfo = new ReferenceInfo(id, type);
                selectedInfo.add( referenceInfo);    
            } catch ( ClassCastException e) {
            }
            
        }
        return new IterableChain<ReferenceInfo>(references, selectedInfo);
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
    
	static private void copy(CalendarModelConfigurationImpl source,CalendarModelConfigurationImpl dest) {
    	 dest.view = source.view;
         dest.defaultEventTypes = source.defaultEventTypes;
         dest.defaultResourceTypes = source.defaultResourceTypes;
         dest.title = source.title;
         dest.startDate = source.startDate;
         dest.endDate = source.endDate;
         dest.selectedDate = source.selectedDate;
         dest.resourceRootSelected = source.resourceRootSelected;
         dest.setResolver( source.resolver);
         List<ClassificationFilterImpl> newFilter = new ArrayList<ClassificationFilterImpl>();
         for ( ClassificationFilterImpl f: source.classificationFilters)
         {
        	 ClassificationFilterImpl clone = f.clone();
        	 newFilter.add( clone);
         }
         dest.setClassificationFilter(newFilter  );
         dest.selected = new ArrayList<String>(source.selected);
         dest.typeList = new ArrayList<String>(source.typeList);
         LinkedHashMap<String, String> optionMap = new LinkedHashMap<String,String>();
         optionMap.putAll(source.optionMap);
         dest.optionMap = Collections.unmodifiableMap(optionMap); 
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

	public static boolean canReference(RaplaType raplaType) 
	{
	    return raplaType == DynamicType.TYPE || raplaType == Allocatable.TYPE || raplaType == User.TYPE;
    }
	
	public List<String> getSelectedIds()
	{
		return selected;
	}
	
	public String toString()
	{
		return super.toString() + ",selected=" + selected; 
	}


    @Override
    public CalendarModelConfiguration cloneWithNewOptions(Map<String, String> newMap) {
        CalendarModelConfigurationImpl clone = (CalendarModelConfigurationImpl) deepClone();
        clone.optionMap = Collections.unmodifiableMap( newMap);
        return clone;
    }


	

}
