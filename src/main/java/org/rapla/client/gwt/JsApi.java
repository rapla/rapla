package org.rapla.client.gwt;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.rapla.client.ReservationController;
import org.rapla.client.menu.RaplaObjectActions;
import org.rapla.entities.User;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.storage.dbrm.RemoteAuthentificationService;

import javax.inject.Inject;
import javax.inject.Provider;

@JsType
public class JsApi
{
    private final RaplaFacade facade;
    private final Logger logger;
    ReservationController reservationController;
    private final CalendarSelectionModel calendarModel;
    private final RemoteAuthentificationService remoteAuthentificationService;
    private final RaplaLocale raplaLocale;
    private final ClientFacade clientFacade;
    private final Provider<RaplaBuilder> raplaBuilder;
    private final Provider<RaplaObjectActions> raplaObjectActionsProvider;

    @JsIgnore
    @Inject
    public JsApi(ClientFacade facade, Logger logger, ReservationController reservationController, CalendarSelectionModel calendarModel,
            RemoteAuthentificationService remoteAuthentificationService, RaplaLocale raplaLocale, Provider<RaplaBuilder> raplaBuilder,
            Provider<RaplaObjectActions> raplaObjectActionsProvider)
    {
        this.clientFacade = facade;
        this.facade = clientFacade.getRaplaFacade();
        this.logger = logger;
        this.reservationController = reservationController;
        this.calendarModel = calendarModel;
        this.remoteAuthentificationService = remoteAuthentificationService;
        this.raplaLocale = raplaLocale;
        this.raplaBuilder = raplaBuilder;
        this.raplaObjectActionsProvider = raplaObjectActionsProvider;
    }

    public User getUser( ) throws RaplaException
    {
        return clientFacade.getUser();
    }

    public RaplaFacade getFacade()
    {
        return facade;
    }

    public CalendarSelectionModel getCalendarModel()
    {
        return calendarModel;
    }

    public RemoteAuthentificationService getRemoteAuthentification()
    {
        return remoteAuthentificationService;
    }

    public RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }

    public ReservationController getReservationController()
    {
        return reservationController;
    }

    public RaplaBuilder createBuilder() { return raplaBuilder.get();};

    public RaplaObjectActions createActions()
    {
        return raplaObjectActionsProvider.get();
    }
}
