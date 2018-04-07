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
package org.rapla.plugin.compactweekview.server;

import org.rapla.RaplaResources;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLCompactWeekView;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.compactweekview.CompactWeekviewPlugin;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Extension(provides = HTMLViewPage.class,id= CompactWeekviewPlugin.COMPACT_WEEK_VIEW)
public class HTMLCompactWeekViewPage extends AbstractHTMLCalendarPage implements  HTMLViewPage
{
    @Inject
    public HTMLCompactWeekViewPage(RaplaLocale raplaLocale, RaplaResources raplaResources, RaplaFacade facade, Logger logger,
            AppointmentFormater appointmentFormater)
    {
        super(raplaLocale, raplaResources, facade, logger, appointmentFormater);
    }

    protected AbstractHTMLView createCalendarView() {
        HTMLCompactWeekView weekView = new HTMLCompactWeekView()
        {
        	@Override
        	public void rebuild(Builder b) {
        		 String weeknumberString = getI18n().calendarweek( getStartDate());
        		 setWeeknumber(weeknumberString);
        		 super.rebuild(b);
        	}
        };
       return weekView;
    }
    
    @Override
    protected void configureView() throws RaplaException {
        CalendarOptions opt = getCalendarOptions();
        Set<Integer> excludeDays = opt.getExcludeDays();
        view.setExcludeDays( excludeDays );
        view.setDaysInView( opt.getDaysInWeekview());
        int firstDayOfWeek = opt.getFirstDayOfWeek();
		view.setFirstWeekday( firstDayOfWeek);
        view.setExcludeDays( excludeDays );
    }

    protected RaplaBuilder createBuilder() throws RaplaException {
        RaplaBuilder builder = super.createBuilder();

        builder.setSmallBlocks( true );
        builder.setSplitByAllocatables( true );

        GroupAllocatablesStrategy strategy;
        List<Allocatable> allocatables = getSortedAllocatables();
        if ( allocatables.size() > 0) {
            strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() );
            strategy.setAllocatables( allocatables ) ;
        } else {
            // put all Allocatables in the same group
            strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() ) {
                protected Collection<List<Block>> group(List<Block> blockList) {
                    ArrayList<List<Block>> list = new ArrayList<>();
                    list.add( blockList );
                    return list;
                }
            };
        }
        strategy.setFixedSlotsEnabled( true );
        builder.setBuildStrategy( strategy );

        String[] slotNames = new String[ allocatables.size() ];
        Iterator<Allocatable> it = allocatables.iterator();
        for (int i = 0; i < slotNames.length; i++ ) {
            Allocatable allocatable =  it.next();
            String slotName = allocatable.getName( getRaplaLocale().getLocale() );
            slotNames[i] = XMLWriter.encode( slotName );
        }
        ((HTMLCompactWeekView)view).setSlots( slotNames );
        return builder;
    }

    protected DateTools.IncrementSize getIncrementSize() {
        return DateTools.IncrementSize.WEEK_OF_YEAR;
    }

}

