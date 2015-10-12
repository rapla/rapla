package org.rapla.plugin.tableview.client;

import java.util.Collection;

import org.rapla.client.base.View;
import org.rapla.plugin.tableview.client.CalendarTableView.Presenter;
import org.rapla.entities.domain.Reservation;

public interface CalendarTableView<W> extends View<Presenter> {

    public interface Presenter {

        void selectReservation(Reservation selectedObject);
    }

    void update(Collection<Reservation> result);

    W provideContent();

}
