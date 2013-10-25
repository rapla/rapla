package org.rapla.plugin.eventtimecalculator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;

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

    public String format(long duration) {
        if (duration < 0 || timeUnit == 0) {
            return "";
        }
        return MessageFormat.format(timeFormat, duration / timeUnit, duration % timeUnit);
    }
	
    public long calcDuration(long minutes) {

        if (timeTillBreak + durationOfBreak == 0)
            return minutes;

        long actualDuration = 0;


        if (minutes > (timeTillBreak + durationOfBreak)) {
            long counter = (minutes / (timeTillBreak + durationOfBreak));
            actualDuration = minutes - (counter * durationOfBreak);
        } else {
            actualDuration = minutes;
        }
        return actualDuration;
    }

    public long calcDuration(Reservation reservation) {
        Collection<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        long totalDuration = 0;
        for (Appointment app : reservation.getAppointments()) {
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

    public long calcDuration(AppointmentBlock block) {
        long duration = DateTools.countMinutes(block.getStart(), block.getEnd());
        long totalDuration = calcDuration(duration);
        return totalDuration;
    }



}
