package org.rapla.components.util;


import java.io.Serializable;
import java.time.LocalDateTime;
public final class TimeInterval implements Serializable
{
	private static final long serialVersionUID = -8387919392038291664L;
	LocalDateTime start;
	LocalDateTime end;

	TimeInterval()
	{
		this( null, null);
	}
	public TimeInterval(LocalDateTime start, LocalDateTime end) {
		this.start = start;
		this.end = end;
	}

	/** {@code LocalDateTime} factory method. UTC. */
	public static TimeInterval of(LocalDateTime start, LocalDateTime end) {
		LocalDateTime s = start == null ? null : start;
		LocalDateTime e = end == null ? null : end;
		return new TimeInterval(s, e);
	}

	public LocalDateTime getStart() {
		return start;
	}
	public void setStart(LocalDateTime start) {
		this.start = start;
	}
	public LocalDateTime getEnd() {
		return end;
	}
	public void setEnd(LocalDateTime end) {
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
		LocalDateTime start2 = other.getStart();
		LocalDateTime end2 = other.getEnd();
		
		if ( start == null  )
		{
			if (start != start2)
			{
				return false;
			}
		}
		else
		{
			if (!start.equals(start2))
			{
				return false;
			}
		}
		
		if ( end == null  )
		{
            return end == end2;
		}
		else
		{
            return end.equals(end2);
		}
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
		LocalDateTime start2 = other.getStart();
		LocalDateTime end2 = other.getEnd();
		
		if ( start != null)
		{
			if ( end2 != null)
			{
				if ( !start.isBefore(end2))
				{
					return false;
				}
			}
		}
		if  ( end != null)
		{
			if ( start2 != null)
			{
                return start2.isBefore(end);
			}
		}
		return true;
	}

	public TimeInterval union(TimeInterval interval) {
		LocalDateTime start = getStart();
		LocalDateTime end = getEnd();
		if ( interval == null )
		{
			interval = new TimeInterval(start, end);
		}
		if  ( start == null || (interval.getStart() != null && interval.getStart().isAfter( start)))
		{
			interval = new TimeInterval( start, interval.getEnd());
		}
		
		if  ( end == null || ( interval.getEnd() != null && end.isAfter( interval.getEnd()))) 
		{
			interval = new TimeInterval( interval.getStart(), end);
		}
		return interval;
	}
}
