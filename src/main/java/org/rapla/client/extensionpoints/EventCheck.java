package org.rapla.client.extensionpoints;

import org.rapla.client.PopupContext;
import org.rapla.entities.domain.Reservation;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;

import java.util.Collection;

/** performs a check, if the reservation is entered correctly. An example of a reservation check is the conflict checker
 *  you can add an interactive check when the user stores a reservation
 **/
@ExtensionPoint(context = InjectionContext.client, id = EventCheck.ID)
public interface EventCheck 
{
    String ID="eventcheck";
    /** @param sourceComponent 
     * @return true if the reservation check is successful and false if the save dialog should be aborted*/
    Promise<Boolean> check(Collection<Reservation> reservation, PopupContext sourceComponent);
}
