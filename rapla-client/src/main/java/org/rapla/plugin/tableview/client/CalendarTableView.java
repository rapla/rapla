package org.rapla.plugin.tableview.client;

import org.rapla.client.PopupContext;
import org.rapla.entities.domain.Reservation;

import java.util.Collection;

public interface CalendarTableView<W>  {

    interface Presenter {

        void selectReservation(Reservation selectedObject,PopupContext context);
    }

    void update(Collection<Reservation> result);
    
    void setPresenter(Presenter presenter);

    W provideContent();

}
