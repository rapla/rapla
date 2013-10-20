package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.tableview.ReservationTableColumn;

/**
* User: kuestermann
* Date: 22.08.12
* Time: 09:29
*/
public final class DurationColumnReservation extends DurationColumn implements ReservationTableColumn {

    private EventTimeModel eventTimeModel;
    EventTimeCalculatorFactory factory;
    public DurationColumnReservation(RaplaContext context) throws RaplaException {
    	super(context);
    	factory = context.lookup(EventTimeCalculatorFactory.class);
    }

    public String getValue(Reservation event) {
    	if ( !validConf )
    	{
    		eventTimeModel = factory.getEventTimeModel();
    		validConf = true;
    	}
    	return eventTimeModel.format(eventTimeModel.calcDuration(event));
    }


    public String getHtmlValue(Reservation object) {
    	String dateString= getValue(object);
    	return dateString;
    }
 }
