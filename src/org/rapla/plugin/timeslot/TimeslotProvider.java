package org.rapla.plugin.timeslot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaLocale;

public class TimeslotProvider extends RaplaComponent {
	
	private ArrayList<Timeslot> timeslots;
	
	public TimeslotProvider(RaplaContext context, Configuration config) throws  ParseDateException
	{
		super(context);
		update(config);
//		timeslots.clear();
//		timeslots.add(new Timeslot("1. Stunde", 7*60 + 45));
//		timeslots.add(new Timeslot("- Pause (5m)", 8*60 + 30));
//		timeslots.add(new Timeslot("2. Stunde", 8 * 60 + 35 ));
//		timeslots.add(new Timeslot("- Pause (15m)", 9 * 60 + 20 ));
//		timeslots.add(new Timeslot("3. Stunde", 9 * 60 + 35 ));
//		timeslots.add(new Timeslot("- Pause (5m)", 10*60 + 20));
//		timeslots.add(new Timeslot("4. Stunde", 10 * 60 + 25 ));
//		timeslots.add(new Timeslot("- Pause (15m)", 11 * 60 + 10 ));
//		timeslots.add(new Timeslot("5. Stunde", 11 * 60 + 25 ));
//		timeslots.add(new Timeslot("- Pause (5m)", 12*60 + 10));
//		timeslots.add(new Timeslot("6. Stunde", 12 * 60 + 15 ));
//		timeslots.add(new Timeslot("Nachmittag", 13 * 60 + 0 ));
	}

	public void update(Configuration config) throws ParseDateException {
		ArrayList<Timeslot> timeslots = parseConfig(config, getRaplaLocale());

		if ( timeslots == null)
		{
			timeslots = getDefaultTimeslots(getRaplaLocale());
		}
		this.timeslots = timeslots;
	}

	public static ArrayList<Timeslot> parseConfig(Configuration config,RaplaLocale locale) throws ParseDateException {
		ArrayList<Timeslot> timeslots = null;
		if ( config != null)
		{
			Calendar cal = locale.createCalendar();
			SerializableDateTimeFormat format = locale.getSerializableFormat();
			Configuration[] children = config.getChildren("timeslot");
			if ( children.length  > 0)
			{
				timeslots = new ArrayList<Timeslot>();
				int i=0;
				for (Configuration conf:children)
				{
					String name = conf.getAttribute("name","");
					String time = conf.getAttribute("time",null);
					int minuteOfDay;
					if ( time == null)
					{
						time =  i + ":00:00";
					}
					cal.setTime(format.parseTime( time));
					int hour = cal.get(Calendar.HOUR_OF_DAY);
					if ( i != 0)
					{
						minuteOfDay= hour * 60 + cal.get(Calendar.MINUTE);
					}
					else
					{
						minuteOfDay = 0;
					}
					if ( name == null)
					{
						name = format.formatTime( cal.getTime());
					}
					Timeslot slot = new Timeslot( name, minuteOfDay);
					timeslots.add( slot);
					i=hour+1;
				}
			}
		}
		return timeslots;
	}

	public static ArrayList<Timeslot> getDefaultTimeslots(RaplaLocale raplaLocale) {
		ArrayList<Timeslot> timeslots = new ArrayList<Timeslot>();
		Calendar cal = raplaLocale.createCalendar();
		cal.setTime(DateTools.cutDate( new Date()));
		for (int i = 0; i <=23; i++ ) {
    		 int minuteOfDay = i * 60;
    		 cal.set(Calendar.HOUR_OF_DAY, i);
    		 String name =raplaLocale.formatTime( cal.getTime());
    		 //String name = minuteOfDay / 60 + ":" + minuteOfDay%60;
    		 Timeslot slot = new Timeslot(name, minuteOfDay);
    		 timeslots.add(slot);
    	}
		return timeslots;
	}
	
	public List<Timeslot> getTimeslots()
	{
		return timeslots;
	}
}
