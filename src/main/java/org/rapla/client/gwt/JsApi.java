package org.rapla.client.gwt;

import jsinterop.annotations.JsType;
import org.rapla.client.ReservationController;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.dbrm.RemoteAuthentificationService;

import javax.inject.Inject;

@JsType
public class JsApi
{
    private final RaplaFacade facade;
    private final Logger logger;
    ReservationController reservationController;
    private final CalendarSelectionModel calendarModel;
    private final RemoteAuthentificationService remoteAuthentificationService;
    private final RaplaLocale raplaLocale;

    @Inject
    public JsApi(RaplaFacade facade, Logger logger, ReservationController reservationController, CalendarSelectionModel calendarModel,
            RemoteAuthentificationService remoteAuthentificationService, RaplaLocale raplaLocale)
    {
        this.facade = facade;
        this.logger = logger;
        this.reservationController = reservationController;
        this.calendarModel = calendarModel;
        this.remoteAuthentificationService = remoteAuthentificationService;
        this.raplaLocale = raplaLocale;
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
}
