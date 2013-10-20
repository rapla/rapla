package org.rapla.plugin.eventtimecalculator.client;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AppointmentStatusFactory;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.toolkit.RaplaWidget;

public class EventTimeCalculatorStatusFactory implements AppointmentStatusFactory {
	public RaplaWidget createStatus(RaplaContext context, ReservationEdit reservationEdit) throws RaplaException {
        return new EventTimeCalculatorStatusWidget(context, reservationEdit);
    }
}
