package org.rapla.client.plugin.tableview;

import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;

import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.event.DetailSelectEvent;
import org.rapla.client.plugin.tableview.CalendarTableView.Presenter;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

import com.google.web.bindery.event.shared.EventBus;

public class CalendarTableViewPresenter<W> implements Presenter, CalendarPlugin {

    private CalendarTableView<W> view;
    @Inject
    private Logger logger;
    @Inject
    private ClientFacade facade;
    @Inject
    private EventBus eventBus;
    
    @Inject
    private CalendarSelectionModel model;

    @Inject
    public CalendarTableViewPresenter(CalendarTableView view) {
        this.view = view;
        this.view.setPresenter(this);
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public W provideContent() {
        return view.provideContent();
    }
    
    @Override
    public void selectReservation(Reservation selectedObject){
        DetailSelectEvent event2 = new DetailSelectEvent(selectedObject, null);
        eventBus.fireEvent(event2);
        logger.info("selection changed");

    }

    @Override
    public void updateContent() {
//        Allocatable[] allocatables = null;
//        Date start = null;
//        Date end = null;
//        User user = null;

        try {
            Reservation[] reservations;
            reservations = model.getReservations();
            Collection<Reservation> result = Arrays.asList( reservations);
            logger.info(result.size() + " Reservations loaded.");
            view.update(result);                
        } catch (RaplaException e) {
            logger.error(e.getMessage(), e);
        }
        
//        ClassificationFilter[] reservationFilters = null;
//        FutureResult<Collection<Reservation>> reservationsAsync = facade.getReservationsAsync(user, allocatables, start, end, reservationFilters);
//        reservationsAsync.get( new AsyncCallback<Collection<Reservation>>() {
//            
//            @Override
//            public void onSuccess(Collection<Reservation> result) {
//                logger.info(result.size() + " Reservations loaded.");
//                view.update(result);                }
//            
//            @Override
//            public void onFailure(Throwable e) {
//                logger.error(e.getMessage(), e.getCause());
//            }
//        });

    }

}
