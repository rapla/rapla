package org.rapla.client;

import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;

import java.util.Collection;
import java.util.List;

import java.time.LocalDateTime;
public interface ReservationEdit<T> extends EditTaskPresenter.EditTaskView<Reservation,T>
{
    Promise<Void> addAppointment(LocalDateTime start, LocalDateTime end);

    Reservation getReservation();

    void addAppointmentListener(AppointmentListener listener);
    void removeAppointmentListener(AppointmentListener listener);
   
    Collection<Appointment> getSelectedAppointments();

    void editReservation(Reservation reservation, Reservation original,AppointmentBlock appointmentBlock) throws RaplaException;

    Reservation getOriginal();

    boolean hasChanged();

    void addExceptionsToCurrentAppointment(List<TimeInterval> exceptions);

    void setReservation(Reservation reservation, Appointment appointment) throws RaplaException;

    /** Applies the classification to the working copy as an undoable command on this editor's
     *  undo history — same machinery and history entry as a type change via the type-selector
     *  dropdown; the classification panel repaints itself, everything else stays untouched. */
    Promise<Void> changeClassificationUndoable(org.rapla.entities.dynamictype.Classification newClassification);
    //void updateReservation(Reservation persistent) throws RaplaException;

    //void deleteReservation() throws RaplaException;

    CommandHistory getCommandHistory();

    void updateView(ModificationEvent evt);

    void fireChange();

    boolean isNew();

    void setHasChanged(boolean b);
}