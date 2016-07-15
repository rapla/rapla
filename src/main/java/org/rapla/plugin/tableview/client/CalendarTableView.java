package org.rapla.plugin.tableview.client;

import java.util.Collection;

import org.rapla.client.PopupContext;
import org.rapla.entities.domain.Reservation;

public interface CalendarTableView<W>  {

    interface Presenter {

        void selectReservation(Reservation selectedObject,PopupContext context);
    }

    void update(Collection<Reservation> result);
    
    void setPresenter(Presenter presenter);

    W provideContent();

}
