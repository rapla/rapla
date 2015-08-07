package org.rapla.client.edit.reservation.sample.gwt.subviews;

import org.rapla.entities.domain.Appointment;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;

public class RaplaDate extends FlowPanel implements Comparable<RaplaDate>
{

    private final Appointment appointment;

    public RaplaDate(Appointment appointment)
    {
        this.appointment = appointment;
        setStyleName("date");
        add(new HTML(appointment.getStart().toString()));
    }

    public Appointment getAppointment()
    {
        return appointment;
    }

    public int compareTo(RaplaDate arg0)
    {
        if (arg0 != null)
        {
            return appointment.compareTo(arg0.appointment);
        }
        return 1;
    }

}
