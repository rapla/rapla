package org.rapla.client.extensionpoints;

import org.rapla.client.PopupContext;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import java.util.Collection;

/** performs a check, if the reservation is entered correctly. An example of a reservation check is the conflict checker
 *  you can add an interactive check when the user stores a reservation
 **/
@ExtensionPoint(id="eventcheck",context = InjectionContext.swing)
public interface EventCheck 
{
    /** @param sourceComponent 
     * @return true if the reservation check is successful and false if the save dialog should be aborted*/
    boolean check(Collection<Reservation> reservation, PopupContext sourceComponent) throws RaplaException;
}
