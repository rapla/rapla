package org.rapla.entities.domain;

import org.rapla.facade.PeriodModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import java.time.LocalDateTime;
import org.rapla.components.util.DateTools;
public class ReservationHelper
{

    static public void makeRepeatingForPeriod(PeriodModel model, Appointment appointment, RepeatingType repeatingType, int repeatings) {
    	appointment.setRepeatingEnabled(true);
        Repeating repeating = appointment.getRepeating();
        repeating.setType( repeatingType );
        Period period = model.getNearestPeriodForStartDate( appointment.getStart());
        if ( period != null && repeatings <=1) {
    		repeating.setEnd(period.getEnd());
    	} else {
            repeating.setNumber( repeatings );
    	}
    }

    /** find the first visible reservation*/
    static public LocalDateTime findFirst( List<Reservation> reservationList) {
        LocalDateTime firstStart = null;
        Iterator<Reservation> it = reservationList.iterator();
        while (it.hasNext()) {
            Appointment[] appointments = ( it.next()).getAppointments();
            for (int i=0;i<appointments.length;i++) {
                LocalDateTime start = appointments[i].getStart();
                Repeating r = appointments[i].getRepeating();
                if (firstStart == null) {
                    firstStart = start;
                    continue;
                }
                if (!start.isBefore(firstStart))
                    continue;
                
                if ( r== null || !r.isException(DateTools.toMilli(start)))
                {
                    firstStart = start;
                } 
                else 
                {
                    Collection<AppointmentBlock> blocks = new ArrayList<>();
                    appointments[i].createBlocks( start, firstStart, blocks );
                    for (AppointmentBlock block: blocks) {
                        LocalDateTime blockStart = block.getStartDateTime();
                        if (blockStart.isBefore(firstStart)) {
                            firstStart = blockStart;
                            continue;
                        }
                    }
                }
            }
        }
        return firstStart;
    }

}
