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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLCompactWeekView;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;

public class HTMLCompactWeekViewPage extends AbstractHTMLCalendarPage
{

    public HTMLCompactWeekViewPage( RaplaContext context, CalendarModel calendarModel) 
    {
        super( context,  calendarModel);
    }

    protected AbstractHTMLView createCalendarView() {
        HTMLCompactWeekView weekView = new HTMLCompactWeekView()
        {
        	@Override
        	public void rebuild() {
        		 String weeknumberString = MessageFormat.format(getString("calendarweek.abbreviation"), getStartDate());
        		 setWeeknumber(weeknumberString);
        		 super.rebuild();
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
        if ( builder.getAllocatables().size() > 0) {
            strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() );
            strategy.setAllocatables( builder.getAllocatables() ) ;
        } else {
            // put all Allocatables in the same group
            strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() ) {
                protected Collection<List<Block>> group(List<Block> blockList) {
                    ArrayList<List<Block>> list = new ArrayList<List<Block>>();
                    list.add( blockList );
                    return list;
                }
            };
        }
        strategy.setFixedSlotsEnabled( true );
        builder.setBuildStrategy( strategy );

        List<Allocatable> allocatables = builder.getAllocatables();
        String[] slotNames = new String[ allocatables.size() ];
        for (int i = 0; i < slotNames.length; i++ ) {
            Allocatable allocatable =  allocatables.get( i );
            String slotName = allocatable.getName( getRaplaLocale().getLocale() );
            slotNames[i] = XMLWriter.encode( slotName );
        }
        ((HTMLCompactWeekView)view).setSlots( slotNames );
        return builder;
    }

    protected int getIncrementSize() {
        return Calendar.WEEK_OF_YEAR;
    }

}

