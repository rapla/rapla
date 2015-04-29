package org.rapla.gui;

import java.awt.Component;

import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;

/** performs a check, if the reservation is entered correctly. An example of a reservation check is the conflict checker
 * @deprecated use {@link EventCheck} instead
 * */
@Deprecated 
public interface ReservationCheck 
{
    /** @param sourceComponent 
     * @return true if the reservation check is successful and false if the save dialog should be aborted*/
    boolean check(Reservation reservation, Component sourceComponent) throws RaplaException;
}
