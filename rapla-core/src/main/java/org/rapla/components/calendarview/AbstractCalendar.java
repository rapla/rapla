package org.rapla.components.calendarview;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.DateWithoutTimezone;
import org.rapla.framework.RaplaLocale;

import java.util.Collection;
import java.util.Collections;
import java.time.LocalDateTime;
public abstract class  AbstractCalendar {
	protected int offsetMinutes = 0;

	private int daysInView = 7;
	private int firstWeekday = 2;//Calendar.inject().getFirstDayOfWeek();
    /** shared calendar instance. Only used for temporary stored values. */
	protected Collection<Integer> excludeDays = Collections.emptySet();

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    protected int daysInMonth;
    
    protected RaplaLocale raplaLocale;
    
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
    	if ( !excludeDays.contains(Integer.valueOf(weekday)) ) {
    		return false;
        }
    	boolean empty = isEmpty(column);
		return empty;
	}

    public int getOffsetMinutes()
    {
        return offsetMinutes;
    }

    abstract protected boolean isEmpty(int column);

	public void setLocale(RaplaLocale raplaLocale) {
        this.raplaLocale = raplaLocale;
    }

    public void setToDate(LocalDateTime date) 
    {
        calcMinMaxDates( date );
    }

    public LocalDateTime getStartDate()
    {
        return startDate;
    }

    public LocalDateTime getEndDate()
    {
        return endDate;
    }
    
    protected void setStartDate(LocalDateTime date)
    {
    	this.startDate = date;
    }
    
    protected void setEndDate(LocalDateTime date)
    {
    	this.endDate = date;
    }

    public void setExcludeDays(Collection<Integer> excludeDays) {
        this.excludeDays = excludeDays;
        if (getStartDate() != null)
            calcMinMaxDates( getStartDate() );
    }
    
    public void calcMinMaxDates(LocalDateTime date)
    {
    	date = DateTools.cutDate(date);
        this.daysInMonth = DateTools.getDaysInMonth(date) ;
        if ( daysInView > 14)
    	{
    	     this.startDate = LocalDateTime.of(date.getYear(), date.getMonthValue(), 1, 0, 0);
    	     this.endDate = LocalDateTime.of(date.getYear(), date.getMonthValue(), daysInMonth, 0, 0).plusDays(1);
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
    	startDate = startDate.plusMinutes(offsetMinutes);
		endDate = endDate.plusMinutes(offsetMinutes);
	}
    
    public RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }
	 
}
