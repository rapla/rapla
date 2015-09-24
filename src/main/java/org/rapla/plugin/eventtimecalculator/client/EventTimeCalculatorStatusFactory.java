package org.rapla.plugin.eventtimecalculator.client;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.toolkit.RaplaWidget;
import org.rapla.inject.Extension;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

import javax.inject.Inject;
import javax.inject.Singleton;

@Extension(provides = AppointmentStatusFactory.class, id="eventtimecalculator")
@Singleton
public class EventTimeCalculatorStatusFactory<T> implements AppointmentStatusFactory<T> {
    private final EventTimeCalculatorFactory factory;
    private final EventTimeCalculatorResources resources;

    @Inject
    public EventTimeCalculatorStatusFactory(EventTimeCalculatorFactory factory, EventTimeCalculatorResources resources)
    {
        this.factory = factory;
        this.resources = resources;
    }
	public RaplaWidget<T> createStatus(RaplaContext context, ReservationEdit reservationEdit) throws RaplaException {
        return new EventTimeCalculatorStatusWidget(context, reservationEdit,factory, resources);
    }
}
