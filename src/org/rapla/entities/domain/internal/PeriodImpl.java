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
package org.rapla.entities.domain.internal;
import java.util.Date;
import java.util.Locale;

import org.rapla.components.util.DateTools;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Period;
import org.rapla.entities.storage.internal.SimpleEntity;

public class PeriodImpl extends SimpleEntity<Period>
    implements
        Period
{
    private final static long WEEK_MILLIS= DateTools.MILLISECONDS_PER_WEEK;

    String name;
    Date start;
    Date end;

    public PeriodImpl() {

    }

    public PeriodImpl(Date start, Date end) {
        this.start = start;
        this.end = end;
    }

    final public RaplaType<Period> getRaplaType() {return TYPE;}

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        checkWritable();
        this.start = start;
    }

    public Date getEnd() {
        return end;
    }
    public void setEnd(Date end) {
        checkWritable();
        this.end = end;
    }

    public int getWeeks()
    {
    	if ( end == null || start == null)
    	{
    		return -1;
    	}
    	long diff= end.getTime()-start.getTime();
        return (int)(((diff-1)/WEEK_MILLIS )+ 1);
    }

    public String getName(Locale locale) {
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        checkWritable();
        this.name = name;
    }

    public boolean contains(Date date) {
        return ((end == null || date.before(end))&& (start == null || !date.before(start)));
    }

    public String toString() {
        return getName() + " " + getStart() + " - " + getEnd();
    }

    public int compareTo_(Date date) {
        int result = getEnd().compareTo(date);
        if (result == 0)
            return 1;
        else
            return result;
    }

    public int compareTo(Period period) {
        int result = getStart().compareTo(period.getStart());
        if (result != 0) return result;

        if (equals(period))
            return 0;

        return (hashCode() < period.hashCode()) ? -1 : 1;
    }


    static private void copy(PeriodImpl source,PeriodImpl dest) {
        dest.start = source.start;
        dest.end = source.end;
        dest.name = source.name;
    }

    @SuppressWarnings("unchecked")
	public void copy(Period obj) {
        super.copy((SimpleEntity<Period>)obj);
        copy((PeriodImpl) obj,this);
    }

    public Period deepClone() {
        PeriodImpl clone = new PeriodImpl();
        super.deepClone(clone);
        copy(this,clone);
        return clone;
    }

}




