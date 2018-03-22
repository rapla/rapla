/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.plugin.abstractcalendar;

import org.rapla.RaplaResources;
import org.rapla.components.calendarview.Block;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import java.util.Date;


public class HTMLRaplaBuilder extends RaplaBuilder {
    
    static String COLOR_NO_RESOURCE = "#BBEEBB";
    int m_rowsPerHour = 4;
    /** shared calendar instance. Only used for temporary stored values. */
    String m_html;
    int index = 0;
    protected boolean onlyAllocationInfo;
    
    @Inject
    public HTMLRaplaBuilder(RaplaLocale raplaLocale, RaplaFacade raplaFacade, RaplaResources i18n, Logger logger, AppointmentFormater appointmentFormater) {
        super(raplaLocale, raplaFacade, i18n, logger, appointmentFormater);
        this.setBlockCreator(( blockContext, start, end)->createBlock(blockContext,start,end));
    }

    
    @Override
    public Promise<RaplaBuilder> initFromModel(CalendarModel model, Date startDate, Date endDate) 
    {
    	final Promise<RaplaBuilder> builderPromise = super.initFromModel(model, startDate, endDate);
    	final Promise<RaplaBuilder> nextBuilderPromise = builderPromise.thenApply((builder) -> {
    	    String option = model.getOption(CalendarModel.ONLY_ALLOCATION_INFO);
    	    if (option != null && option.equalsIgnoreCase("true"))
    	    {
    	        onlyAllocationInfo = true;
    	    }
    	    return builder;
    	});
    	return nextBuilderPromise;
    }
    
    @Override
    protected boolean isAnonymous(User user, Appointment appointment) {
    	if ( onlyAllocationInfo)
    	{
    		return true;
    	}
    	return super.isAnonymous(user, appointment);
    }

    @Override
    protected boolean isExceptionsExcluded() {
        return true;
    }


    private Block createBlock(RaplaBlockContext blockContext, Date start, Date end) {
        HTMLRaplaBlock block = new HTMLRaplaBlock(blockContext, start, end);
        block.setIndex( index ++ );

        int row = (int) (
            DateTools.getHourOfDay(start.getTime())* m_rowsPerHour
            + Math.round((DateTools.getMinuteOfHour(start.getTime()) * m_rowsPerHour)/60.0)
            );
        block.setRow(row);
        block.setDay(DateTools.getWeekday( start));
        int endRow = (int) (
            DateTools.getHourOfDay(end.getTime())* m_rowsPerHour
            + Math.round((DateTools.getMinuteOfHour(end.getTime()) * m_rowsPerHour)/60.0)
            );
        int rowCount = endRow -row;
        block.setRowCount(rowCount);
        //System.out.println("Start " + start + " End " + end);
        //System.out.println("Block " + block.getReservation().getName(null)
        //                   + " Row: " + row + " Endrow: " + endRow + " Rowcount " + rowCount );
        return block;
    }

}
