package org.rapla.client.edit.reservation.sample;

import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.DynamicType;

import java.util.Collection;
import java.util.Date;

public interface ReservationView {

	interface Presenter {
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
	
	void setPresenter(Presenter p);

	void show(Reservation event);
	
	boolean isVisible();

	void hide();

    void showWarning(String string, String string2);

    void updateAppointments(Appointment newSelectedAppointment);
}
