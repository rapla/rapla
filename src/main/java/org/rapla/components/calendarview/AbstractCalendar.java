package org.rapla.components.calendarview;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.DateWithoutTimezone;
import org.rapla.framework.RaplaLocale;

public abstract class  AbstractCalendar {
	private int daysInView = 7;
	private int firstWeekday = 2;//Calendar.inject().getFirstDayOfWeek();
    /** shared calendar instance. Only used for temporary stored values. */
	protected Collection<Integer> excludeDays = Collections.emptySet();

    private Date startDate;
    private Date endDate;
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
    	if ( !excludeDays.contains(new Integer( weekday )) ) {
    		return false;
        }
    	boolean empty = isEmpty(column);
		return empty;
	}
	
	abstract protected boolean isEmpty(int column);

	public void setLocale(RaplaLocale raplaLocale) {
        this.raplaLocale = raplaLocale;
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
    
    public RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }
	 
}
