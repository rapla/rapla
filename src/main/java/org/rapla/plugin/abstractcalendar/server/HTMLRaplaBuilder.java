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

package org.rapla.plugin.abstractcalendar.server;

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Named;

import org.rapla.components.calendarview.Block;
import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;


public class HTMLRaplaBuilder extends RaplaBuilder {
    
    static String COLOR_NO_RESOURCE = "#BBEEBB";
    int m_rowsPerHour = 4;
    /** shared calendar instance. Only used for temporary stored values. */
    String m_html;
    int index = 0;
    protected boolean onlyAllocationInfo;
    
    public HTMLRaplaBuilder(RaplaContext context) throws RaplaContextException {
        super(context.lookup(RaplaLocale.class),context.lookup(ClientFacade.class),context.lookup(RaplaComponent.RAPLA_RESOURCES), context.lookup(Logger.class),context.lookup( AppointmentFormater.class));
    }

    @Inject
    public HTMLRaplaBuilder(RaplaLocale raplaLocale, ClientFacade clientFacade, @Named(RaplaComponent.RaplaResourcesId) I18nBundle i18n, Logger logger, AppointmentFormater appointmentFormater) {
        super(raplaLocale, clientFacade, i18n, logger, appointmentFormater);
    }

    
    @Override
    public void setFromModel(CalendarModel model, Date startDate, Date endDate)
    		throws RaplaException {
    	super.setFromModel(model, startDate, endDate);
        {
        	String option = model.getOption(CalendarModel.ONLY_ALLOCATION_INFO);
        	if (option != null && option.equalsIgnoreCase("true"))
        	{
        		onlyAllocationInfo = true;
        	}
        }
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

    @Override
    protected Block createBlock(RaplaBlockContext blockContext, Date start, Date end) {
        HTMLRaplaBlock block = createBlock();
        block.setIndex( index ++ );
        block.setStart(start);
        block.setEnd(end);
        block.contextualize(blockContext);

        
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

    protected HTMLRaplaBlock createBlock() {
        HTMLRaplaBlock block = new HTMLRaplaBlock();
        return block;
    }

}
