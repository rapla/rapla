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
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

/** <strong>WARNING!!</strong> This class should not be public to the outside. Please use the interface */
public class CalendarOptionsImpl implements CalendarOptions {
    public final static TypedComponentRole<RaplaConfiguration> CALENDAR_OPTIONS= new TypedComponentRole<RaplaConfiguration>("org.rapla.calendarview");
    public final static TypedComponentRole<Boolean> SHOW_CONFLICT_WARNING = new TypedComponentRole<Boolean>("org.rapla.conflict.showWarning");
    
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
  
    Set<Integer> excludeDays = new LinkedHashSet<Integer>();

    int maxtimeMinutes = -1;
    int mintimeMinutes = -1;
    int rowsPerHour = 4;
    boolean exceptionsVisible;
    boolean compactColumns = false;  // use for strategy.setFixedSlotsEnabled
    Configuration config;
    String colorField;
    boolean nonFilteredEventsVisible = false;
   
    int daysInWeekview;
	int firstDayOfWeek;
	private int minBlockWidth;

    public CalendarOptionsImpl(Configuration config ) throws RaplaException {
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
    		}
        } catch ( NumberFormatException e ) {
            throw new RaplaException( "Invalid time in " + worktime  + ". use the following format : 8-18 or 8:30-18:00!");
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
		            excludeDays.add( new Integer(normalizedToken) );
		        } catch ( NumberFormatException e ) {
		            throw new RaplaException("Invalid day in " + excludeDays  + ". only numbers are allowed!");
		        }
		    } // end of while ()
        }
        int firstDayOfWeekDefault = Calendar.getInstance().getFirstDayOfWeek();
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

    public int getWorktimeStart() {
        return mintimeMinutes / 60;
    }

    public int getRowsPerHour() {
        return rowsPerHour;
    }

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

    public void setNonFilteredEventsVisible(boolean nonFilteredEventsVisible)
    {
        this.nonFilteredEventsVisible = nonFilteredEventsVisible;
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

	public int getFirstDayOfWeek()
	{
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
