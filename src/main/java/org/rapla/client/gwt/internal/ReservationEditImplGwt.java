package org.rapla.client.gwt.internal;

import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import jsinterop.annotations.JsType;
import org.rapla.RaplaResources;
import org.rapla.client.AppointmentListener;
import org.rapla.client.ReservationEdit;
import org.rapla.client.gwt.window.VueWindow;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

@JsType
@DefaultImplementation(context = InjectionContext.gwt, of = ReservationEdit.class)
public class ReservationEditImplGwt implements ReservationEdit<VueWindow>, VueWindow {

  private final RaplaLocale locale;
  private final RaplaResources i18n;
  private final Logger logger;

  public Reservation reservation;
  public Reservation original;
  public AppointmentBlock appointmentBlock;
  public final String name = "ReservationForm";
  private WindowAction[] actions;
  public Action onClose;

  @Inject
  public ReservationEditImplGwt(final Logger logger, final RaplaLocale locale, final RaplaResources i18n) {
    this.locale = locale;
    this.i18n = i18n;
    this.logger = logger;
  }

  @Override
  public Promise<Void> addAppointment(Date start, Date end) {
    return null;
  }

  @Override
  public Reservation getReservation() {
    return reservation;
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
    this.reservation = reservation;
    this.original = original;
    this.appointmentBlock = appointmentBlock;
  }

  @Override
  public Reservation getOriginal() {
    return original;
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
    return !original.isReadOnly();
  }

  @Override
  public void setHasChanged(boolean b) {

  }

  @Override
  public VueWindow getComponent() {
    return this;
  }

  @Override
  public boolean hasChanged() {
    return false;
  }

  @Override
  public void setReservation(Reservation reservation, Appointment appointment) {
    logger.info("ReservationEditImplGwt.setReservation");
    this.reservation = reservation;
  }

  @Override
  public void start(Consumer<Collection<Reservation>> save, Runnable close, Runnable deleteCmd) {
    logger.info("ReservationEditImplGwt.start: " + reservation);
    this.actions = new WindowAction[] {
      new WindowAction(
        "delete",
        i18n.getString("delete"),
        deleteCmd::run
      ),
      new WindowAction(
        "save",
        i18n.getString("save"),
        () -> save.accept(Collections.singleton(reservation))
      )
    };
    this.onClose = close::run;
  }

  @Override
  public Map<Reservation, Reservation> getEditMap() {
    return Collections.emptyMap();
  }

  @Override
  public WindowAction[] getActions() {
    return actions;
  }
}
