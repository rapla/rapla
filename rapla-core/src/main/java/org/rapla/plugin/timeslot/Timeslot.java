package org.rapla.plugin.timeslot;

import org.rapla.components.util.SerializableDateTimeFormat;


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
		final String time = SerializableDateTimeFormat.INSTANCE.formatTime(java.time.LocalTime.of(hour, minute, 0));
		return name + " " + time;
	}

}