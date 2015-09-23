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
package org.rapla.facade.internal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.components.util.Assert;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.QueryModule;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;


class PeriodModelImpl implements PeriodModel,ModificationListener
{
    TreeSet<Period> m_periods = new TreeSet<Period>(new Comparator<Period>() {
            public int compare(Period o1, Period o2) {
				int compareTo = o1.compareTo(o2);
				return -compareTo;
            }
        }
                                  );
    ClientFacade facade;
    Period defaultPeriod;

    PeriodModelImpl( ClientFacade query ) throws RaplaException {
        this.facade = query;
        update();
    }

    public void update() throws RaplaException {
        m_periods.clear();
        DynamicType type = facade.getDynamicType(StorageOperator.PERIOD_TYPE);
        ClassificationFilter[] filters = type.newClassificationFilter().toArray();
        Collection<Allocatable> allocatables = facade.getOperator().getAllocatables( filters);
        for ( Allocatable alloc:allocatables)
        {
        	Classification classification = alloc.getClassification();
        	String name = (String)classification.getValue("name");
			Date start = (Date) classification.getValue("start");
			Date end = (Date) classification.getValue("end");
			PeriodImpl period = new PeriodImpl(name,start,end);
        	m_periods.add(period);
        }
    }

	public void dataChanged(ModificationEvent evt) throws RaplaException 
	{
    	if (isPeriodModified(evt))
    	{
    		update();
    	}
	}

	protected boolean isPeriodModified(ModificationEvent evt) {
		for (Entity changed:evt.getChanged())
		{
			if ( isPeriod( changed))
			{
				return true;
			}
		}
		
		for (Entity changed:evt.getRemoved())
		{
			if ( isPeriod( changed))
			{
				return true;
			}
		}
		return false;
	}
    
    private boolean isPeriod(Entity entity) {
    	if  ( entity.getRaplaType() != Allocatable.TYPE)
    	{
    		return false;
    	}
    	Allocatable alloc = (Allocatable) entity;
    	Classification classification = alloc.getClassification();
    	if ( classification == null)
    	{
    		return false;
    	}
    	if (!classification.getType().getKey().equals(StorageOperator.PERIOD_TYPE))
    	{
    		return false;
    	}
    	return true;
	}

	protected QueryModule getQuery() {
        return facade;
    }


    /** returns the first matching period or null if no period matches.*/
    public Period getPeriodFor(Date date) {
        if (date == null)
            return null;
        PeriodImpl comparePeriod = new PeriodImpl("DUMMY",date,date);
        Iterator<Period> it = m_periods.tailSet(comparePeriod).iterator();
        while (it.hasNext()) {
            Period period = it.next();
            if (period.contains(date)) {
                return period;
            }
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
        return getNearestPeriodForStartDate( date, null);
    }

    public Period getNearestPeriodForStartDate(Date date, Date endDate) {
        return getNearestPeriodForStartDate( getPeriodsFor( date ), date, endDate);
    }

    public Period getNearestPeriodForEndDate(Date date) {
        return getNearestPeriodForEndDate( getPeriodsFor( date ), date);
    }

    static private Period getNearestPeriodForStartDate(Collection<Period> periodList, Date date, Date endDate) {
        Period result = null;
        long min_from_start=Long.MAX_VALUE, min_from_end=0;
        long from_start, from_end=0;
        Iterator<Period> it = periodList.iterator();
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
        ArrayList<Period> list = new ArrayList<Period>();
        if (date == null)
            return list;

        PeriodImpl comparePeriod = new PeriodImpl("DUMMY",date,date);
        SortedSet<Period> set = m_periods.tailSet(comparePeriod);
        Iterator<Period> it = set.iterator();
        while (it.hasNext()) {
            Period period = it.next();
            //System.out.println(m_periods[i].getStart() + " - " + m_periods[i].getEnd());
            if (period.contains(date)) {
                list.add( period );
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

    public Object getElementAt(int index) {
        Assert.notNull(m_periods,"Componenet not setup!");
        Iterator<Period> it = m_periods.iterator();
        for (int i=0;it.hasNext();i++) {
            Object obj = it.next();
            if (i == index)
                return obj;
        }
        return null;
    }

	

}



