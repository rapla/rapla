package org.rapla.plugin.tableview.client;

import org.rapla.client.base.View;
import org.rapla.entities.domain.Reservation;
import org.rapla.plugin.tableview.client.CalendarTableView.Presenter;

import java.util.Collection;

public interface CalendarTableView<W> extends View<Presenter> {

    interface Presenter {

        void selectReservation(Reservation selectedObject);
    }

    void update(Collection<Reservation> result);

    W provideContent();

}
