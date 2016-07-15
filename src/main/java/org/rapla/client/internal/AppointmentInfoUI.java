/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class AppointmentInfoUI extends HTMLInfo<Appointment> {
	ReservationInfoUI parent;
	AppointmentFormater appointmentFormater;
	
    public AppointmentInfoUI(RaplaResources i18n, RaplaLocale raplaLocale, RaplaFacade facade, Logger logger, AppointmentFormater appointmentFormater)
    {
        super( i18n, raplaLocale, facade, logger);
        parent = new ReservationInfoUI( i18n, raplaLocale, facade, logger, appointmentFormater);
        this.appointmentFormater = appointmentFormater;
    }
    

    @Override
    public String getTooltip(Appointment appointment,User user) {
        Reservation reservation =  appointment.getReservation();
        StringBuffer buf = new StringBuffer();
        parent.insertModificationRow( reservation, buf );
        insertAppointmentSummary( appointment, buf );
        parent.insertClassificationTitle( reservation, buf );
        createTable( parent.getAttributes( reservation, null, user, true),buf,false);
        return buf.toString();
    }
    
    void insertAppointmentSummary(Appointment appointment, StringBuffer buf) {
       buf.append("<div>");
       buf.append( appointmentFormater.getSummary( appointment ) );
       buf.append("</div>");
   }

    public String createHTMLAndFillLinks(Appointment appointment,
			LinkController controller, User user) throws RaplaException {
		Reservation reservation = appointment.getReservation();
		return parent.createHTMLAndFillLinks(reservation, controller, user);
	}
    

}

