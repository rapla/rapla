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

import org.rapla.components.util.DateTools;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/** <strong>WARNING!!</strong> This class should not be public to the outside. Please use the interface */
@DefaultImplementation(of = CalendarOptions.class, context = InjectionContext.all)
@Singleton
public class CalendarOptionsImpl implements CalendarOptions {
    public final static TypedComponentRole<RaplaConfiguration> CALENDAR_OPTIONS= new TypedComponentRole<>("org.rapla.calendarview");
    public final static TypedComponentRole<Boolean> SHOW_CONFLICT_WARNING = new TypedComponentRole<>("org.rapla.conflict.showWarning");
    public final static TypedComponentRole<Boolean> SHOW_HOLIDAY_WARNING = new TypedComponentRole<>("org.rapla.holiday.showWarning");
    public final static TypedComponentRole<Boolean> SHOW_HOLIDAY_WARNING_SINGLE_APPOINTMENT = new TypedComponentRole<>("org.rapla.holiday.showWarningSingle");
    public final static TypedComponentRole<Boolean> SHOW_NOT_IN_CALENDAR_WARNING = new TypedComponentRole<>("org.rapla.calendar.showNotInCalendarWarning");
    public final static TypedComponentRole<Boolean> SHOW_ABORT_EDIT_WARNING = new TypedComponentRole<>("org.rapla.abortEditWarning");

    public static final String WORKTIME = "worktime";
    public static final String EXCLUDE_DAYS = "exclude-days";
    public static final String WEEKSTART = "exclude-days";
    public static final String ROWS_PER_HOUR = "rows-per-hour";
    public final static String EXCEPTIONS_VISIBLE="exceptions-visible";
    public final static String COMPACT_COLUMNS="compact-columns";
    public final static String COLOR_BLOCKS="color";
    public final static String COLOR_RESOURCES="resources";
    public final static String COLOR_EVENTS="reservations";
    public final static String COLOR_EVENTS_AND_RESOURCES="reservations_and_resources";
    public final static String COLOR_NONE="disabled";
    
    public final static String DAYS_IN_WEEKVIEW = "days-in-weekview";
    public final static String FIRST_DAY_OF_WEEK = "first-day-of-week";
    public final static String MIN_BLOCK_WIDTH = "minimum-block-width";
    
    /** The following fields will be replaced in version 1.7 as they don't refer to the calendar but to the creation of appointment. Please don't use*/ 
    public final static String REPEATING="repeating"; 
    public final static String NTIMES="repeating.ntimes"; 
    public final static String CALNAME="calendar-name"; 
    public final static String REPEATINGTYPE="repeatingtype"; 
    
    public final static String NON_FILTERED_EVENTS= "non-filtered-events";
    public final static String NON_FILTERED_EVENTS_TRANSPARENT= "transparent";
    public final static String NON_FILTERED_EVENTS_HIDDEN= "not_visible";
    
    int nTimes; 
    /** Ends here*/
  
    Set<Integer> excludeDays = new LinkedHashSet<>();

    final int maxtimeMinutes;
    final int mintimeMinutes;
    final int rowsPerHour;
    boolean exceptionsVisible;
    boolean compactColumns = false;  // use for strategy.setFixedSlotsEnabled
    final Configuration config;
    final String colorField;
    final boolean nonFilteredEventsVisible;
   
    final int daysInWeekview;
	final int firstDayOfWeek;
	final private int minBlockWidth;

    @Inject
	public CalendarOptionsImpl() throws RaplaInitializationException {
	    this(new DefaultConfiguration());
	}
	
    public CalendarOptionsImpl(Configuration config ) throws RaplaInitializationException {
        this.config = config;
        Configuration worktime = config.getChild( WORKTIME );
        String worktimesString = worktime.getValue("8-18");
        int minusIndex = worktimesString.indexOf("-");
        try {
    		if ( minusIndex >= 0)
    		{
    			String firstPart = worktimesString.substring(0,minusIndex);
    			String secondPart = worktimesString.substring(minusIndex+ 1);
				mintimeMinutes = parseMinutes( firstPart );
				maxtimeMinutes = parseMinutes( secondPart );
    		}
    		else
    		{
    			mintimeMinutes = parseMinutes( worktimesString);
                maxtimeMinutes = -1;
    		}
        } catch ( NumberFormatException e ) {
            throw new RaplaInitializationException( "Invalid time in " + worktime  + ". use the following format : 8-18 or 8:30-18:00!");
        }

        Configuration exclude = config.getChild( EXCLUDE_DAYS );
        String excludeString = exclude.getValue("");
        if ( excludeString.trim().length() > 0)
        {
		    String[] tokens = excludeString.split(",");
		    for ( String token:tokens)
		    {
		        String normalizedToken = token.toLowerCase().trim();
		        try {
		            excludeDays.add( Integer.valueOf(normalizedToken) );
		        } catch ( NumberFormatException e ) {
		            throw new RaplaInitializationException("Invalid day in " + excludeDays  + ". only numbers are allowed!");
		        }
		    } // end of while ()
        }
        int firstDayOfWeekDefault = DateTools.MONDAY;
		firstDayOfWeek = config.getChild(FIRST_DAY_OF_WEEK).getValueAsInteger(firstDayOfWeekDefault);
		
		daysInWeekview = config.getChild(DAYS_IN_WEEKVIEW).getValueAsInteger( 7 );
        
        rowsPerHour = config.getChild( ROWS_PER_HOUR ).getValueAsInteger( 4 );
        exceptionsVisible = config.getChild(EXCEPTIONS_VISIBLE).getValueAsBoolean(false);
        colorField = config.getChild( COLOR_BLOCKS ).getValue( COLOR_EVENTS_AND_RESOURCES );
        minBlockWidth = config.getChild( MIN_BLOCK_WIDTH).getValueAsInteger(0);
        
        nTimes = config.getChild( NTIMES ).getValueAsInteger( 1 );
        nonFilteredEventsVisible = config.getChild( NON_FILTERED_EVENTS).getValue(NON_FILTERED_EVENTS_TRANSPARENT).equals( NON_FILTERED_EVENTS_TRANSPARENT);
    }

    private int parseMinutes(String string) {
    	String[] split = string.split(":");
    	int hour = Integer.parseInt(split[0].toLowerCase().trim());
    	int minute = 0;
    	if ( split.length > 1)
    	{
        	minute = Integer.parseInt(split[1].toLowerCase().trim());
    	}
    	int result = Math.max(0,Math.min(24 * 60,hour * 60 + minute));
		return result;
	}



	public Configuration getConfig() {
        return config;
    }

    @Deprecated
    public int getWorktimeStart() {
        return mintimeMinutes / 60;
    }

    public int getRowsPerHour() {
        return rowsPerHour;
    }

    @Deprecated
    public int getWorktimeEnd() {
        return maxtimeMinutes / 60;
    }

    public Set<Integer> getExcludeDays() {
        return excludeDays;
    }
    
    public boolean isNonFilteredEventsVisible() 
    {
        return nonFilteredEventsVisible;
    }

    public boolean isExceptionsVisible() {
        return exceptionsVisible;
    }

    public boolean isCompactColumns() {
    	return compactColumns;
    }

    public boolean isResourceColoring() {
        return colorField.equals( COLOR_RESOURCES ) || colorField.equals( COLOR_EVENTS_AND_RESOURCES);
    }

    public boolean isEventColoring() {
        return colorField.equals( COLOR_EVENTS ) || colorField.equals( COLOR_EVENTS_AND_RESOURCES);
    }


    public int getDaysInWeekview() {
		return daysInWeekview;
	}

	@Override
	public int getFirstDayOfWeek()
	{
	   return firstDayOfWeek;
	}

    @Override
    public int getFirstDayOfWeek(Date now)
    {
        if ( firstDayOfWeek == DateTools.CURRENT_WEEKDAY) {
            return DateTools.getWeekday( now );
        }
        return firstDayOfWeek;
    }


    public int getMinBlockWidth()
	{
		return minBlockWidth;
	}

	public boolean isWorktimeOvernight() {
        int worktimeS = getWorktimeStartMinutes();
        int worktimeE = getWorktimeEndMinutes();
        worktimeE = (worktimeE == 0)?24*60:worktimeE;
        boolean overnight = worktimeS >= worktimeE|| worktimeE == 24*60;
		return overnight;
	}
	
	public int getWorktimeStartMinutes() {
        return mintimeMinutes;
    }

	public int getWorktimeEndMinutes() {
        return maxtimeMinutes;
    }
	


}
