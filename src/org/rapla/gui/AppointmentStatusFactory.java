package org.rapla.gui;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.toolkit.RaplaWidget;

public interface AppointmentStatusFactory {
	RaplaWidget createStatus(RaplaContext context, ReservationEdit reservationEdit) throws RaplaException;
}
