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

import org.jetbrains.annotations.NotNull;
import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.PeriodModel;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;


class PeriodModelImpl implements PeriodModel
{
    TreeSet<PeriodImpl> m_periods = createPeriodSet();

    @NotNull
    static private TreeSet<PeriodImpl> createPeriodSet() {
        return new TreeSet<>((o1, o2) -> -1 * o1.compareTo(o2));
    }

    StorageOperator operator;
    Period defaultPeriod;
    Set<Category> categories;

    PeriodModelImpl( StorageOperator query,Category[] keys) throws RaplaException {
        this.operator = query;
        this.categories = new HashSet<>(Arrays.asList(keys));
        update();
    }

    public void update() throws RaplaException {
        TreeSet<PeriodImpl> newSet = createPeriodSet();
        DynamicType type = operator.getDynamicType(StorageOperator.PERIOD_TYPE);
        ClassificationFilter[] filters = type.newClassificationFilter().toArray();
        Collection<Allocatable> allocatables = operator.getAllocatables( filters);
        final Attribute categoryAtt = type.getAttribute("category");
        for ( Allocatable alloc:allocatables)
        {
        	Classification classification = alloc.getClassification();
        	String name = (String)classification.getValue("name");
			Date start = (Date) classification.getValue("start");
			Date end = (Date) classification.getValue("end");
            final Collection<Category> categories = (Collection)classification.getValues(categoryAtt);
            if ( !machtesKey(categories))
            {
                continue;
            }
            PeriodImpl period = new PeriodImpl(name,start, DateTools.fillDate(end), alloc.getId(), new LinkedHashSet<>(categories));
            newSet.add(period);
        }
        m_periods = newSet;
    }

    private boolean machtesKey(Collection<Category> categories)
    {
        if ( this.categories.size()  == 0)
        {
            return categories.size() == 0;
        }
        for (Category category:categories)
        {
            if ( this.categories.contains(category))
            {
                return true;
            }
        }
        return  false;
    }

    public void update(Collection<Entity> updatedEntities, Collection<ReferenceInfo> toRemove) throws RaplaException
	{

    	if (isPeriodModified(updatedEntities, toRemove))
    	{
    		update();
    	}
	}

	private boolean isPeriodModified(Collection<Entity> updatedEntities, Collection<ReferenceInfo> toRemove) {
		for (Entity changed:updatedEntities)
		{
			if ( isPeriod( changed))
			{
				return true;
			}
		}
		
		for (ReferenceInfo removed:toRemove)
		{
			if ( containsPeriodId(removed.getId()))
			{
				return true;
			}
		}
		return false;
	}

    private boolean containsPeriodId(String id) {
        for (PeriodImpl period:m_periods)
        {
            if ( period.getId().equals( id))
            {
                return true;
            }
        }
        return false;
    }


    static private boolean isPeriod(Entity entity) {
    	if  ( entity.getTypeClass() != Allocatable.class)
    	{
    		return false;
    	}
    	Allocatable alloc = (Allocatable) entity;
    	Classification classification = alloc.getClassification();
    	if ( classification == null)
    	{
    		return false;
    	}
        return classification.getType().getKey().equals(StorageOperator.PERIOD_TYPE);
    }

    /** returns the first matching period or null if no period matches.*/
    public Period getPeriodFor(Date date) {
        if (date == null)
            return null;

        final Period nearestPeriodForDate = getNearestPeriodForDate(date);
        if ( nearestPeriodForDate != null && nearestPeriodForDate.contains( date))
        {
            return  nearestPeriodForDate;
        }
        return null;
    }

    static private long diff(Date d1,Date d2) {
        long diff = d1.getTime()-d2.getTime();
        if (diff<0)
           diff = diff * -1;
        return diff;
    }

    public Period getNearestPeriodForDate(Date date) {
        return getNearestPeriodForStartDate( m_periods, date, null);
    }

    public Period getNearestPeriodForStartDate(Date date) {
        return getNearestPeriodForStartDate( new TimeInterval(date, null));
    }

    public Period getNearestPeriodForStartDate(TimeInterval interval)
    {
        Date date = interval.getStart();
        Date endDate = interval.getEnd();
        return getNearestPeriodForStartDate( getPeriodsFor( date ), date, endDate);
    }

    public Period getNearestPeriodForEndDate(Date date) {
        return getNearestPeriodForEndDate( getPeriodsFor( date ), date);
    }

    static private Period getNearestPeriodForStartDate(Collection<? extends Period> periodList, Date date, Date endDate) {
        Period result = null;
        long min_from_start=Long.MAX_VALUE, min_from_end=0;
        long from_start, from_end=0;
        Iterator<? extends Period> it = periodList.iterator();
        while (it.hasNext()) 
        {
            Period period = it.next();
            if ( period == null) 
            { // EXCO: Why this test ?
            	continue;
            }
            if ( period.getStart() != null && period.getEnd() != null)
            {
        	    from_start = diff(period.getStart(),date);
        	    if ( endDate != null )
                {
        	        from_end = Math.abs(diff(period.getEnd(), endDate));
                }
        	    if (    from_start < min_from_start	
                    || (from_start == min_from_start && from_end < min_from_end)
        		  ) 
               {
                   min_from_start = from_start;
                   min_from_end   = from_end;
                   result = period;
               }
            }
            else if ( result == null)
            {
                result =  period;
            }
        }
        return result;
    }

    static private Period getNearestPeriodForEndDate(Collection<Period> periodList, Date date) {
        Period result = null;
        long min=-1;
        Iterator<Period> it = periodList.iterator();
        while (it.hasNext()) {
            Period period = it.next();
            if (min == -1 ) {
                if ( period.getEnd() != null)
                {
                    min = diff(period.getEnd(),date);
                }
                result = period;
            }
            if (period.getEnd() != null && diff(period.getEnd(),date) < min) {
                min = diff(period.getEnd(),date);
                result = period;
            }
        }
        return result;
    }


    /** return all matching periods.*/
    public List<Period> getPeriodsFor(Date date) {
        ArrayList<Period> list = new ArrayList<>();
        if (date == null)
            return list;

        for ( Period period:m_periods)
        {
            if ( period.contains( date))
            {
                list.add( period);
            }
        }
        return list;
    }

    public List<Period> getPeriodsFor(TimeInterval interval) {
        ArrayList<Period> list = new ArrayList<>();

        for ( Period period:m_periods)
        {
            if ( period.getInterval().overlaps( interval))
            {
                list.add( period);
            }
        }
        return list;
    }

    public int getSize() {
        Assert.notNull(m_periods,"Componenet not setup!");
        return m_periods.size();
    }

    public Period[] getAllPeriods() {
    	Period[] sortedPriods = m_periods.toArray( Period.PERIOD_ARRAY);
        return sortedPriods;
    }


}



