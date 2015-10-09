package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.tableview.RaplaTableColumn;

import javax.inject.Inject;
import javax.swing.table.TableColumn;

//@Extension(provides = ReservationTableColumn.class, id = EventTimeCalculatorPlugin.PLUGIN_ID)
public final class DurationColumnReservation
        extends DurationColumn implements RaplaTableColumn<Reservation, TableColumn>
{

    private EventTimeModel eventTimeModel;
    EventTimeCalculatorFactory factory;

    @Inject public DurationColumnReservation(RaplaContext context, EventTimeCalculatorResources i18n) throws RaplaException
    {
        super(context, i18n);
        factory = context.lookup(EventTimeCalculatorFactory.class);
    }

    public String getValue(Reservation event)
    {
        if (!validConf)
        {
            eventTimeModel = factory.getEventTimeModel();
            validConf = true;
        }
        return eventTimeModel.format(eventTimeModel.calcDuration(event));
    }

    public String getHtmlValue(Reservation object)
    {
        String dateString = getValue(object);
        return dateString;
    }
}
