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

import org.rapla.components.util.iterator.IterableChain;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.ClassificationFilterImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
    
   public CalendarModelConfigurationImpl( Collection<String> selected,Collection<Class<? extends Entity>> idTypeList,boolean resourceRootSelected, ClassificationFilter[] filter, boolean defaultResourceTypes, boolean defaultEventTypes,String title, Date startDate, Date endDate, Date selectedDate,String view,Map<String,String> extensionMap) {
	   if (selected != null)
	   {
	       this.selected = Collections.unmodifiableList(new ArrayList<>(selected));
	       typeList = new ArrayList<>();
	       for ( Class<? extends RaplaObject> type:idTypeList)
	       {
               String localname = RaplaType.getLocalName( type);
	           typeList.add(localname);
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
       List<ClassificationFilterImpl> filterList = new ArrayList<>();
       if ( filter != null)
       {
	       for ( ClassificationFilter f:filter)
	       {
	    	   filterList.add((ClassificationFilterImpl)f);
	       }
       }
       super.setClassificationFilter( filterList );
       Map<String,String> map= new LinkedHashMap<>();
       if ( extensionMap != null)
       {
           map.putAll(extensionMap);
       }
       this.optionMap = Collections.unmodifiableMap( map);
   }
   

   CalendarModelConfigurationImpl() {
   }

    @Override
    public Class<CalendarModelConfiguration> getTypeClass()
    {
        return CalendarModelConfiguration.class;
    }
   
   @Override
   public boolean isResourceRootSelected() {
	   return resourceRootSelected;
   }

   public void setResolver( EntityResolver resolver)  {
       super.setResolver( resolver );
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
		ArrayList<Entity> result = new ArrayList<>();

        int i=0;
		for ( String id: selected)
		{
            String type = typeList.get(i);
            final Class<? extends Entity> aClass;
            try
            {
                aClass = RaplaType.find(type);
            } catch (RaplaException ex)
            {
                continue;
            }
            Entity entity = resolver.tryResolve(id, aClass);
            if (entity != null)
            {
                result.add(entity);
            }
            i++;
		}
		return result;
    }

    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() {
        Iterable<ReferenceInfo> references = super.getReferenceInfo();
        List<ReferenceInfo> selectedInfo = getSelectedReferences();
        return new IterableChain<>(references, selectedInfo);
    }


    private List<ReferenceInfo> getSelectedReferences()
    {
        List<ReferenceInfo> selectedInfo = new ArrayList<>();
        int size = selected.size();
        for ( int i = 0;i<size;i++)
        {
            String id = selected.get(0);
            String localname = typeList.get(0);
            Class<? extends Entity> typeClass = null;
            try {
                typeClass = RaplaType.find(localname);
            }
            catch (RaplaException ex)
            {
                throw new IllegalArgumentException(ex.getMessage());
            }
            try {
                ReferenceInfo referenceInfo = new ReferenceInfo(id, typeClass);
                selectedInfo.add( referenceInfo);    
            } catch ( ClassCastException e) {
            }
            
        }
        return selectedInfo;
    }
    
    @Override
    public boolean needsChange(DynamicType newType) {
        final boolean needsChange = super.needsChange(newType);
        if ( needsChange )
        {
            return true;
        }
        for (Entity entity:getSelected())
        {
            if ( entity.getTypeClass() == DynamicType.class && entity.getId().equals( newType.getId()))
            {
                return true;
            }
        }
        return false;
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
         List<ClassificationFilterImpl> newFilter = new ArrayList<>();
         for ( ClassificationFilterImpl f: source.classificationFilters)
         {
        	 ClassificationFilterImpl clone = f.clone();
        	 newFilter.add( clone);
         }
         dest.setClassificationFilter(newFilter  );
         dest.selected = new ArrayList<>(source.selected);
         dest.typeList = new ArrayList<>(source.typeList);
         LinkedHashMap<String, String> optionMap = new LinkedHashMap<>(source.optionMap);
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

	public static boolean canReference(Class<? extends RaplaObject> raplaType)
	{
	    return raplaType == DynamicType.class || raplaType == Allocatable.class || raplaType == User.class;
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
    
    @Override
        public void replace(ReferenceInfo origId, ReferenceInfo newId)
        {
            super.replace(origId, newId);
            if(selected != null && selected.contains(origId.getId()))
            {
                final ArrayList<String> copy = new ArrayList<>(selected);
                final int indexOf = copy.indexOf(origId.getId());
                copy.remove(indexOf);
                if(copy.contains(newId.getId()))
                {
                    final ArrayList<String> typeCopy = new ArrayList<>(typeList);
                    typeCopy.remove(indexOf);
                    this.typeList = Collections.unmodifiableList(typeCopy);
                }
                else
                {
                    copy.add(indexOf, newId.getId());
                }
                this.selected = Collections.unmodifiableList(copy);
            }
        }

}
