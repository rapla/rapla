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

import org.rapla.components.calendarview.Block;
import org.rapla.components.util.DateTools;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.entities.Named;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.abstractcalendar.RaplaBuilder.BuildContext;

import java.util.LinkedHashSet;
import java.util.List;


import java.time.LocalDateTime;
public class RaplaBlock implements Block
{
    RaplaBuilder.RaplaBlockContext m_context;
    LocalDateTime m_start;
    LocalDateTime m_end;
    RaplaLocale m_raplaLocale;
    protected String timeStringSeperator = " -";

    public RaplaBlock(RaplaBuilder.RaplaBlockContext context, LocalDateTime start, LocalDateTime end) {
        m_start = start;
        m_end = end;
        m_context = context;
        m_raplaLocale = getBuildContext().getRaplaLocale();
    }

    public String getNameFor(Named named) {

        return named.getName(m_raplaLocale.getLocale());
    }
    
    public String getName()
    {
        return getReservationName();
    }
    
    public String getReservationName()
    {
        String name = NameFormatUtil.getName(getAppointmentBlock(), getI18n().getLocale());
        return name;
    }

    public LocalDateTime getStart()  {
        return m_start;
    }

    public LocalDateTime getEnd() {
        return m_end;
    }
    
    protected I18nBundle getI18n() {
        return getBuildContext().getI18n();
    }

    public void setStart(LocalDateTime start) {
        m_start = start;
    }

    public void setEnd(LocalDateTime end) {
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
    
    public boolean isBlockSelected()
    {
        return getContext().isBlockSelected();
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
                DateTools.toMilli(getAppointment().getStart())
                ,DateTools.toMilli(getAppointment().getEnd()) -1
        )
        ;
    }

    public boolean isRequest(Allocatable allocatable) {
        return getReservation().getRequestStatus( allocatable) != null;
    }


    public String[] getColorsAsHex() {
        BuildContext buildContext = getBuildContext();
        Reservation reservation = getReservation();
        String eventColor = reservation == null ? null : RaplaBuilder.getColorForClassifiable(reservation);
        List<Allocatable> allocatables = getContext().getSelectedAllocatables();
        java.util.List<String> resourceColors = new java.util.ArrayList<>(allocatables.size());
        for (Allocatable alloc : allocatables)
        {
            resourceColors.add(buildContext.lookupColorString(alloc));
        }
        java.util.List<String> colors = org.rapla.plugin.calendarview.BlockColors.resolve(
                buildContext.isEventColoringEnabled(),
                eventColor,
                buildContext.isResourceColoringEnabled(),
                resourceColors);
        if (colors.isEmpty())
        {
            // Swing fallback: paint with the default colour rather than
            // leaving an unrendered tile. The server-side decorator skips
            // this fallback (Angular decides the default).
            return new String[] { buildContext.lookupColorString(null) };
        }
        return colors.toArray(new String[0]);
    }

    public String getTimeString(boolean small) {
        RaplaLocale loc = getBuildContext().getRaplaLocale();
        String timeString = null;
        if ( getBuildContext().isTimeVisible()) {
            timeString = "";
            if ( !getContext().isSplitStart() ) {
                timeString = loc.formatTime( getStart() );
            }
            timeString = timeString + timeStringSeperator;
            if ( !small && !getContext().isSplitEnd())  {
                timeString = timeString + loc.formatTime( getEnd());
           }
        }
        return timeString;
    }

    public boolean isException() {
        final Repeating repeating = getAppointment().getRepeating();
        final long time = getAppointmentBlock().getStart();
        return repeating != null && repeating.isException(time);
    }

    public boolean isStartResizable() {
        return startsAndEndsOnSameDay() && !isException();
    }

    public boolean isEndResizable() {
        return startsAndEndsOnSameDay() && !isException();
    }

}







