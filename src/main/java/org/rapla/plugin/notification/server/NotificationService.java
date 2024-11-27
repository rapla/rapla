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

import  io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.AllocationChangeEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.AllocationChangeFinder;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.mail.server.MailToUserImpl;
import org.rapla.plugin.notification.NotificationPlugin;
import org.rapla.plugin.notification.NotificationResources;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.UpdateResult;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.*;

/** Sends Notification Mails on allocation change.*/

@Extension(provides = ServerExtension.class, id = NotificationPlugin.PLUGIN_ID)
public class NotificationService implements ServerExtension
{
    static final String NOTIFICATION_LOCK_ID = "NOTIFICATION";
    private static final long VALID_LOCK = DateTools.MILLISECONDS_PER_MINUTE * 5;
    private final RaplaFacade raplaFacade;
    private final Provider<MailToUserImpl> mailToUserInterface;
    protected CommandScheduler scheduler;
    private final AppointmentFormater appointmentFormater;
    private final NotificationResources notificationI18n;
    private final RaplaResources raplaI18n;
    private final CachableStorageOperator operator;

    private final Logger logger;
    private final NotificationStorage notificationStorage;
    private final List<Disposable> scheduleList = new ArrayList<>();

    @Inject
    public NotificationService(RaplaFacade facade, RaplaResources i18nBundle, NotificationResources notificationI18n, AppointmentFormater appointmentFormater,
                               Provider<MailToUserImpl> mailToUserInterface, CommandScheduler scheduler, Logger logger, NotificationStorage notificationStorage)

    {
        this.notificationI18n = notificationI18n;
        this.raplaFacade = facade;
        this.raplaI18n = i18nBundle;
        this.notificationStorage = notificationStorage;
        this.logger = logger.getChildLogger("notification");
        //setChildBundleName( NotificationPlugin.RESOURCE_FILE );
        this.mailToUserInterface = mailToUserInterface;
        this.scheduler = scheduler;
        //raplaFacade.addAllocationChangedListener(this);
        this.appointmentFormater = appointmentFormater;
        this.operator = (CachableStorageOperator) facade.getOperator();

    }

    @Override
    public void start()
    {
        getLogger().info("NotificationServer Plugin started");
        getLogger().info("scheduling command for NotificationSercice");
        Action sentUpdateMails = () ->
        {
            Date lastUpdated = null;
            Date updatedUntil = null;
            try
            {
                lastUpdated = operator.requestLock(NOTIFICATION_LOCK_ID, VALID_LOCK);
                final UpdateResult updateResult = operator.getUpdateResult(lastUpdated);
                changed(updateResult);
                // set it as last, so update must have been successful
                updatedUntil = updateResult.getUntil();
            }
            catch (Throwable t)
            {
                NotificationService.this.logger.warn("Could not prepare mail: " + t.getMessage(),t);
            }
            finally
            {
                if (lastUpdated != null)
                {
                    operator.releaseLock(NOTIFICATION_LOCK_ID, updatedUntil);
                }
            }
        };
        scheduleList.add( scheduler.schedule( sentUpdateMails,0, 30000L));
        Action retryMails = () ->
        {
            Date lastUpdated = null;
            try
            {
                lastUpdated = operator.requestLock(NOTIFICATION_LOCK_ID, VALID_LOCK);
                final Collection<AllocationMail> mailsToSend = notificationStorage.getMailsToSend();
                sendMails(mailsToSend);
            }
            catch (Throwable t)
            {
                NotificationService.this.logger.warn("Could not send mail: " + t.getMessage());
            }
            finally
            {
                if (lastUpdated != null)
                {
                    operator.releaseLock(NOTIFICATION_LOCK_ID, null);
                }
            }
        };
        scheduleList.add(scheduler.schedule( retryMails, 45000L,DateTools.MILLISECONDS_PER_MINUTE * 15 + 531L));
    }

    public void stop()
    {
        scheduleList.forEach(Disposable::dispose);
    }

    protected Logger getLogger()
    {
        return logger;
    }

    public Locale getLocale()
    {
        return raplaI18n.getLocale();
    }

    private void changed(UpdateResult updateResult)
    {
        try
        {
            getLogger().debug("Mail check triggered");
            List<AllocationMail> mailList = getAllocationMails(updateResult);
            if (!mailList.isEmpty())
            {
                notificationStorage.store(mailList);
                sendMails(mailList);
            }
            List<AllocationMail> mailList2 = getBookingRequestMails(updateResult);
            if (!mailList2.isEmpty())
            {
                notificationStorage.store(mailList2);
                sendMails(mailList2);
            }
        }
        catch (RaplaException ex)
        {
            getLogger().error("Can't trigger notification service." + ex.getMessage(), ex);
        }
    }

    @NotNull
    private List<AllocationMail> getBookingRequestMails(UpdateResult updateResult) throws RaplaException {
        List<AllocationMail> mailList = new ArrayList<>();
        if (updateResult == null || !updateResult.getOperations().iterator().hasNext()) {
            return mailList;
        }
        User owner = null;
        Map<String,List<AllocationChangeEvent>> eventsPerEmail = new LinkedHashMap<>();
        final List<AllocationChangeEvent> changeEvents = AllocationChangeFinder.getTriggerEvents(updateResult, owner, logger, operator);
        for (AllocationChangeEvent event: changeEvents) 
        {
            final Allocatable allocatable = event.getAllocatable();
            final String email;
            AllocationChangeEvent.Type type = event.getType();
            if (type == AllocationChangeEvent.REQUESTED) {
                email = (String) allocatable.getClassification().getValue("Email");
            }
            else if (type == AllocationChangeEvent.CONFIRMED || type == AllocationChangeEvent.DENIED) {
                final Reservation newReservation = event.getNewReservation();
                final ReferenceInfo<User> ownerRef = newReservation.getOwnerRef();
                final User user = operator.tryResolve(ownerRef);
                if (user != null ) {
                     email = user.getEmail();
                } else {
                    email = null;
                }
             } else {
                email = null;
            }
            if ( email != null && !email.trim().isEmpty()) {
                eventsPerEmail.computeIfAbsent(email, l -> new ArrayList<>()).add(event);
            }
        }
        for ( Map.Entry<String,List<AllocationChangeEvent>> entry:eventsPerEmail.entrySet()) {
            String email = entry.getKey();

            final AllocationMail allocationMail = new AllocationMail();
            allocationMail.subject = "Buchungsanfrage für " ;
            Set<Allocatable> processed = new HashSet<>();
            allocationMail.recipient = email;
            allocationMail.body = "";
            for (AllocationChangeEvent event: entry.getValue())
            {
                final Allocatable allocatable = event.getAllocatable();
                if ( processed.contains( allocatable)){
                    continue;
                } else {
                    processed.add(allocatable);
                }
                if (event.getType() == AllocationChangeEvent.REQUESTED) {
                    final Reservation newReservation = event.getNewReservation();
                    allocationMail.subject+=  allocatable.getName( getLocale() ) + " gestellt ";
                    StringBuilder buf = new StringBuilder();
                    printReservation(newReservation, buf);
                    allocationMail.body += buf.toString();
                }
                if (event.getType() == AllocationChangeEvent.CONFIRMED) {
                    final Reservation newReservation = event.getNewReservation();
                    allocationMail.recipient = email;
                    allocationMail.subject += allocatable.getName(getLocale()) + " genehmigt ";
                    StringBuilder buf = new StringBuilder();
                    printReservation(newReservation, buf);
                    allocationMail.body += buf.toString();
                }

                if (event.getType() == AllocationChangeEvent.DENIED) {
                    final Reservation newReservation = event.getNewReservation();
                    allocationMail.recipient = email;
                    allocationMail.subject += allocatable.getName(getLocale()) + " abgelehnt ";
                    StringBuilder buf = new StringBuilder();
                    printReservation(newReservation, buf);
                    allocationMail.body += buf.toString();
                }
            }
            mailList.add( allocationMail );
        }
        return mailList;
    }

    @NotNull
    private List<AllocationMail> getAllocationMails(UpdateResult updateResult) throws RaplaException {
        User[] users = raplaFacade.getUsers();
        List<AllocationMail> mailList = new ArrayList<>();
        // we check for each user if a mail must be sent
        for (int i = 0; i < users.length; i++)
        {
            User user = users[i];
            if (user.getEmail().trim().length() == 0)
                continue;

            Preferences preferences = raplaFacade.getPreferences(user);
            RaplaMap< Allocatable> allocatableMap = null;

            if (preferences != null && preferences.getEntry(NotificationPlugin.ALLOCATIONLISTENERS_CONFIG) != null)
            {
                allocatableMap = preferences.getEntry(NotificationPlugin.ALLOCATIONLISTENERS_CONFIG);
            }
            else
            {
                continue;
            }
            if (allocatableMap != null && allocatableMap.size() > 0)
            {
                boolean notifyIfOwner = preferences.getEntryAsBoolean(NotificationPlugin.NOTIFY_IF_OWNER_CONFIG, false);
                final ReferenceInfo<User> ownerId = preferences.getOwnerRef();
                final User owner = ownerId != null ? raplaFacade.getOperator().resolve(ownerId) : null;
                AllocationMail mail = getAllocationMail(new HashSet<>(allocatableMap.values()), updateResult, owner, notifyIfOwner);
                if (mail != null)
                {
                    mailList.add(mail);
                }

            }
        }
        return mailList;
    }

    private void sendMails(Collection<AllocationMail> mails) throws RaplaException
    {
        Iterator<AllocationMail> it = mails.iterator();
        while (it.hasNext())
        {
            AllocationMail mail = it.next();
            if (getLogger().isDebugEnabled())
                getLogger().debug("Sending mail " + mail.toString());
            getLogger().info("AllocationChange. Sending mail to " + mail.recipient);
            try
            {
                mailToUserInterface.get().sendMailToEmail(mail.recipient, mail.subject, mail.body);
                notificationStorage.markSent(mail);
                getLogger().info("AllocationChange. Mail sent.");
            }
            catch (RaplaException ex)
            {
                getLogger().error("Could not send mail to " + mail.recipient + " Cause: " + ex.getMessage(), ex);
                notificationStorage.increateAndStoreRetryCount(mail);
            }
        }
    }

    AllocationMail getAllocationMail(Collection<Allocatable> allocatablesTheUsersListensTo, UpdateResult updateResult, User owner, boolean notifyIfOwner)
            throws RaplaException
    {
        if (updateResult == null || !updateResult.getOperations().iterator().hasNext())
        {
            return null;
        }
        final HashMap<Reservation, List<AllocationChangeEvent>> reservationMap = new HashMap<>(4);
        final HashSet<Allocatable> changedAllocatables = new HashSet<>();
        final List<AllocationChangeEvent> changeEvents = AllocationChangeFinder.getTriggerEvents(updateResult, owner, logger, operator);
        for (int i = 0; i < changeEvents.size(); i++)
        {
            AllocationChangeEvent event = changeEvents.get(i);
            Reservation reservation = event.getNewReservation();
            Allocatable allocatable = event.getAllocatable();
            final String templateId = reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
            if (templateId != null)
            {
                continue;
            }
            // Did the user opt in for the resource?
            if (!allocatablesTheUsersListensTo.contains(allocatable))
                continue;
            if (!notifyIfOwner && (reservation.getLastChangedBy() != null && owner.getReference().equals(reservation.getLastChangedBy())))
                continue;
            List<AllocationChangeEvent> eventList = reservationMap.get(reservation);
            if (eventList == null)
            {
                eventList = new ArrayList<>(3);
                reservationMap.put(reservation, eventList);
            }
            changedAllocatables.add(allocatable);
            eventList.add(event);
        }
        if (reservationMap == null || changedAllocatables == null)
        {
            return null;
        }
        Set<Reservation> keySet = reservationMap.keySet();
        // Check if we have any notifications.
        if (keySet.size() == 0)
            return null;

        AllocationMail mail = new AllocationMail();
        StringBuffer buf = new StringBuffer();
        //buf.append(getString("mail_body") + "\n");
        for (Reservation reservation : keySet)
        {
            List<AllocationChangeEvent> eventList = reservationMap.get(reservation);
            String eventBlock = printEvents(reservation, eventList);
            buf.append(eventBlock);
            buf.append("\n\n");
        }
        String raplaTitle = raplaFacade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, raplaI18n.getString("rapla.title"));
        buf.append(notificationI18n.format("disclaimer_1", raplaTitle));
        StringBuffer allocatableNames = new StringBuffer();
        for (Allocatable alloc : changedAllocatables)
        {
            if (allocatableNames.length() > 0)
            {
                allocatableNames.append(", ");
            }
            allocatableNames.append(alloc.getName(getLocale()));
        }
        String allocatablesString = allocatableNames.toString();
        buf.append(notificationI18n.format("disclaimer_2", allocatablesString));
        mail.subject = notificationI18n.format("mail_subject", allocatablesString);
        mail.body = buf.toString();
        mail.recipient = owner.getEmail();
        return mail;
    }

    private String printEvents(Reservation reservation, List<AllocationChangeEvent> eventList)
    {
        StringBuilder buf = new StringBuilder();
        buf.append("\n");
        buf.append("-----------");
        buf.append(raplaI18n.getString("changes"));
        buf.append("-----------");
        buf.append("\n");
        buf.append("\n");
        buf.append(raplaI18n.getString("reservation"));
        buf.append(": ");
        {// TODO think about better solution 
            ReservationImpl resImpl = ((ReservationImpl) reservation);
            if (resImpl.getResolver() == null)
            {
                resImpl.setResolver(operator);
            }
            try
            {
                buf.append(reservation.getName(getLocale()));
            }
            catch (Throwable t)
            {
                // TODO is there a unknown event type needed?
                ReservationImpl anonymousReservation = new ReservationImpl(new Date(), new Date());
                DynamicType anonymousReservationType = operator.getDynamicType(StorageOperator.ANONYMOUSEVENT_TYPE);
                anonymousReservation.setClassification(anonymousReservationType.newClassification());
                // print unknown ressource
                buf.append(anonymousReservation.getName(getLocale()));
            }

        }
        buf.append("\n");
        buf.append("\n");
        Iterator<AllocationChangeEvent> it = eventList.iterator();
        boolean removed = true;
        boolean changed = false;
        //        StringBuilder changes = new StringBuilder();
        while (it.hasNext())
        {
            AllocationChangeEvent event = it.next();
            if (!event.getType().equals(AllocationChangeEvent.REMOVE))
                removed = false;

            buf.append(notificationI18n.format("appointment." + event.getType(), event.getAllocatable().getName(getLocale())));
            //            changes.append("[" + event.getAllocatable().getName(getLocale()) + "]");
            //            if(it.hasNext())
            //            	changes.append(", ");
            if (!event.getType().equals(AllocationChangeEvent.ADD))
            {
                Appointment oldAppointment = event.getOldAppointment();
                if ( oldAppointment!= null) {
                    printAppointment(buf, oldAppointment);
                }
            }
            if (event.getType().equals(AllocationChangeEvent.CHANGE))
            {
                buf.append(notificationI18n.getString("moved_to"));
            }
            if (!event.getType().equals(AllocationChangeEvent.REMOVE))
            {
                Appointment newAppointment = event.getNewAppointment();
                if (newAppointment != null) {
                    printAppointment(buf, newAppointment);
                }
            }
            /*
            if ( event.getUserFromRequest() != null) {
                buf.append("\n");
                buf.append( getI18n().format("modified_by", event.getUserFromRequest().getUsername() ) );
            }
            */
            Reservation newReservation = event.getNewReservation();
            if (newReservation != null && !changed)
            {
                User eventUser = event.getUser();
                ReferenceInfo<User> lastChangedBy = newReservation.getLastChangedBy();
                String name;
                if (lastChangedBy != null)
                {
                    final User user = raplaFacade.tryResolve(lastChangedBy);
                    name = user != null ? user.getName() : "unknown";
                }
                else if (eventUser != null)
                {
                    name = eventUser.getName();
                }
                else
                {
                    name = "Rapla";
                }
                buf.insert(0, notificationI18n.format("mail_body", name) + "\n");
                changed = true;
            }

            buf.append("\n");
            buf.append("\n");
        }

        if (removed)
            return buf.toString();

        buf.append("-----------");
        buf.append(notificationI18n.getString("complete_reservation"));
        buf.append("-----------");
        buf.append("\n");
        buf.append("\n");
        printReservation(reservation, buf);
        return buf.toString();
    }

    private void printReservation(Reservation reservation, StringBuilder buf) {


        ReferenceInfo<User> ownerId = reservation.getOwnerRef();
        User owner = ownerId != null ? raplaFacade.getOperator().tryResolve(ownerId) : null;
        if (owner != null)
        {
            buf.append(raplaI18n.getString("reservation.owner"));
            buf.append(": ");
            buf.append(owner.getUsername());
            buf.append(" <");
            buf.append(owner.getName());
            buf.append(">");
            buf.append("\n");
        }
        buf.append(raplaI18n.getString("reservation_type"));
        buf.append(": ");
        Classification classification = reservation.getClassification();
        buf.append(classification.getType().getName(getLocale()));
        Attribute[] attributes = classification.getAttributes();
        for (int i = 0; i < attributes.length; i++)
        {
            Object value = classification.getValueForAttribute(attributes[i]);
            if (value == null)
                continue;
            final String valueAsString = classification.getValueAsString(attributes[i], getLocale());
            if ( !valueAsString.trim().isEmpty())
            {
                buf.append("\n");
                buf.append(attributes[i].getName(getLocale()));
                buf.append(": ");
                buf.append(valueAsString);
            }
        }

        Allocatable[] resources = reservation.getResources();
        if (resources.length > 0)
        {
            buf.append("\n");
            buf.append(raplaI18n.getString("resources"));
            buf.append(": ");
            printAllocatables(buf, reservation, resources);
        }
        Allocatable[] persons = reservation.getPersons();
        if (persons.length > 0)
        {
            buf.append("\n");
            buf.append(raplaI18n.getString("persons"));
            buf.append(": ");
            printAllocatables(buf, reservation, persons);
        }
        Appointment[] appointments = reservation.getAppointments();
        if (appointments.length > 0)
        {
            buf.append("\n");
            buf.append("\n");
            buf.append(raplaI18n.getString("appointments"));
            buf.append(": ");
            buf.append("\n");
        }
        for (int i = 0; i < appointments.length; i++)
        {
            printAppointment(buf, appointments[i]);
        }
    }

    private void printAppointment(StringBuilder buf, Appointment app)
    {
        buf.append("\n");
        buf.append(appointmentFormater.getSummary(app));
        buf.append("\n");
        Repeating repeating = app.getRepeating();
        if (repeating != null)
        {
            buf.append(appointmentFormater.getSummary(app.getRepeating()));
            buf.append("\n");
            if (repeating.hasExceptions())
            {
                String exceptionString = raplaI18n.getString("appointment.exceptions") + ": " + repeating.getExceptions().length;
                buf.append(exceptionString);
                buf.append("\n");
            }

        }
    }

    private String printAllocatables(StringBuilder buf, Reservation reservation, Allocatable[] allocatables)
    {
        for (int i = 0; i < allocatables.length; i++)
        {
            Allocatable allocatable = allocatables[i];
            buf.append(allocatable.getName(getLocale()));
            printRestriction(buf, reservation, allocatable);
            if (i < allocatables.length - 1)
            {
                buf.append(",");
            }
        }
        return buf.toString();
    }

    private void printRestriction(StringBuilder buf, Reservation reservation, Allocatable allocatable)
    {
        Appointment[] restriction = reservation.getRestriction(allocatable);
        if (restriction.length == 0)
            return;
        buf.append(" (");
        for (int i = 0; i < restriction.length; i++)
        {
            if (i > 0)
                buf.append(", ");
            buf.append(appointmentFormater.getShortSummary(restriction[i]));
        }
        buf.append(")");
    }

    class AllocationMail
    {
        String recipient;
        String subject;
        String body;

        public String toString()
        {
            return "TO Username: " + recipient + "\n" + "Subject: " + subject + "\n" + body;
        }
    }

}