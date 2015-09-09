package org.rapla.client.edit.reservation.sample;

import java.util.Collection;
import java.util.Date;

import org.rapla.client.base.View;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.DynamicType;

public interface ReservationView<W> extends View<Presenter> {

	public interface Presenter {
		void onSaveButtonClicked(Reservation reservation);

		void onDeleteButtonClicked(Reservation reservation);

		boolean isDeleteButtonEnabled(Reservation reservation);

		void onCancelButtonClicked(Reservation reservation);
		
		void changeAttribute(Reservation reservation, Attribute attribute, Object newValue);

        Collection<DynamicType> getChangeableReservationDynamicTypes();

        void changeClassification(Reservation reservation, DynamicType newDynamicType);

        void newDateClicked(Reservation reservation);

        void deleteDateClicked(Reservation reservation);

        void selectAppointment(Reservation reservation, Appointment selectedAppointment);

        void timeChanged(Reservation reservation, Date startDate, Date endDate);

        void allDayEvent(Reservation reservation, boolean selected);

        void repeating(Reservation reservation, RepeatingType repeating);

        void convertAppointment(Reservation reservation);
	}

	void show(Reservation event);

	void hide(Reservation reservation);

    void showWarning(String string, String string2);

    void updateAppointments(Reservation reservation, Appointment[] allAppointments, Appointment newSelectedAppointment);
}
