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
package org.rapla.facade.internal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.components.util.Assert;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.QueryModule;
import org.rapla.framework.RaplaException;


class PeriodModelImpl implements PeriodModel,ModificationListener
{
    TreeSet<Period> m_periods = new TreeSet<Period>(new Comparator<Period>() {
            public int compare(Period o1,
                               Period o2) {
            	return -o1.compareTo(o2);
            }
        }
                                  );
    QueryModule query;
    Period defaultPeriod;

    PeriodModelImpl( ClientFacade query ) throws RaplaException {
        this.query = query;
        update();
    }

    public void update() throws RaplaException {
        Period[] periodArray = getQuery().getPeriods();
        m_periods.clear();
        m_periods.addAll(Arrays.asList(periodArray));
    }

	public void dataChanged(ModificationEvent evt) throws RaplaException 
	{
    	if (evt.isModified(Period.TYPE))
    	{
    		update();
    	}
	}
    
    protected QueryModule getQuery() {
        return query;
    }


    /** returns the first matching period or null if no period matches.*/
    public Period getPeriodFor(Date date) {
        if (date == null)
            return null;
        PeriodImpl comparePeriod = new PeriodImpl(date,date);
        comparePeriod.setId(Period.TYPE.getId( -1));
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
        return result;
    }

    static private Period getNearestPeriodForEndDate(Collection<Period> periodList, Date date) {
        Period result = null;
        long min=-1;
        Iterator<Period> it = periodList.iterator();
        while (it.hasNext()) {
            Period period = it.next();
            if (min == -1) {
                min = diff(period.getEnd(),date);
                result = period;
            }
            if (diff(period.getEnd(),date) < min) {
                min = diff(period.getStart(),date);
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

        PeriodImpl comparePeriod = new PeriodImpl(date,date);
        comparePeriod.setId( Period.TYPE.getId( -1));
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
    	return m_periods.toArray( Period.PERIOD_ARRAY);
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



