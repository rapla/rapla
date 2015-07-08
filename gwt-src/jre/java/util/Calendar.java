package java.util;

public class Calendar {
    private final static Calendar defaultCal = new Calendar();

    public final static int SUNDAY = 1;
    public final static int MONDAY = 2;
    public final static int TUESDAY = 3;
    public final static int WEDNESDAY = 4;
    public final static int THURSDAY = 5;
    public final static int FRIDAY = 6;
    public final static int SATURDAY = 7;

    public final static int WEEK_OF_YEAR = 3;
    public final static int HOUR_OF_DAY = 11;
    public final static int MINUTE = 12;
    public final static int DAY_OF_WEEK = 7;
    
    public final static int JANUARY = 0;
    public final static int FEBRUARY = 1;
    public final static int MARCH = 2;
    public final static int APRIL = 3;
    public final static int MAY = 4;
    public final static int JUNE = 5;
    public final static int JULY = 6;
    public final static int AUGUST = 7;
    public final static int SEPTEMBER = 8;
    public final static int OCTOBER = 9;
    public final static int NOVEMBER = 10;
    public final static int DECEMBER = 11;

    
    static public Calendar getInstance()
    {
        return defaultCal;
    }
    
    public int getFirstDayOfWeek()
    {
        return MONDAY;
    }
}
