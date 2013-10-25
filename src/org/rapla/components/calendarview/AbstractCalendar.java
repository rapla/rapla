package org.rapla.components.calendarview;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import org.rapla.components.util.DateTools;

public abstract class  AbstractCalendar {
	private int daysInView = 7;
	private int firstWeekday = Calendar.getInstance().getFirstDayOfWeek();
    /** shared calendar instance. Only used for temporary stored values. */
	protected Calendar blockCalendar;
    protected Collection<Integer> excludeDays = Collections.emptySet();

    private Date startDate;
    private Date endDate;
    protected Locale locale;
    protected TimeZone timeZone;
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
	
	public String getWeekdayName(int weekday)
	{
		 SimpleDateFormat format = new SimpleDateFormat("EEEEEE",locale);
		 Calendar calendar = createCalendar();
		 calendar.set(Calendar.DAY_OF_WEEK, weekday);
		 String weekdayName = format.format(calendar.getTime());
		 return weekdayName;
	}

	protected boolean isExcluded(int column)
	{
    	if ( daysInView == 1)
    	{
    		return false;
    	}
		blockCalendar.set(Calendar.DAY_OF_WEEK, getFirstWeekday());
    	blockCalendar.add(Calendar.DATE, column);
    	int weekday = blockCalendar.get(Calendar.DAY_OF_WEEK);
    	if ( !excludeDays.contains(new Integer( weekday )) ) {
    		return false;
        }
    	boolean empty = isEmpty(column);
		return empty;
	}
	
	abstract protected boolean isEmpty(int column);

	public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
        blockCalendar = createCalendar();
    }

    protected Calendar createCalendar() {
        return Calendar.getInstance(getTimeZone(),locale);
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
        // Constructor called?
        if (timeZone != null) {
            setTimeZone( timeZone );
            blockCalendar = createCalendar();
        }
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
    	Calendar blockCalendar = createCalendar();
    	blockCalendar.setTime( date );
        blockCalendar.set(Calendar.HOUR_OF_DAY,0);
        blockCalendar.set(Calendar.MINUTE,0);
        blockCalendar.set(Calendar.SECOND,0);
        blockCalendar.set(Calendar.MILLISECOND,0);
        this.daysInMonth = blockCalendar.getActualMaximum( Calendar.DAY_OF_MONTH ) ; 
    	if ( daysInView > 14)
    	{
    		 blockCalendar.set(Calendar.DAY_OF_MONTH, 1);
    		 this.startDate = blockCalendar.getTime();
    		 blockCalendar.set(Calendar.MILLISECOND,1);
    		
    		 blockCalendar.set(Calendar.MILLISECOND,0);
    		 blockCalendar.add(Calendar.DATE, this.daysInMonth);
    		 this.endDate = blockCalendar.getTime();
    		 firstWeekday = blockCalendar.getFirstDayOfWeek();
    	}
    	else
    	{
    		if ( daysInView >= 3)
        	{
    			int firstWeekday2 = getFirstWeekday();
				blockCalendar.set(Calendar.DAY_OF_WEEK, firstWeekday2);
    	    	if ( blockCalendar.getTime().after( date))
    	    	{
    	    		blockCalendar.add(Calendar.DATE, -7);
    		    }
        	}
        	else
        	{
        		firstWeekday = blockCalendar.get( Calendar.DAY_OF_WEEK);
        	}
        	startDate = blockCalendar.getTime();
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
	public static String formatDateMonth(Date date, Locale locale, TimeZone timeZone) {
	    FieldPosition fieldPosition = new FieldPosition( DateFormat.YEAR_FIELD );
	    StringBuffer buf = new StringBuffer();
	    DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale);
	    format.setTimeZone( timeZone );
	    buf = format.format(date,
	                       buf,
	                       fieldPosition
	                       );
	    if ( fieldPosition.getEndIndex()<buf.length() ) {
	        buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex()+1 );
	    } else if ( (fieldPosition.getBeginIndex()>=0) ) {
	        buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex() );
	    }
	    char lastChar = buf.charAt(buf.length()-1);
		if (lastChar == '/' || lastChar == '-' ) {
			String result = buf.substring(0,buf.length()-1);
			return result;
		} else {
			String result = buf.toString();
			return result;
		}
	}

	/** formats the day of week, date and month in the selected locale and timeZone*/
	public static String formatDayOfWeekDateMonth(Date date, Locale locale, TimeZone timeZone) {
	    SimpleDateFormat format =  new SimpleDateFormat("EEE", locale);
	    format.setTimeZone(timeZone);
	    String datePart = format.format(date);
		String dateOfMonthPart = AbstractCalendar.formatDateMonth( date,locale,timeZone );
		return datePart + " " + dateOfMonthPart ;
	}

	public static boolean isAmPmFormat(Locale locale) {
	    // Determines if am-pm-format should be used.
	    DateFormat format= DateFormat.getTimeInstance(DateFormat.SHORT, locale);
	    FieldPosition amPmPos = new FieldPosition(DateFormat.AM_PM_FIELD);
	    format.format(new Date(), new StringBuffer(),amPmPos);
	    return (amPmPos.getEndIndex()>0);
	}
    

	 

}
