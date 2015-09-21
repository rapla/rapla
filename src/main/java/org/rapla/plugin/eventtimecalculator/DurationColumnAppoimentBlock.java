package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.tableview.AppointmentTableColumn;

import javax.inject.Inject;

/**
* User: kuestermann
* Date: 22.08.12
* Time: 09:30
*/
public final class DurationColumnAppoimentBlock extends DurationColumn implements AppointmentTableColumn {

	EventTimeCalculatorFactory factory;
    private EventTimeModel eventTimeModel;

    @Inject
    public DurationColumnAppoimentBlock(RaplaContext context, EventTimeCalculatorResources i18n) throws RaplaException {
        super(context,i18n);
        factory = context.lookup(EventTimeCalculatorFactory.class);
    }

    public String getValue(AppointmentBlock block) {
    	if ( !validConf )
    	{
    		eventTimeModel = factory.getEventTimeModel();
    		validConf = true;
    	}
        return eventTimeModel.format(eventTimeModel.calcDuration(block));
    }

    public String getHtmlValue(AppointmentBlock block) {
        String dateString = getValue(block);
        return dateString;
    }
}
