/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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

package org.rapla.plugin.abstractcalendar;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

import org.rapla.components.calendarview.Block;
import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Named;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.abstractcalendar.RaplaBuilder.BuildContext;


public abstract class AbstractRaplaBlock implements Block
{
    RaplaBuilder.RaplaBlockContext m_context;
    Date m_start;
    Date m_end;
    RaplaLocale m_raplaLocale;
    protected String timeStringSeperator = " -";
    
    protected AbstractRaplaBlock() {

    }

    public void contextualize(RaplaBuilder.RaplaBlockContext context) {
        m_context = context;
        m_raplaLocale = getBuildContext().getRaplaLocale();
    }

    public String getResourceName(Named named) {
        return named.getName(m_raplaLocale.getLocale());
    }
    
    public String getName()
    {
        return getReservationName();
    }
    
    public String getReservationName()
    {
        String name = NameFormatUtil.getName(getAppointmentBlock(), getI18n().getLocale());
//        final String appointmentBlockExtraInfo = getContext().getAppointmentBlockExtraInfo();
//        if ( appointmentBlockExtraInfo != null)
//        {
//            name = appointmentBlockExtraInfo + name; 
//        }
        return name;
    }
    
    public Date getStart()  {
        return m_start;
    }

    public Date getEnd() {
        return m_end;
    }
    
    protected I18nBundle getI18n() {
        return getBuildContext().getI18n();
    }

    public void setStart(Date start) {
        m_start = start;
    }

    public void setEnd(Date end) {
        m_end = end;
    }

    public Appointment getAppointment() {
        return getContext().getAppointment();
    }

    public Reservation getReservation()  {
        return getAppointment().getReservation();
    }

    public AppointmentBlock getAppointmentBlock()
    {
    	return getContext().getAppointmentBlock();
    }
    
    protected RaplaBuilder.RaplaBlockContext getContext() {
        return m_context;
    }
    
    public Allocatable getGroupAllocatable()
    {
    	return getContext().getGroupAllocatable();
    }

    public RaplaBuilder.BuildContext getBuildContext() {
        return getContext().getBuildContext();
    }

    public boolean isMovable() {
        return getContext().isMovable() && !isException();
    }

    public boolean startsAndEndsOnSameDay() {
        return DateTools.isSameDay(
                getAppointment().getStart().getTime()
                ,getAppointment().getEnd().getTime() -1
        )
        ;
    }

    protected String[] getColorsAsHex() {
        BuildContext buildContext = getBuildContext();
    	LinkedHashSet<String> colorList = new LinkedHashSet<String>();
        if ( buildContext.isEventColoringEnabled())
        {
        	Reservation reservation = getReservation();
        	if (reservation != null)
        	{
				String eventColor = RaplaBuilder.getColorForClassifiable( reservation );
	        	if ( eventColor != null)
	        	{
	        		colorList.add( eventColor);
	        	}
        	}
        }
    	
        if ( buildContext.isResourceColoringEnabled())
        {
	       List<Allocatable> allocatables = getContext().getSelectedAllocatables();
	       for (Allocatable  alloc:allocatables) 
	       {
	    	   String lookupColorString = buildContext.lookupColorString(alloc);
	    	   if ( lookupColorString != null)
	    	   {
	    		   colorList.add( lookupColorString);
	    	   }
	       }
        }
        if ( colorList.size() == 0)
        {
        	colorList.add(buildContext.lookupColorString(null));
        }
        return colorList.toArray(new String[] {});
    }

    protected String getTimeString(boolean small) {
        RaplaLocale loc = getBuildContext().getRaplaLocale();
        String timeString = null;
        if ( getBuildContext().isTimeVisible()) {
            timeString = "";
            // Don't show startTime if its 00:00
            /* TODO nicht sinnvoll auch 0:00 als Start und Endzeit anzuzeigen?*/
            if ( !DateTools.isMidnight(getStart()) ) {
                timeString += loc.formatTime( getStart() );
            }
            if ( !small && !DateTools.isMidnight(getEnd().getTime() + 1))  {
				timeString += timeStringSeperator;
                timeString += loc.formatTime( getEnd());
            }
        }
        return timeString;
    }

    public boolean isException() {
        return getAppointment().getRepeating() != null && getAppointment().getRepeating().isException(getStart().getTime());
    }

    public boolean isStartResizable() {
        return startsAndEndsOnSameDay() && !isException();
    }

    public boolean isEndResizable() {
        return startsAndEndsOnSameDay() && !isException();
    }

}







