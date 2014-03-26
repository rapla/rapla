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
package org.rapla;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Repeating;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;

/** default implementation of appointment formater */
public class AppointmentFormaterImpl
    implements
    AppointmentFormater
{
    I18nBundle i18n;
    RaplaLocale loc;

    public AppointmentFormaterImpl(RaplaContext context) throws RaplaException
    {
        i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);
        loc =  context.lookup(RaplaLocale.class);
    }

    protected RaplaLocale getRaplaLocale() {
        return loc;
    }

    protected I18nBundle getI18n() {
        return i18n;
    }

    protected String getString(String key) {
        return i18n.getString(key);
    }


    public String getShortSummary(Appointment appointment) {
        String time = loc.formatTime(appointment.getStart());
        Repeating repeating = appointment.getRepeating();
        final boolean wholeDaysSet = appointment.isWholeDaysSet();
        final String timeString = wholeDaysSet ? "" :" " + time;
        String weekday = loc.getWeekday(appointment.getStart());
        if (repeating != null) {
			if (repeating.isWeekly()) {
                return weekday + timeString;
            }
            if (repeating.isDaily())
                return getString("daily") + " " + time;
            if (repeating.isMonthly())
                return getWeekdayOfMonth( appointment.getStart() )  +  weekday + timeString;
            if (repeating.isYearly())
                return getDayOfMonth( appointment.getStart() )  +  loc.getMonth(appointment.getStart()) +" " + timeString;
        }
        String date = loc.formatDate(appointment.getStart());
        return weekday + " " +date + " " + timeString;
    }

    public String getVeryShortSummary(Appointment appointment) {
        Repeating repeating = appointment.getRepeating();
        if (repeating != null) {
            if (repeating.isWeekly())
                return getRaplaLocale().getWeekday(appointment.getStart());
            if (repeating.isDaily()) {
                String time = getRaplaLocale().formatTime(appointment.getStart());
                return time;
            }
            if (repeating.isMonthly())
            {
                return getRaplaLocale().getWeekday(appointment.getStart());
            }
        }
        String date = getRaplaLocale().formatDateShort(appointment.getStart());
        return date;
    }

    public String getSummary( Appointment a ) {
        StringBuffer buf = new StringBuffer();
        Repeating repeating = a.getRepeating();
        final boolean wholeDaysSet = a.isWholeDaysSet();
        Date start = a.getStart();
		Date end = a.getEnd();
		if ( repeating == null )
        {
            buf.append( loc.getWeekday( start ) );
            buf.append( ' ' );
            buf.append( loc.formatDate( start ) );
            if (!wholeDaysSet && !( end.equals( DateTools.cutDate(end)) && start.equals(DateTools.cutDate( start))))
            {
                buf.append( ' ' );
                buf.append( loc.formatTime( start ) );
                
                if ( isSameDay( start, end )   || (end.equals( DateTools.cutDate(end)) && isSameDay( DateTools.fillDate( start), end)) )
                {
                    buf.append( '-' );
                }
                else
                {
                    buf.append( " - " );
                    buf.append( loc.getWeekday( end ) );
                    buf.append( ' ' );
                    buf.append( loc.formatDate( end ) );
                    buf.append( ' ' );
                }
                buf.append( loc.formatTime( end ) );
            }
            else if ( end.getTime() - start.getTime() > DateTools.MILLISECONDS_PER_DAY)
            {
                buf.append( " - " );
                buf.append( loc.getWeekday( DateTools.addDays(end,-1 )) );
                buf.append( ' ' );
                buf.append( loc.formatDate( DateTools.addDays(end,-1 )) );
            }
        }
        else if ( repeating.isWeekly()  || repeating.isMonthly() || repeating.isYearly())
        {
            if( repeating.isMonthly())
            {
                buf.append( getWeekdayOfMonth( start ));
            }
            if (repeating.isYearly())
            {
                buf.append( getDayOfMonth( start ) );
                buf.append( loc.getMonth( start ) );
            }
            else
            {
                buf.append( loc.getWeekday( start ) );
            }
            if (wholeDaysSet)
            {
                if ( end.getTime() - start.getTime() > DateTools.MILLISECONDS_PER_DAY)
                {
                    if ( end.getTime() - start.getTime() <= DateTools.MILLISECONDS_PER_DAY * 6 )
                    {
                        buf.append( " - " );
                        buf.append( loc.getWeekday( end ) );
                    }
                    else
                    {
                        buf.append( ' ' );
                        buf.append( loc.formatDate( start ) );
                        buf.append( " - " );
                        buf.append( loc.getWeekday( end ) );
                        buf.append( ' ' );
                        buf.append( loc.formatDate( end ) );
                    }
                }
            }
            else
            {
                buf.append( ' ' );
                if ( isSameDay( start, end ) )
                {
                    buf.append( loc.formatTime( start ) );
                    buf.append( '-' );
                    buf.append( loc.formatTime( end ) );
                }
                else if ( end.getTime() - start.getTime() <= DateTools.MILLISECONDS_PER_DAY * 6 )
                {
                    buf.append( loc.formatTime( start ) );
                    buf.append( " - " );
                    buf.append( loc.getWeekday( end ) );
                    buf.append( ' ' );
                    buf.append( loc.formatTime( end ) );
                }
                else
                {
                    buf.append( loc.formatDate( start ) );
                    buf.append( ' ' );
                    buf.append( loc.formatTime( start ) );
                    buf.append( " - " );
                    buf.append( loc.getWeekday( end ) );
                    buf.append( ' ' );
                    buf.append( loc.formatDate( end ) );
                    buf.append( ' ' );
                    buf.append( loc.formatTime( end ) );
                }
            }
            
            if ( repeating.isWeekly())
            {
                buf.append( ' ' );
                buf.append( getInterval( repeating ) );
            }
            if ( repeating.isMonthly())
            {
                buf.append(" " + getString("monthly"));
            }
            if ( repeating.isYearly())
            {
                buf.append(" " + getString("yearly"));
            }
        }
        
        else if ( repeating.isDaily() )
        {
           
            long days =(end.getTime() - start.getTime()) / (DateTools.MILLISECONDS_PER_HOUR * 24 );
            if ( !a.isWholeDaysSet())
            {
                buf.append( loc.formatTime( start ) );
                if ( days <1)
                {
                    buf.append( '-' );
                    buf.append( loc.formatTime( end ) );
                }
                buf.append( ' ' );
            }
            buf.append( getInterval( repeating ) );
        }
        return buf.toString();
    }

    private String getWeekdayOfMonth( Date date )
    {
        StringBuffer b = new StringBuffer();
        int numb = DateTools.getDayOfWeekInMonth( date );
        b.append( String.valueOf(numb));
        b.append( '.');
        b.append( ' ');
        return b.toString();
    }

    private String getDayOfMonth( Date date )
    {
        StringBuffer b = new StringBuffer();
        int numb = DateTools.getDayOfMonth( date );
        b.append( String.valueOf(numb));
        b.append( '.');
        b.append( ' ');
        return b.toString();
    }

    private boolean isSameDay( Date d1, Date d2 ) {
    	return DateTools.isSameDay(d1, d2);
    }

    public String getExceptionSummary( Repeating r ) {
        StringBuffer buf = new StringBuffer();
        buf.append(getString("appointment.exceptions"));
        buf.append(": ");
        Date[] exc = r.getExceptions();
        for ( int i=0;i<exc.length;i++) {
            if (i>0)
                buf.append(", ");
            buf.append( getRaplaLocale().formatDate( exc[i] ) );
        }
        return buf.toString();
    }

    private String getInterval( Repeating r ) {
        StringBuffer buf = new StringBuffer();
        if ( r.getInterval() == 1 ) {
            buf.append( getString( r.getType().toString() ) );
        } else {
            String fString ="weekly";
            if ( r.isWeekly() ) {
                fString = getString( "weeks" );
            }
            if ( r.isDaily() ) {
                fString = getString( "days" );
            }
            buf.append( getI18n().format( "interval.format", "" + r.getInterval(), fString ) );
        }
        return buf.toString();
    }

    private boolean isPeriodicaly(Period period, Repeating r) {
        Appointment a = r.getAppointment();
        if (r.getEnd().after( period.getEnd() ) )
            return false;
        if ( r.isWeekly() )
        {
            return
               ( DateTools.cutDate(a.getStart().getTime()) - period.getStart().getTime() )
               <= DateTools.MILLISECONDS_PER_DAY * 6
               &&
               ( DateTools.cutDate(period.getEnd().getTime()) - r.getEnd().getTime() )
               <= DateTools.MILLISECONDS_PER_DAY * 6
               ;
        }
        else if ( r.isDaily() )
        {
            return
                isSameDay( a.getStart(), period.getStart() )
                &&
                isSameDay( r.getEnd(), period.getEnd() )
                ;
        }
        return false;
    }

    public String getSummary( Repeating r , List<Period> periods) {
        if ( r.getEnd() != null && !r.isFixedNumber() )
        {
            Iterator<Period> it =  periods.iterator();
            while ( it.hasNext() ) {
                Period period =  it.next();
                if ( isPeriodicaly(period, r))
                    return getI18n().format("in_period.format"
                                            ,period.getName(loc.getLocale())
                                            );
            }
        }
        return getSummary(r);
    }

    public String getSummary( Repeating r ) {
        Appointment a = r.getAppointment();
        StringBuffer buf = new StringBuffer();
        String startDate = loc.formatDate( a.getStart() );
        buf.append( getI18n().format("format.repeat_from", startDate) );
        buf.append( ' ' );
        // print end date, when end is given
        if ( r.getEnd() != null) {
            String endDate = loc.formatDate( DateTools.subDay(r.getEnd()) );
            buf.append( getI18n().format("format.repeat_until", endDate) );
            buf.append( ' ' );
        }

        // print number of repeating if number is gt 0 and fixed times
        if ( r.getNumber()>=0 && r.isFixedNumber() ) {
            buf.append( getI18n().format("format.repeat_n_times", String.valueOf(r.getNumber())) );
            buf.append( ' ' );
        }
        // print never ending if end is null
        if (r.getEnd() == null ){
            buf.append( getString("repeating.forever") );
            buf.append( ' ' );
        }

        return buf.toString();
    }

}
