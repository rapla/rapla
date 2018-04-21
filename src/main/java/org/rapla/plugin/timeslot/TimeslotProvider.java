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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Singleton
public class TimeslotProvider {
	
	private ArrayList<Timeslot> timeslots;
    private final RaplaLocale raplaLocale;

	@Inject
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
					final Date date = format.parseTime(time);
					final DateTools.TimeWithoutTimezone timeWithoutTimezone = DateTools.toTime(date.getTime());
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

	public static ArrayList<Timeslot> getDefaultTimeslots(RaplaLocale raplaLocale) {
		ArrayList<Timeslot> timeslots = new ArrayList<>();
		final Date date = DateTools.cutDate(new Date());
		for (int i = 0; i <=23; i++ ) {
    		 int minuteOfDay = i * 60;
			 Date toFormat = new Date( date.getTime() + minuteOfDay * DateTools.MILLISECONDS_PER_MINUTE);
    		 String name =raplaLocale.formatTime( toFormat);
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
