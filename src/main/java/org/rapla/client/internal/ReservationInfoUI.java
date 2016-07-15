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

import java.util.ArrayList;
import java.util.List;

import org.rapla.RaplaResources;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;

public class ReservationInfoUI extends ClassificationInfoUI<Reservation> {

    private final PermissionController permissionController;
    AppointmentFormater appointmentFormater;

    public ReservationInfoUI(RaplaResources i18n, RaplaLocale raplaLocale, RaplaFacade facade, Logger logger, AppointmentFormater appointmentFormater)
    {
        super(i18n, raplaLocale, facade, logger);
        this.appointmentFormater = appointmentFormater;
        this.permissionController = facade.getPermissionController();
    }

    private void addRestriction(Reservation reservation, Allocatable allocatable, StringBuffer buf) {
        Appointment[] appointments = reservation.getRestriction(allocatable);
        if ( appointments.length == 0 )
            return;
        buf.append("<small>");
        buf.append(" (");
        for (int i=0; i<appointments.length ; i++) {
            if (i >0)
                buf.append(", ");
            encode(appointmentFormater.getShortSummary(appointments[i]), buf );
        }
        buf.append(")");
        buf.append("</small>");
    }

    private String allocatableList(Reservation reservation,Allocatable[] allocatables, User user, LinkController controller) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0;i<allocatables.length;i++) {
            Allocatable allocatable = allocatables[i];
            if ( user != null && !permissionController.canReadOnlyInformation(allocatable, user))
                continue;
            if (controller != null)
                controller.createLink(allocatable,getName(allocatable),buf);
            else
                encode(getName(allocatable), buf);
            addRestriction(reservation, allocatable, buf);
            if (i<allocatables.length-1) {
                buf.append (",");
            }
        }
        return buf.toString();
    }
    
    @Override
    public String getTooltip(Reservation reservation, User user) {
        StringBuffer buf = new StringBuffer();
        insertModificationRow( reservation, buf );
        insertClassificationTitle( reservation, buf );
        createTable( getAttributes( reservation, null, user, true),buf,false);
        return buf.toString();
    }
    
    @Override
    public String createHTMLAndFillLinks(Reservation reservation,LinkController controller, User user) {
        StringBuffer buf = new StringBuffer();
        insertModificationRow( reservation, buf );
        insertClassificationTitle( reservation, buf );
        createTable( getAttributes( reservation, controller, null, false),buf,false);
        this.insertAllAppointments( reservation, buf );
        return buf.toString();
    }
    
    public List<Row> getAttributes(Reservation reservation,LinkController controller, User user, boolean excludeAdditionalInfos) {
        ArrayList<Row> att = new ArrayList<Row>();
        att.addAll( getClassificationAttributes( reservation, excludeAdditionalInfos,controller, user ));
        ReferenceInfo<User> ownerId = reservation.getOwnerRef();
        if ( ownerId != null)
        {
            final String ownerName = getUsername( ownerId);
            String ownerText = encode(ownerName);
            att.add( new Row(getString("reservation.owner"), ownerText));
        }
        ReferenceInfo<User> lastChangeById = reservation.getLastChangedBy();
        if ( lastChangeById != null && (ownerId == null ||! lastChangeById.equals(ownerId))) {
        	final String lastChangedName = getUsername(lastChangeById);
            String lastChangeByText = encode(lastChangedName);
            att.add( new Row(getString("last_changed_by"), lastChangeByText));
        }

        Allocatable[] resources = reservation.getResources();
        String resourceList = allocatableList(reservation, resources, user, controller);  
        if (resourceList.length() > 0) {
            att.add (new Row( getString("resources"), resourceList ));
        }
        Allocatable[] persons = reservation.getPersons();
        String personList = allocatableList(reservation, persons, user, controller);
        if (personList.length() > 0) {
            att.add (new Row( getString("persons"), personList ) );
        }
        return att;
    }
    
    void insertAllAppointments(Reservation reservation, StringBuffer buf) {
        buf.append( "<table cellpadding=\"2\">");
        buf.append( "<tr>\n" );
        buf.append( "<td colspan=\"2\" class=\"label\">");
        String appointmentLabel = getString("appointments");
        encode(appointmentLabel, buf);
        buf.append( ":");
        buf.append( "</td>\n" );
        buf.append( "</tr>\n");
        Appointment[] appointments = reservation.getAppointments();
        for (int i = 0;i<appointments.length;i++) {
            buf.append( "<tr>\n" );
            buf.append( "<td valign=\"top\">\n");
            if (appointments[i].getRepeating() != null) {
                buf.append ("<img width=\"16\" height=\"16\" src=\"org/rapla/client/swing/gui/images/repeating.png\">");
            } else {
                buf.append ("<img width=\"16\" height=\"16\" src=\"org/rapla/client/swing/gui/images/single.png\">");
            }
            buf.append( "</td>\n");
            buf.append( "<td>\n");
            String appointmentSummary =
                appointmentFormater.getSummary( appointments[i] );
            encode( appointmentSummary, buf );
            Repeating repeating = appointments[i].getRepeating();
            if ( repeating != null ) {
                buf.append("<br>");
                buf.append("<small>");
                List<Period> periods = getPeriodModel().getPeriodsFor(appointments[i].getStart());
                String repeatingSummary =
                    appointmentFormater.getSummary(repeating,periods);
                encode( repeatingSummary, buf ) ;
                if ( repeating.hasExceptions() ) {
                    buf.append("<br>");
                    buf.append( appointmentFormater.getExceptionSummary(repeating) );
                }
                buf.append("</small>");
            }
            buf.append( "</td>\n");
            buf.append( "<td></td>");
            buf.append( "</tr>\n");
        }
        buf.append( "</table>\n");
    }
    
   

}

