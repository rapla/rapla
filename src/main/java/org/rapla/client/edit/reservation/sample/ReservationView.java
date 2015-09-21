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
		void onSaveButtonClicked();

		void onDeleteButtonClicked();

		boolean isDeleteButtonEnabled();

		void onCancelButtonClicked();
		
		void changeAttribute(Attribute attribute, Object newValue);

        Collection<DynamicType> getChangeableReservationDynamicTypes();

        void changeClassification(DynamicType newDynamicType);

        void newDateClicked();

        void deleteDateClicked();

        void selectAppointment(Appointment selectedAppointment);

        void timeChanged(Date startDate, Date endDate);

        void allDayEvent(boolean selected);

        void repeating(RepeatingType repeating);

        void convertAppointment();
	}

	void show(Reservation event);

	void hide();

    void showWarning(String string, String string2);

    void updateAppointments(Appointment newSelectedAppointment);
}
