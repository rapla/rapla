package org.rapla.client;

import jsinterop.annotations.JsType;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.scheduler.Promise;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/** Use the ReservationController to modify or createInfoDialog a {@link Reservation}.
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
@JsType
public interface ReservationController
{
//    void edit( Reservation reservation ) throws RaplaException;
//    void edit( AppointmentBlock appointmentBlock) throws RaplaException;
//
//    ReservationEdit[] getEditWindows();


    Promise<Void> deleteAppointment( AppointmentBlock appointmentBlock, PopupContext context );
    Promise<Void> copyAppointmentBlock(AppointmentBlock appointmentBlock, PopupContext context, Collection<Allocatable> contextAllocatables );
    Promise<Void> cutAppointment(AppointmentBlock appointmentBlock, PopupContext context, Collection<Allocatable> contextAllocatables);
    Promise<Void> pasteAppointment( Date start, PopupContext context, boolean asNewReservation, boolean keepTime );
    Promise<Void> copyReservations(Collection<Reservation> reservations,Collection<Allocatable> contextAllocatables );
    Promise<Void> cutReservations(Collection<Reservation> reservations,Collection<Allocatable> contextAllocatables );

    /**
     * @param keepTime when moving only the date part and not the time part is modified*/
    Promise<Void> moveAppointment( AppointmentBlock appointmentBlock,  Date newStart, PopupContext context, boolean keepTime );
    /**
     * @param keepTime when moving only the date part and not the time part is modified*/
    Promise<Void> resizeAppointment( AppointmentBlock appointmentBlock, Date newStart, Date newEnd, PopupContext context, boolean keepTime );
    
	Promise<Void> exchangeAllocatable(AppointmentBlock appointmentBlock, Allocatable oldAlloc, Allocatable newAlloc,Date newStart, PopupContext context);
	boolean isAppointmentOnClipboard();
	
	Promise<Void> deleteBlocks(Collection<AppointmentBlock> blockList, PopupContext context);
	Promise<Void> deleteReservations(Set<Reservation> reservation, PopupContext context);
    Promise<Void> saveReservations(Map<Reservation,Reservation> reservation, PopupContext context);
    Promise<Void> saveReservation( Reservation origReservation, Reservation reservation);
}