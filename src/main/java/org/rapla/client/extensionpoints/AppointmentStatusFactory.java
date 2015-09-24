package org.rapla.client.extensionpoints;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.toolkit.RaplaWidget;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/** add a footer for summary of appointments in edit window
 * provide an AppointmentStatusFactory to add your own footer to the appointment edit
 @see AppointmentStatusFactory
  * */
@ExtensionPoint(context = InjectionContext.swing,id = "appointmentstatus")
public interface AppointmentStatusFactory<T> {
	RaplaWidget<T> createStatus(RaplaContext context, ReservationEdit reservationEdit) throws RaplaException;
}
