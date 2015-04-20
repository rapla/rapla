package org.rapla.components.calendarview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.DateWithoutTimezone;

public abstract class  AbstractCalendar {
	private int daysInView = 7;
	private int firstWeekday = 2;//Calendar.getInstance().getFirstDayOfWeek();
    /** shared calendar instance. Only used for temporary stored values. */
	protected Collection<Integer> excludeDays = Collections.emptySet();

    private Date startDate;
    private Date endDate;
    protected Locale locale;
    protected int daysInMonth;
    
    protected Collection<Builder> builders = new ArrayList<Builder>();
   
	public int getDaysInView() {
		return daysInView;
	}

	public void setDaysInView(int daysInView) 
	{
		this.daysInView = daysInView;
	}

	public int getFirstWeekday()
	{
		return firstWeekday;
	}

	public void setFirstWeekday(int firstDayOfWeek) {
		this.firstWeekday = firstDayOfWeek;
	}
	
	protected boolean isExcluded(int column)
	{
    	if ( daysInView == 1)
    	{
    		return false;
    	}
    	int weekday = ((getFirstWeekday()-1+column)%7) + 1;
//    	DateTools.getWeekday(date)
//		blockCalendar.set(Calendar.DAY_OF_WEEK, );
//    	blockCalendar.add(Calendar.DATE, column);
//    	int weekday = blockCalendar.get(Calendar.DAY_OF_WEEK);
    	if ( !excludeDays.contains(new Integer( weekday )) ) {
    		return false;
        }
    	boolean empty = isEmpty(column);
		return empty;
	}
	
	abstract protected boolean isEmpty(int column);

	
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public void setToDate(Date date) 
    {
        calcMinMaxDates( date );
    }

    public Date getStartDate()
    {
        return startDate;
    }

    public Date getEndDate()
    {
        return endDate;
    }
    
    protected void setStartDate(Date date)
    {
    	this.startDate = date;
    }
    
    protected void setEndDate(Date date)
    {
    	this.endDate = date;
    }

    public void setExcludeDays(Collection<Integer> excludeDays) {
        this.excludeDays = excludeDays;
        if (getStartDate() != null)
            calcMinMaxDates( getStartDate() );
    }
    
    public void rebuild(Builder builder) {
        try {
            addBuilder( builder);
            rebuild();
        } finally {
            removeBuilder( builder );
        }
    }
    
    public void calcMinMaxDates(Date date)
    {
    	date = DateTools.cutDate(date);
        this.daysInMonth = DateTools.getDaysInMonth(date) ;
        if ( daysInView > 14)
    	{
    	     DateWithoutTimezone date2 = DateTools.toDate( date.getTime());
    	     this.startDate = new Date(DateTools.toDate( date2.year, date2.month, 1));
    	     this.endDate = new Date(DateTools.toDate( date2.year, date2.month, daysInMonth));
    		 firstWeekday = getFirstWeekday();
    	}
    	else
    	{
    		if ( daysInView >= 3)
        	{
    			startDate = DateTools.getFirstWeekday( date, getFirstWeekday());
        	}
        	else
        	{
        	    startDate = DateTools.cutDate(date);
        		firstWeekday = DateTools.getWeekday(date);
        	}
        	endDate =DateTools.addDays(startDate, daysInView);
    	}
	}
    
    public abstract void rebuild();
    
    public Iterator<Builder> getBuilders() {
        return builders.iterator();
    }

    public void addBuilder(Builder b) {
        builders.add(b);
    }

    public void removeBuilder(Builder b) {
        builders.remove(b);
    }

	/** formats the date and month in the selected locale and timeZone*/
	public static String formatDateMonth(Date date, Locale locale) {
	    DateWithoutTimezone date2 = DateTools.toDate( date.getTime());
	    return date2.month + "/" + date2.day;
//	    FieldPosition fieldPosition = new FieldPosition( DateFormat.YEAR_FIELD );
//	    StringBuffer buf = new StringBuffer();
//	    DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale);
//	    buf = format.format(date,
//	                       buf,
//	                       fieldPosition
//	                       );
//	    if ( fieldPosition.getEndIndex()<buf.length() ) {
//	        buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex()+1 );
//	    } else if ( (fieldPosition.getBeginIndex()>=0) ) {
//	        buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex() );
//	    }
//	    char lastChar = buf.charAt(buf.length()-1);
//		if (lastChar == '/' || lastChar == '-' ) {
//			String result = buf.substring(0,buf.length()-1);
//			return result;
//		} else {
//			String result = buf.toString();
//			return result;
//		}
	}

	/** formats the day of week, date and month in the selected locale and timeZone*/
    public static String formatDayOfWeekDateMonth(Date date, Locale locale) {
        //SimpleDateFormat format =  new SimpleDateFormat("EEE", locale);
        int weekday = DateTools.getWeekday( date);
        String datePart = getWeekdayName(weekday, locale).substring(0,3);
        String dateOfMonthPart = AbstractCalendar.formatDateMonth( date,locale );
        return datePart + " " + dateOfMonthPart ;
    }

	public static boolean isAmPmFormat(Locale locale) {
	    // Determines if am-pm-format should be used.
	    return false;
//	    DateFormat format= DateFormat.getTimeInstance(DateFormat.SHORT, locale);
//	    FieldPosition amPmPos = new FieldPosition(DateFormat.AM_PM_FIELD);
//	    format.format(new Date(), new StringBuffer(),amPmPos);
//	    return (amPmPos.getEndIndex()>0);
	}

    public static String formatMonthYear(Date date, Locale locale)
    {
        int year = DateTools.toDate( date.getTime()).year;
        String result = formatMonth( date, locale) + " " + year;
        return result;

    }
    
    static public String getWeekdayName(int weekday, Locale locale)
    {
        String result;
        switch (weekday)
        {
            case 1: result= "sunday";break;
            case 2: result= "monday";break;
            case 3: result= "tuesday";break;
            case 4: result= "wednesday";break;
            case 5: result= "thursday";break;
            case 6: result= "friday";break;
            case 7: result= "saturday";break;
            default: throw new IllegalArgumentException("Weekday " + weekday + " not supported.");
        }
        return result;
    }

    public static String formatMonth(Date date, Locale locale)
    {    
        int month = DateTools.toDate( date.getTime()).month;
        String result;
        switch (month)
        {
            case 0: result= "january";break;
            case 1: result= "february";break;
            case 2: result= "march";break;
            case 3: result= "april";break;
            case 4: result= "may";break;
            case 5: result= "june";break;
            case 6: result= "july";break;
            case 7: result= "august";break;
            case 8: result= "september";break;
            case 9: result= "october";break;
            case 10: result= "november";break;
            case 11: result= "december";break;
            default: throw new IllegalArgumentException("Month " + month + " not supported.");
        }
        return result;
    }

    
    public static String formatTime(int minuteOfDay,boolean useAM_PM, Locale locale) {
        int minute = minuteOfDay%60;
        int hour = minuteOfDay/60;
        String displayedHour = "" + (useAM_PM ? hour %12 : hour);
        String displayedMinute = minute > 9 ? ""+ minute : "0"+minute ;
        String string = displayedHour + ":" + displayedMinute;
        if (useAM_PM ) {
            if ( hour >= 12)
            {
                string += " PM";
            }
            else
            {
                string += " AM";
            }
        }
        return string;
    }

	 

}
