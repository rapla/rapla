package org.rapla.plugin.timeslot;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;

import java.time.LocalDateTime;
public class TimeslotProvider {
	
	private ArrayList<Timeslot> timeslots;
    private final RaplaLocale raplaLocale;

	@Autowired
	public TimeslotProvider(RaplaLocale raplaLocale, RaplaFacade facade) throws RaplaInitializationException // ParseDateException
	{
		this.raplaLocale = raplaLocale;
        try
		{
		    final RaplaConfiguration config = facade.getSystemPreferences().getEntry(TimeslotPlugin.CONFIG, null);
		    update(config);
		}
		catch(ParseDateException|RaplaException e)
		{
		    throw new RaplaInitializationException(e.getMessage(), e);
		}
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
	
	private RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
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
			SerializableDateTimeFormat format = locale.getSerializableFormat();
			Configuration[] children = config.getChildren("timeslot");
			if ( children.length  > 0)
			{
				timeslots = new ArrayList<>();
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
					final LocalDateTime date = format.parseTime(time);
					final DateTools.TimeWithoutTimezone timeWithoutTimezone = DateTools.toTime(date);
					int hour = timeWithoutTimezone.hour;
					if ( i != 0)
					{
						minuteOfDay= hour * 60 + timeWithoutTimezone.minute;
					}
					else
					{
						minuteOfDay = 0;
					}
					if ( name == null)
					{
						name = format.formatTime( date);
					}
					Timeslot slot = new Timeslot( name, minuteOfDay);
					timeslots.add( slot);
					i=hour+1;
				}
			}
		}
		return timeslots;
	}

	/** 7-slot default at 2-hour intervals from 06:00 to 18:00 — covers a typical
	 *  working day with 5 daytime blocks plus an early and a late slot. Matches
	 *  what {@code TimeslotPreferencesPanel} shows as the JSON_EDITOR default,
	 *  so calendars and panel stay in sync before any explicit config is saved.
	 *  (Previous default was 24 hourly slots — too verbose for most schedules.) */
	public static ArrayList<Timeslot> getDefaultTimeslots(RaplaLocale raplaLocale) {
		ArrayList<Timeslot> timeslots = new ArrayList<>();
		final LocalDateTime date = DateTools.cutDate(LocalDateTime.now());
		int[] starts = {6 * 60, 8 * 60, 10 * 60, 12 * 60, 14 * 60, 16 * 60, 18 * 60};
		for (int minuteOfDay : starts) {
			LocalDateTime toFormat = date.plusMinutes(minuteOfDay);
			String name = raplaLocale.formatTime(toFormat);
			timeslots.add(new Timeslot(name, minuteOfDay));
		}
		return timeslots;
	}
	
	public List<Timeslot> getTimeslots()
	{
		return timeslots;
	}
}
