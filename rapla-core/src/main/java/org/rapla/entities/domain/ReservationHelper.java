package org.rapla.entities.domain;

import org.rapla.facade.PeriodModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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
    static public Date findFirst( List<Reservation> reservationList) {
        Date firstStart = null;
        Iterator<Reservation> it = reservationList.iterator();
        while (it.hasNext()) {
            Appointment[] appointments = ( it.next()).getAppointments();
            for (int i=0;i<appointments.length;i++) {
                Date start = appointments[i].getStart();
                Repeating r = appointments[i].getRepeating();
                if (firstStart == null) {
                    firstStart = start;
                    continue;
                }
                if (!start.before(firstStart))
                    continue;
                
                if ( r== null || !r.isException(start.getTime()))
                {
                    firstStart = start;
                } 
                else 
                {
                    Collection<AppointmentBlock> blocks = new ArrayList<>();
                    appointments[i].createBlocks( start, firstStart, blocks );
                    for (AppointmentBlock block: blocks) {
                        if (block.getStart()<firstStart.getTime()) {
                            firstStart = new Date(block.getStart());
                            continue;
                        }
                    }
                }
            }
        }
        return firstStart;
    }

}
