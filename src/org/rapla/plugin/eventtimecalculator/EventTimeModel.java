package org.rapla.plugin.eventtimecalculator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.Configuration;

/**
 * Created with IntelliJ IDEA.
 * User: kuestermann
 * Date: 10.06.13
 * Time: 17:52
 * To change this template use File | Settings | File Templates.
 */
public class EventTimeModel {
    protected int timeTillBreak;
    protected int durationOfBreak;
    protected int timeUnit;
    protected String timeFormat;

    public EventTimeModel()
    {
    	timeUnit = EventTimeCalculatorPlugin.DEFAULT_timeUnit;
    	timeFormat = EventTimeCalculatorPlugin.DEFAULT_timeFormat;
    	timeTillBreak = EventTimeCalculatorPlugin.DEFAULT_intervalNumber;
    	durationOfBreak = EventTimeCalculatorPlugin.DEFAULT_breakNumber;
    }
    public int getTimeTillBreak() {
		return timeTillBreak;
	}
	public void setTimeTillBreak(int timeTillBreak) {
		this.timeTillBreak = timeTillBreak;
	}
	public int getDurationOfBreak() {
		return durationOfBreak;
	}
	public void setDurationOfBreak(int durationOfBreak) {
		this.durationOfBreak = durationOfBreak;
	}
	public int getTimeUnit() {
		return timeUnit;
	}
	public void setTimeUnit(int timeUnit) {
		this.timeUnit = timeUnit;
	}
	public String getTimeFormat() {
		return timeFormat;
	}
	public void setTimeFormat(String timeFormat) {
		this.timeFormat = timeFormat;
	}
	public EventTimeModel(Configuration configuration) {
    	timeTillBreak = configuration.getChild(EventTimeCalculatorPlugin.INTERVAL_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_intervalNumber);
        durationOfBreak= configuration.getChild(EventTimeCalculatorPlugin.BREAK_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_breakNumber);
        timeUnit= configuration.getChild(EventTimeCalculatorPlugin.TIME_UNIT).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_timeUnit);
        timeFormat= configuration.getChild(EventTimeCalculatorPlugin.TIME_FORMAT).getValue(EventTimeCalculatorPlugin.DEFAULT_timeFormat);
	}
    
	public String format(long duration) {
        if (duration < 0 || timeUnit == 0) {
            return "";
        }
        return MessageFormat.format(timeFormat, duration / timeUnit, duration % timeUnit);
    }
	
    public long calcDuration(long minutes) 
    {
    	int blockTimeIncludingBreak = timeTillBreak + durationOfBreak;
		if (timeTillBreak  <= 0 || durationOfBreak <= 0 || minutes<=timeTillBreak)
            return minutes;

		long breaks = (minutes + durationOfBreak -1 ) / blockTimeIncludingBreak;
		long fullBreaks = minutes   / blockTimeIncludingBreak;
		long partBreak;
		if ( breaks > fullBreaks)
		{
			long timeInclFullBreak =  timeTillBreak * (fullBreaks + 1) + fullBreaks * durationOfBreak ;
			partBreak = minutes - timeInclFullBreak;
		}
		else
		{
			partBreak = 0;
		}
		long actualDuration = minutes - (fullBreaks * durationOfBreak) - partBreak;
        return actualDuration;
    }

    public long calcDuration(Reservation reservation) {
        return calcDuration(reservation.getAppointments());
    }

    public long calcDuration(AppointmentBlock block) {
        long duration = DateTools.countMinutes(block.getStart(), block.getEnd());
        return calcDuration(duration);
    }

    public long calcDuration(Appointment[] appointments){
        final Collection<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        long totalDuration = 0;
        for (Appointment app : appointments) {
            Date start = app.getStart();
            Date end = app.getMaxEnd();
            if (end == null) {
                totalDuration = -1;
                break;
            } else {
                app.createBlocks(start, end, blocks);
            }
        }
        for (AppointmentBlock block : blocks) {
            long duration = calcDuration(block);
            if (totalDuration >= 0) {
                totalDuration += duration;
            }
        }
        return totalDuration;
    }

    public boolean hasEnd(Appointment[] appointments) {
        boolean hasEnd = true;
        for (Appointment appointment : appointments) { // goes through all appointments of the reservation
            if (hasEnd(appointment)) { // appoinment repeats forever?
                hasEnd = false;
                break;
            }
        }
        return hasEnd;
    }

    public boolean hasEnd(Appointment appointment) {
        return appointment != null && appointment.getRepeating() != null && appointment.getRepeating().getEnd() == null;
    }

    public boolean hasEnd(Reservation reservation) {
        return hasEnd(reservation.getAppointments());
    }
}
