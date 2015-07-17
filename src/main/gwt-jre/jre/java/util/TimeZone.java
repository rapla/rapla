package java.util;

public class TimeZone {
	static TimeZone GMT = new TimeZone();
	public static TimeZone getTimeZone(String id)
	{
		return GMT;
	} 
	
	public int getOffset( long date)
	{
		return 0;
	}
}
