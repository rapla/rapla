package org.rapla.components.util;

import jsinterop.annotations.JsType;

import java.io.Serializable;
import java.util.Date;

@JsType(namespace = "rapla")
public final class TimeInterval implements Serializable
{
	private static final long serialVersionUID = -8387919392038291664L;
	Date start;
	Date end;

	TimeInterval()
	{
		this( null, null);
	}
	public TimeInterval(Date start, Date end) {
		this.start = start;
		this.end = end;
	}
	
	public Date getStart() {
		return start;
	}
	public void setStart(Date start) {
		this.start = start;
	}
	public Date getEnd() {
		return end;
	}
	public void setEnd(Date end) {
		this.end = end;
	}
	
	public String toString()
	{
		return start + " - " + end;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof TimeInterval) )
		{
			return false;
		}
		TimeInterval other = (TimeInterval) obj;
		Date start2 = other.getStart();
		Date end2 = other.getEnd();
		
		if ( start == null  )
		{
			if (start != start2)
			{
				return false;
			}
		}
		else
		{
			if ( start2 == null || !start.equals(start2))
			{
				return false;
			}
		}
		
		if ( end == null  )
		{
			if (end != end2)
			{
				return false;
			}
		}
		else
		{
			if ( end2 == null || !end.equals(end2))
			{
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public int hashCode() 
	{
		int hashCode;
		if ( start!=null )
		{
			hashCode = start.hashCode(); 
			if ( end!=null )
			{
				hashCode *= end.hashCode();
			}
		}
		else if ( end!=null )
		{
			hashCode = end.hashCode(); 
		}
		else
		{
			hashCode =super.hashCode();
		}
		return hashCode;
	}

	public boolean overlaps(TimeInterval other) {
		Date start2 = other.getStart();
		Date end2 = other.getEnd();
		
		if ( start != null)
		{
			if ( end2 != null)
			{
				if ( !start.before(end2))
				{
					return false;
				}
			}
		}
		if  ( end != null)
		{
			if ( start2 != null)
			{
				if ( !start2.before(end))
				{
					return false;
				}
			}
		}
		return true;
	}

	public TimeInterval union(TimeInterval interval) {
		Date start = getStart();
		Date end = getEnd();
		if ( interval == null )
		{
			interval = new TimeInterval(start, end);
		}
		if  ( start == null || (interval.getStart() != null && interval.getStart().after( start)))
		{
			interval = new TimeInterval( start, interval.getEnd());
		}
		
		if  ( end == null || ( interval.getEnd() != null && end.after( interval.getEnd()))) 
		{
			interval = new TimeInterval( interval.getStart(), end);
		}
		return interval;
	}
}
