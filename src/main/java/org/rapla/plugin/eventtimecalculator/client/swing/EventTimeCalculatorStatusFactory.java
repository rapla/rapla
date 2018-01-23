package org.rapla.plugin.eventtimecalculator.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.ReservationEdit;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

import javax.inject.Inject;
import javax.inject.Singleton;

@Extension(provides = AppointmentStatusFactory.class, id="eventtimecalculator")
@Singleton
public class EventTimeCalculatorStatusFactory implements AppointmentStatusFactory {
    private final EventTimeCalculatorFactory factory;
    private final EventTimeCalculatorResources resources;
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;

    @Inject
    public EventTimeCalculatorStatusFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, EventTimeCalculatorFactory factory, EventTimeCalculatorResources resources)
    {
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.factory = factory;
        this.resources = resources;
    }
	public RaplaWidget createStatus(ReservationEdit reservationEdit) throws RaplaException {
        return new EventTimeCalculatorStatusWidget(facade, i18n, raplaLocale, logger, reservationEdit,factory, resources);
    }
}
