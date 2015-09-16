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
package org.rapla.plugin.notification.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.xmlbundle.CompoundI18n;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.AllocationChangeEvent;
import org.rapla.facade.AllocationChangeListener;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.mail.MailToUserInterface;
import org.rapla.plugin.notification.NotificationPlugin;
import org.rapla.server.ServerExtension;

/** Sends Notification Mails on allocation change.*/

public class NotificationService extends RaplaComponent
    implements
    AllocationChangeListener,
    ServerExtension
{
    ClientFacade clientFacade;
    MailToUserInterface mailToUserInterface;
    protected CommandScheduler mailQueue;
    AppointmentFormater appointmentFormater;

    @Inject
    public NotificationService(ClientFacade facade,@Named(RaplaComponent.RaplaResourcesId) I18nBundle   i18nBundle,@Named(NotificationPlugin.ResourceFileId) I18nBundle   childBundle, RaplaLocale raplaLocale, AppointmentFormater appointmentFormater, Provider<MailToUserInterface> mailToUserInterface, CommandScheduler mailQueue, Logger logger) throws RaplaException
    {
        super( facade, new  CompoundI18n(childBundle,i18nBundle), raplaLocale, logger);
        this.clientFacade = facade;
        setLogger( getLogger().getChildLogger("notification"));
        //setChildBundleName( NotificationPlugin.RESOURCE_FILE );
        try
        {
            this.mailToUserInterface = mailToUserInterface.get();
        }
        catch (Exception ex)
        {
            getLogger().error("Could not start notification service, because Mail Plugin not activated. Check for mail plugin activation or errors.");
            return;
        }
        this.mailQueue = mailQueue;
        clientFacade.addAllocationChangedListener(this);
        this.appointmentFormater = appointmentFormater;
        getLogger().info("NotificationServer Plugin started");
    }

    public void changed(AllocationChangeEvent[] changeEvents) {
        try {
            getLogger().debug("Mail check triggered") ;
            User[] users = clientFacade.getUsers();
            List<AllocationMail> mailList = new ArrayList<AllocationMail>();
            for ( int i=0;i< users.length;i++) {
                User user = users[i];
                if(user.getEmail().trim().length() == 0)
                	continue;
                
                Preferences preferences = clientFacade.getPreferences(user);
                Map<String,Allocatable> allocatableMap = null ;
                
                if (preferences != null && preferences.getEntry(NotificationPlugin.ALLOCATIONLISTENERS_CONFIG)!= null ) {
                    allocatableMap = preferences.getEntry(NotificationPlugin.ALLOCATIONLISTENERS_CONFIG);
                }else {
                	continue;
                }
                if ( allocatableMap != null && allocatableMap.size()> 0)
                {
                    boolean notifyIfOwner = preferences.getEntryAsBoolean(NotificationPlugin.NOTIFY_IF_OWNER_CONFIG, false);
                    AllocationMail mail = getAllocationMail( new HashSet<Allocatable>(allocatableMap.values()), changeEvents, preferences.getOwner(),notifyIfOwner);
                    if (mail != null) {
                        mailList.add(mail);
                    }

                }
            }
            if(!mailList.isEmpty()) {
            	MailCommand mailCommand = new MailCommand(mailList);
            	mailQueue.schedule(mailCommand,0);
            }
        } catch (RaplaException ex) {
            getLogger().error("Can't trigger notification service." + ex.getMessage(),ex);
        }
    }

    AllocationMail getAllocationMail(Collection<Allocatable> allocatables, AllocationChangeEvent[] changeEvents, User owner,boolean notifyIfOwner) throws RaplaException {

        HashMap<Reservation,List<AllocationChangeEvent>> reservationMap = null;
        HashSet<Allocatable> changedAllocatables = null;
        for ( int i = 0; i< changeEvents.length; i++) {
            if (reservationMap == null)
                reservationMap = new HashMap<Reservation,List<AllocationChangeEvent>>(4);
            AllocationChangeEvent event = changeEvents[i];
            Reservation reservation = event.getNewReservation();
            Allocatable allocatable = event.getAllocatable();
			if (!allocatables.contains(allocatable))
                continue;
            if (!notifyIfOwner && owner.equals(reservation.getOwner()))
                continue;
            List<AllocationChangeEvent> eventList = reservationMap.get(reservation);
            if (eventList == null) {
                eventList = new ArrayList<AllocationChangeEvent>(3);
                reservationMap.put(reservation,eventList);
            }
            if ( changedAllocatables == null)
            {
                changedAllocatables = new HashSet<Allocatable>();
            }
            changedAllocatables.add(allocatable);
            eventList.add(event);
        }
        if ( reservationMap == null || changedAllocatables == null) {
            return null;
        }
        Set<Reservation> keySet = reservationMap.keySet();
        // Check if we have any notifications.
        if (keySet.size() == 0)
            return null;

        AllocationMail mail = new AllocationMail();
        StringBuffer buf = new StringBuffer();
        //buf.append(getString("mail_body") + "\n");
        for (Reservation reservation:keySet) {
            List<AllocationChangeEvent> eventList = reservationMap.get(reservation);
            String eventBlock = printEvents(reservation,eventList);
            buf.append( eventBlock );
            buf.append("\n\n");
        }
        I18nBundle i18n = getI18n();
		String raplaTitle = getQuery().getSystemPreferences().getEntryAsString(ContainerImpl.TITLE, getString("rapla.title"));
		buf.append(i18n.format("disclaimer_1", raplaTitle));
        StringBuffer allocatableNames = new StringBuffer();
        for (Allocatable alloc: changedAllocatables) {
            if ( allocatableNames.length() > 0)
            {
                allocatableNames.append(", ");
            }
            allocatableNames.append(alloc.getName(getLocale()));
        }
        String allocatablesString = allocatableNames.toString();
        buf.append(i18n.format("disclaimer_2", allocatablesString));
		mail.subject = i18n.format("mail_subject",allocatablesString);
        mail.body = buf.toString();
        mail.recipient = owner.getUsername();
        return mail;
    }

    private String printEvents(Reservation reservation,List<AllocationChangeEvent> eventList) {
    	StringBuilder buf =new StringBuilder();
    	buf.append("\n");
        buf.append("-----------");
        buf.append(getString("changes"));
        buf.append("-----------");
        buf.append("\n");
        buf.append("\n");
        buf.append(getString("reservation"));
        buf.append(": ");
        buf.append(reservation.getName(getLocale()));
        buf.append("\n");
        buf.append("\n");
        Iterator<AllocationChangeEvent> it = eventList.iterator();
        boolean removed = true;
        boolean changed = false;
//        StringBuilder changes = new StringBuilder();
        while (it.hasNext()) {
            AllocationChangeEvent event = it.next();
            if (!event.getType().equals( AllocationChangeEvent.REMOVE ))
                removed = false;

            buf.append(getI18n().format("appointment." + event.getType()
                                        ,event.getAllocatable().getName(getLocale()))
                       );
//            changes.append("[" + event.getAllocatable().getName(getLocale()) + "]");
//            if(it.hasNext())
//            	changes.append(", ");
            if (!event.getType().equals(AllocationChangeEvent.ADD )) {
                printAppointment (buf, event.getOldAppointment() );
            }
            if (event.getType().equals( AllocationChangeEvent.CHANGE )) {
                buf.append(getString("moved_to"));
            }
            if (!event.getType().equals( AllocationChangeEvent.REMOVE )) {
                printAppointment (buf, event.getNewAppointment() );
            }
            /*
            if ( event.getUserFromRequest() != null) {
                buf.append("\n");
                buf.append( getI18n().format("modified_by", event.getUserFromRequest().getUsername() ) );
            }
            */
            Reservation newReservation = event.getNewReservation();
			if ( newReservation != null && changed == false) {
				User eventUser = event.getUser();
				User lastChangedBy = newReservation.getLastChangedBy();
				String name;  
				if ( lastChangedBy != null)
            	{
					name =  lastChangedBy.getName();
            	}
				else if ( eventUser != null)
				{
					name = eventUser.getName();
				}
				else
				{
					name = "Rapla";
				}
				buf.insert(0, getI18n().format("mail_body", name) + "\n");
            	changed = true;
            }
            
            buf.append("\n");
            buf.append("\n");
        }

        if (removed)
            return buf.toString();

        buf.append("-----------");
        buf.append(getString("complete_reservation"));
        buf.append("-----------");
        buf.append("\n");
        buf.append("\n");
        buf.append(getString("reservation.owner"));
        buf.append(": ");
        buf.append(reservation.getOwner().getUsername());
        buf.append(" <");
        buf.append(reservation.getOwner().getName());
        buf.append(">");
        buf.append("\n");
        buf.append(getString("reservation_type"));
        buf.append(": ");
        Classification classification = reservation.getClassification();
        buf.append( classification.getType().getName(getLocale()) );
        Attribute[] attributes = classification.getAttributes();
        for (int i=0; i< attributes.length; i++) {
            Object value = classification.getValue(attributes[i]);
            if (value == null)
                continue;
            buf.append("\n");
            buf.append(attributes[i].getName(getLocale()));
            buf.append(": ");
            buf.append(classification.getValueAsString(attributes[i], getLocale()));
        }

        Allocatable[] resources = reservation.getResources();
        if (resources.length>0) {
            buf.append("\n");
            buf.append( getString("resources"));
            buf.append( ": ");
            printAllocatables(buf,reservation,resources);
        }
        Allocatable[] persons = reservation.getPersons();
        if (persons.length>0) {
            buf.append("\n");
            buf.append( getString("persons"));
            buf.append( ": ");
            printAllocatables(buf,reservation,persons);
        }
        Appointment[] appointments = reservation.getAppointments();
        if (appointments.length>0) {
            buf.append("\n");
            buf.append("\n");
            buf.append( getString("appointments"));
            buf.append( ": ");
            buf.append("\n");
        }
        for (int i = 0;i<appointments.length;i++) {
            printAppointment(buf, appointments[i]);
        }
        return buf.toString();
    }


    private void printAppointment(StringBuilder buf, Appointment app) {
        buf.append("\n");
        buf.append(appointmentFormater.getSummary(app));
        buf.append("\n");
        Repeating repeating = app.getRepeating();
        if ( repeating != null ) {
            buf.append(appointmentFormater.getSummary(app.getRepeating()));
            buf.append("\n");
            if ( repeating.hasExceptions() ) {
                String exceptionString =
                    getString("appointment.exceptions") + ": " + repeating.getExceptions().length ;
                buf.append(exceptionString);
                buf.append("\n");
            }

        }
    }

    private String printAllocatables(StringBuilder buf
                                     ,Reservation reservation
                                     ,Allocatable[] allocatables) {
        for (int i = 0;i<allocatables.length;i++) {
            Allocatable allocatable = allocatables[i];
            buf.append(allocatable.getName( getLocale()));
            printRestriction(buf,reservation, allocatable);
            if (i<allocatables.length-1) {
                buf.append (",");
            }
        }
        return buf.toString();
    }

    private void printRestriction(StringBuilder buf
                                  ,Reservation reservation
                                  , Allocatable allocatable) {
        Appointment[] restriction = reservation.getRestriction(allocatable);
        if ( restriction.length == 0 )
            return;
        buf.append(" (");
        for (int i = 0;  i < restriction.length ; i++) {
            if (i >0)
                buf.append(", ");
            buf.append( appointmentFormater.getShortSummary( restriction[i]) );
        }
        buf.append(")");
    }


    class AllocationMail {
        String recipient;
        String subject;
        String body;
        public String toString() {
            return "TO Username: " + recipient + "\n"
                + "Subject: " + subject + "\n"
                + body;
        }
    }

    final class MailCommand implements Command {
        List<AllocationMail> mailList;
        public MailCommand(List<AllocationMail> mailList) {
            this.mailList = mailList;
        }

        public void execute() {
            Iterator<AllocationMail> it = mailList.iterator();
            while (it.hasNext()) {
                AllocationMail mail = it.next();
                if (getLogger().isDebugEnabled())
                    getLogger().debug("Sending mail " + mail.toString());
                getLogger().info("AllocationChange. Sending mail to " + mail.recipient);
                try {
                    mailToUserInterface.sendMail(mail.recipient, mail.subject, mail.body );
                    getLogger().info("AllocationChange. Mail sent.");
                } catch (RaplaException ex) {
                    getLogger().error("Could not send mail to " + mail.recipient + " Cause: " + ex.getMessage(), ex);
                }
            }
        }

    }


}