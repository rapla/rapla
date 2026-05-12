package org.rapla.plugin.tableview.client;

import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.PopupContext;
import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.logger.Logger;
import org.rapla.plugin.tableview.client.CalendarTableView.Presenter;
import org.rapla.scheduler.Promise;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Collection;
import java.util.Collections;
import java.time.LocalDateTime;
public class CalendarTableViewPresenter implements Presenter, CalendarPlugin
{

    public static final String TABLE_VIEW = "table";
    private final CalendarTableView view;
    private final Logger logger;
    private final ApplicationEventBus eventBus;
    private final CalendarSelectionModel model;
    
    @SuppressWarnings("unchecked")
    @Autowired
    public CalendarTableViewPresenter(CalendarTableView view, Logger logger, ApplicationEventBus eventBus, CalendarSelectionModel model)
    {
        this.view = view;
        this.logger = logger;
        this.eventBus = eventBus;
        this.model = model;
        this.view.setPresenter(this);
    }
    
    @Override
    public boolean isEnabled()
    {
        return true;
    }

    @Override
    public String getName()
    {
        return "list";
    }

    @Override
    public LocalDateTime calcNext(LocalDateTime currentDate)
    {
        return DateTools.addMonths(currentDate, 1);
    }

    @Override
    public LocalDateTime calcPrevious(LocalDateTime currentDate)
    {
        return DateTools.addMonths(currentDate, -1);
    }

    @Override
    public Object provideContent()
    {
        updateContent();
        return view.provideContent();
    }

    @Override
    public void selectReservation(Reservation selectedObject, PopupContext context)
    {
        ApplicationEventContext editContext = new EditApplicationEventContext<>(Collections.singletonList(selectedObject));
        // Activity id preserved verbatim from the deleted sample presenter
        // (org.rapla.client.edit.reservation.sample.ReservationPresenter). No
        // production listener is registered for this id today — the call
        // was pre-existing, deliberately kept as-is to avoid silently
        // changing double-click behaviour while the sample is removed.
        final ApplicationEvent activity = new ApplicationEvent("editevent", selectedObject.getId(),context, editContext);
        eventBus.publish(activity);
        logger.info("selection changed");

    }

    @Override
    /** ASYNC */
    public void updateContent()
    {
        Promise<Collection<Reservation>> resultPromise = model.queryReservations(model.getTimeIntervall());
        resultPromise.thenAccept((result) ->
        {
            logger.info(result.size() + " Reservations loaded.");
            view.update(result);
        }).exceptionally((e) -> logger.error(e.getMessage(), e));
    }
}
