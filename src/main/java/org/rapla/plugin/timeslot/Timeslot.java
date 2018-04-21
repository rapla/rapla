package org.rapla.plugin.timeslot;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;

import java.util.Date;

public class Timeslot implements Comparable<Timeslot>
{
	String name;
	int minuteOfDay;
	public Timeslot(String name, int minuteOfDay)
	{
		this.name = name;
		this.minuteOfDay = minuteOfDay;
	}
	public String getName() {
		return name;
	}
	public int getMinuteOfDay() {
		return minuteOfDay;
	}
	
	public int compareTo(Timeslot other) {
		if ( minuteOfDay == other.minuteOfDay)
		{
			return name.compareTo( other.name);
		}
		return minuteOfDay < other.minuteOfDay ? -1 : 1;
	}
	
	public String toString()
	{
		int hour = minuteOfDay/60;
		int minute = minuteOfDay % 60;
		final long l = DateTools.toTime(hour, minute, 0);
		final String time = SerializableDateTimeFormat.INSTANCE.formatTime(new Date(l));
		return name + " " + time;
	}

}