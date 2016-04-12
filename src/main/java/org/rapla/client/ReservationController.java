package org.rapla.client;

import java.util.Collection;
import java.util.Date;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;

/** Use the ReservationController to modify or create a {@link Reservation}.
    This class handles all interactions with the user. Examples:
    <ul>
    <li>
    If you edit a reservation it will first check, if there is already is an
    open edit-window for the reservation and will give focus to that window instead of
    creating a new one.
    </li>
    <li>
    If you move or delete an repeating appointment it will display dialogs
    where the user will be asked if he wants to delete/move the complete appointment
    or just the occurrence on the selected date.
    </li>
    <li>
    If conflicts are found, a conflict panel will be displayed on saving.
    </li>
    </ul>
 */
public interface ReservationController
{
//    void edit( Reservation reservation ) throws RaplaException;
//    void edit( AppointmentBlock appointmentBlock) throws RaplaException;
//
//    ReservationEdit[] getEditWindows();

    /** copies an appointment without interaction */
    Appointment copyAppointment( Appointment appointment ) throws RaplaException;

    void deleteAppointment( AppointmentBlock appointmentBlock, PopupContext context )  throws RaplaException;

    void copyAppointment( AppointmentBlock appointmentBlock, PopupContext context,Collection<Allocatable> contextAllocatables ) throws RaplaException;
    void cutAppointment(AppointmentBlock appointmentBlock, PopupContext context, Collection<Allocatable> contextAllocatables) throws RaplaException;

    void pasteAppointment( Date start, PopupContext context, boolean asNewReservation, boolean keepTime ) throws RaplaException;

    void copyReservations(Collection<Reservation> reservations,Collection<Allocatable> contextAllocatables )  throws RaplaException;
    
    void cutReservations(Collection<Reservation> reservations,Collection<Allocatable> contextAllocatables )  throws RaplaException;

    /**
     * @param keepTime when moving only the date part and not the time part is modified*/
    void moveAppointment( AppointmentBlock appointmentBlock,  Date newStart, PopupContext context, boolean keepTime ) throws RaplaException;

  
    /**
     * @param keepTime when moving only the date part and not the time part is modified*/
    void resizeAppointment( AppointmentBlock appointmentBlock, Date newStart, Date newEnd, PopupContext context, boolean keepTime ) throws RaplaException;
    
	void exchangeAllocatable(AppointmentBlock appointmentBlock, Allocatable oldAlloc, Allocatable newAlloc,Date newStart, PopupContext context) throws RaplaException;
	
	boolean isAppointmentOnClipboard();
	
	void deleteBlocks(Collection<AppointmentBlock> blockList, PopupContext context) throws RaplaException;
}