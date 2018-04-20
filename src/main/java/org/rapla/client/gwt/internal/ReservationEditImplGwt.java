package org.rapla.client.gwt.internal;

import io.reactivex.functions.Consumer;
import org.rapla.client.AppointmentListener;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.gwt.VueDialog;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.dialog.gwt.components.VueLabel;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

@DefaultImplementation(context = InjectionContext.gwt, of = ReservationEdit.class)
public class ReservationEditImplGwt implements ReservationEdit<VueComponent> {

  private Reservation reservation;

  @Inject
  public ReservationEditImplGwt() {
  }

  @Override
  public Promise<Void> addAppointment(Date start, Date end) {
    return null;
  }

  @Override
  public Reservation getReservation() {
    return null;
  }

  @Override
  public void addAppointmentListener(AppointmentListener listener) {

  }

  @Override
  public void removeAppointmentListener(AppointmentListener listener) {

  }

  @Override
  public Collection<Appointment> getSelectedAppointments() {
    return null;
  }

  @Override
  public void editReservation(Reservation reservation, Reservation original, AppointmentBlock appointmentBlock)
  throws RaplaException {

  }

  @Override
  public Reservation getOriginal() {
    return null;
  }

  @Override
  public CommandHistory getCommandHistory() {
    return null;
  }

  @Override
  public void updateView(ModificationEvent evt) {

  }

  @Override
  public void fireChange() {

  }

  @Override
  public boolean isNew() {
    return false;
  }

  @Override
  public void setHasChanged(boolean b) {

  }

  @Override
  public VueComponent getComponent() {
    return dialog;
  }

  @Override
  public boolean hasChanged() {
    return false;
  }

  @Override
  public void setReservation(Reservation reservation, Appointment appointment) {
    this.reservation = reservation;
  }

  private VueDialog dialog;

  @Override
  public void start(Consumer<Collection<Reservation>> save, Runnable close, Runnable deleteCmd) {

    // TODO: create a proxy for reservationform vue component
    dialog = new VueDialog(new VueLabel("hi"), new String[] {
      "Save",
      "Close"
    });

    final Promise<Integer> promise = dialog.getPromise();
    promise.thenAccept(i -> {
      if (i == 0) {
        // Save
        save.accept(Collections.singletonList(reservation));
      } else if (i == 1) {
        close.run();
      }
    });

  }

  @Override
  public Map<Reservation, Reservation> getEditMap() {
    return Collections.emptyMap();
  }
}
