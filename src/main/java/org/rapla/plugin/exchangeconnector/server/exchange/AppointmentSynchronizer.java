package org.rapla.plugin.exchangeconnector.server.exchange;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.error.ServiceError;
import microsoft.exchange.webservices.data.core.enumeration.property.BodyType;
import microsoft.exchange.webservices.data.core.enumeration.property.DefaultExtendedPropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.LegacyFreeBusyStatus;
import microsoft.exchange.webservices.data.core.enumeration.property.MailboxType;
import microsoft.exchange.webservices.data.core.enumeration.property.MapiPropertyType;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.property.time.DayOfTheWeek;
import microsoft.exchange.webservices.data.core.enumeration.property.time.DayOfTheWeekIndex;
import microsoft.exchange.webservices.data.core.enumeration.property.time.Month;
import microsoft.exchange.webservices.data.core.enumeration.service.ConflictResolutionMode;
import microsoft.exchange.webservices.data.core.enumeration.service.DeleteMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SendCancellationsMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SendInvitationsMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SendInvitationsOrCancellationsMode;
import microsoft.exchange.webservices.data.core.enumeration.service.ServiceResult;
import microsoft.exchange.webservices.data.core.enumeration.service.calendar.AffectedTaskOccurrence;
import microsoft.exchange.webservices.data.core.exception.misc.ArgumentException;
import microsoft.exchange.webservices.data.core.exception.misc.ArgumentOutOfRangeException;
import microsoft.exchange.webservices.data.core.exception.service.local.ServiceLocalException;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceResponseException;
import microsoft.exchange.webservices.data.core.response.ServiceResponse;
import microsoft.exchange.webservices.data.core.response.ServiceResponseCollection;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.AppointmentSchema;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.property.complex.Attendee;
import microsoft.exchange.webservices.data.property.complex.DeletedOccurrenceInfo;
import microsoft.exchange.webservices.data.property.complex.DeletedOccurrenceInfoCollection;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.property.complex.MessageBody;
import microsoft.exchange.webservices.data.property.complex.recurrence.pattern.Recurrence;
import microsoft.exchange.webservices.data.property.complex.time.TimeZoneDefinition;
import microsoft.exchange.webservices.data.property.definition.ExtendedPropertyDefinition;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;
import org.jetbrains.annotations.Nullable;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask.SyncStatus;
import org.rapla.server.TimeZoneConverter;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 
 * synchronizes a rapla appointment with an exchange appointment.
 * contains the interface to the exchange api 
 *
 */
public class AppointmentSynchronizer
{

    private static final ExtendedPropertyDefinition RAPLA_APPOINTMENT_MARKER;
    private static final ExtendedPropertyDefinition RAPLA_APPOINTMENT_ID;
    static
    {
        try
        {
            RAPLA_APPOINTMENT_ID = new ExtendedPropertyDefinition(DefaultExtendedPropertySet.Appointment, "raplaId", MapiPropertyType.String);
            RAPLA_APPOINTMENT_MARKER = new ExtendedPropertyDefinition(DefaultExtendedPropertySet.Appointment, "isRaplaMeeting", MapiPropertyType.Boolean);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e.getMessage(), e);
        }

    }
    private static final String LINE_BREAK = "\n";
    private static final String BODY_ATTENDEE_LIST_OPENING_LINE = "The following resources participate in the appointment:" + LINE_BREAK;

    private final User raplaUser;
    private final TimeZoneConverter timeZoneConverter;
    private final Appointment raplaAppointment;
    private final TimeZone systemTimeZone = TimeZone.getDefault();
    private final SynchronizationTask appointmentTask;
    private final boolean sendNotificationMail;
    private final Logger logger;
    private final Map<ReferenceInfo<Allocatable>, CalendarFolder> usedSharedMailboxes;
    private final EWSConnector ewsConnector;
    private final String exchangeTimezoneId;
    private final String exchangeAppointmentCategory;
    private final Locale locale;


    public AppointmentSynchronizer(Logger logger, TimeZoneConverter converter, final String exchangeTimezoneId,
            final String exchangeAppointmentCategory, User user, EWSConnector ewsConnector, boolean sendNotificationMail,
            SynchronizationTask appointmentTask, Appointment appointment, Locale locale, Map<ReferenceInfo<Allocatable>, CalendarFolder> usedSharedMailboxes)
    {
        this.usedSharedMailboxes = usedSharedMailboxes;
        this.sendNotificationMail = sendNotificationMail;
        this.logger = logger;
        this.raplaUser = user;
        this.locale = locale;
        this.ewsConnector = ewsConnector;
        timeZoneConverter = converter;
        this.raplaAppointment = appointment;
        this.appointmentTask = appointmentTask;
        this.exchangeTimezoneId = exchangeTimezoneId;
        this.exchangeAppointmentCategory = exchangeAppointmentCategory;
    }

    static public Collection<String> remove(Logger logger, EWSConnector ewsConnector, CalendarFolder folder) throws RaplaException
    {
        Collection<String> errors = new LinkedHashSet<>();
        final Logger ewsLogger = logger.getChildLogger("webservice");
        final SearchFilter searchFilter = new SearchFilter.Exists(RAPLA_APPOINTMENT_MARKER);
        List<Item> items = new ArrayList<>();
        Collection<ItemId> itemIds = new ArrayList<>();

        try
        {
            boolean newRequestNeeded = true;
            int offset = 0;
            final int maxRequestedAppointments = 1;
            while (newRequestNeeded)
            {
                final ItemView view = new ItemView(maxRequestedAppointments, offset);
                ExchangeService service = ewsConnector.getService();
                final FindItemsResults<Item> foundItems = service.findItems(folder.getId(), searchFilter, view);
                if (foundItems.getItems().size() == maxRequestedAppointments)
                {
                    offset++;
                }
                else
                {
                    newRequestNeeded = false;
                }
                for (Item item : foundItems)
                {
                    ItemId id = item.getId();
                    if (id != null)
                    {
                        itemIds.add(id);
                        items.add(item);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            throw new RaplaException(ex.getMessage(), ex);
        }
        if (itemIds.isEmpty())
            return errors;
        AffectedTaskOccurrence affectedTaskOccurrences = AffectedTaskOccurrence.AllOccurrences;
        SendCancellationsMode sendCancellationsMode = SendCancellationsMode.SendToNone;
        DeleteMode deleteMode = DeleteMode.HardDelete;
        ServiceResponseCollection<ServiceResponse> deleteItems;
        try
        {
            ExchangeService service = ewsConnector.getService();
            deleteItems = service.deleteItems(itemIds, deleteMode, sendCancellationsMode, affectedTaskOccurrences);
        }
        catch (Exception e)
        {
            throw new RaplaException(e.getMessage(), e);
        }
        int index = 0;
        for (ServiceResponse resultItem : deleteItems)
        {
            ServiceResult code = resultItem.getResult();
            if (code == ServiceResult.Error)
            {
                Item item = items.get(index);
                String errorMessage = resultItem.getErrorMessage();
                if (errorMessage == null || errorMessage.isEmpty())
                {
                    errorMessage = "UnknownError";
                }
                String subject;
                try
                {
                    subject = item.getSubject();
                }
                catch (Exception e)
                {
                    subject = "Unknown";
                }
                if (subject != null)
                {
                    errorMessage = "Fehler beim Termin mit dem Betreff " + subject + ": " + errorMessage;
                }
                errors.add(errorMessage);
            }
            index++;
        }
        return errors;
    }

    public Logger getLogger()
    {
        return logger;
    }

    public Appointment getRaplaAppointment()
    {
        return raplaAppointment;
    }

    public void execute() throws Exception
    {
        SyncStatus status = appointmentTask.getStatus();
        switch (status)
        {
            case deleted:
                return;
            case synched:
                return;
            case toDelete:
                delete();
                appointmentTask.setStatus(SyncStatus.deleted);
                return;
            case toUpdate:
                addOrUpdate();
                appointmentTask.setStatus(SyncStatus.synched);
        }
    }

    /** This method holds the core functionality of the worker. It creates a {@link microsoft.exchange.webservices.data.core.service.item.Appointment} and saves its Exchange-Representation
     * (Appointment) to the Exchange Server.
     * @throws Exception
     */
    private void addOrUpdate() throws Exception
    {
        //ewsConnector.test();
        long time = System.currentTimeMillis();
        ExchangeService service = ewsConnector.getService();
        {
            microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment = getExchangeAppointmentByRaplaId(service, raplaAppointment.getId());
            if (isDeletedRecurrenceRemoved(exchangeAppointment, calcExceptionDates()))
            {
                delete();
            }
        }
        microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment = getEquivalentExchangeAppointment(raplaAppointment);
        saveToExchangeServer(exchangeAppointment, sendNotificationMail);
        // FIXME it an error occurs exceptions may not be serialized correctly
        removeRecurrenceExceptions(exchangeAppointment);
        Logger logger = getLogger().getChildLogger("exchangeupdate");
        logger.info(getMailboxName() + " updated appointment " + raplaAppointment + " took " + (System.currentTimeMillis() - time) + " ms ");
    }

    private synchronized void delete() throws Exception
    {
        String source = "2014-11-21+01:00";
        try
        {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'Z'");
            df.parse(source);
        }
        catch (ParseException ex)
        {
            int offset = 0;
            char offsetChar = '+';
            if (source.length() >= 10)
            {
                offsetChar = source.charAt(10);
                if (offsetChar == '+' || offsetChar == '-')
                {
                    String time = source.substring(11);
                    source = source.substring(0, 10);
                    Date timeString = new SimpleDateFormat("hh:mm").parse(time);
                    Calendar instance = Calendar.getInstance();
                    instance.setTime(timeString);
                    offset = instance.get(Calendar.HOUR_OF_DAY);
                }
            }
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            df.setTimeZone(TimeZone.getTimeZone("UTC" + offsetChar + offset));
            df.parse(source);
        }

        String identifier = appointmentTask.getAppointmentId();
        Logger logger = getLogger().getChildLogger("exchangeupdate");
        long time = System.currentTimeMillis();
        try
        {
            ExchangeService service = ewsConnector.getService();
            microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment = getExchangeAppointmentByRaplaId(service, identifier);
            if (exchangeAppointment != null)
            {
                try
                {
                    getLogger().debug("Deleting  " + exchangeAppointment.getId().getUniqueId() + " " + exchangeAppointment);
                    exchangeAppointment.delete(DeleteMode.HardDelete, SendCancellationsMode.SendToNone);
                }
                catch (ServiceResponseException e)
                {
                    getLogger().error(e.getMessage(), e);
                }
            }
        }
        catch (microsoft.exchange.webservices.data.core.exception.service.remote.ServiceResponseException e)
        {
            //can be ignored
        }
        //delete on the Exchange Server side
        //remove it from the "to-be-removed"-list
        logger.info(getMailboxName() + " Deleted appointment with id " + identifier + " took " + (System.currentTimeMillis() - time) + " ms ");
    }

    private void saveToExchangeServer(microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment, boolean notify) throws Exception
    {
        // save the appointment to the server
        if (exchangeAppointment.isNew())
        {
            FolderId folderId = getFolderId();

            getLogger().info(getMailboxName() +  "Adding " + exchangeAppointment.getSubject() + " to exchange");
            SendInvitationsMode sendMode = notify ? SendInvitationsMode.SendOnlyToAll : SendInvitationsMode.SendToNone;
            exchangeAppointment.save(folderId,sendMode);
        }
        else
        {
            getLogger().info(getMailboxName() +  "Updating " + exchangeAppointment.getId() + " " + exchangeAppointment.getSubject() + "," + exchangeAppointment.getWhen());
            SendInvitationsOrCancellationsMode sendMode = notify ? SendInvitationsOrCancellationsMode.SendOnlyToAll
                    : SendInvitationsOrCancellationsMode.SendToNone;
            exchangeAppointment.update(ConflictResolutionMode.AlwaysOverwrite, sendMode);
        }
    }

    private String getMailboxName() {
        return appointmentTask != null ? appointmentTask.getMailboxName() :"unkown";
    }

    private microsoft.exchange.webservices.data.core.service.item.Appointment getExchangeAppointmentByRaplaId(ExchangeService service, String raplaId) throws Exception
    {
        FolderId folderId = getFolderId();
        if (folderId == null) return null;
        try
        {
            final ItemView view = new ItemView(1);
            final SearchFilter searchFilter = new SearchFilter.IsEqualTo(RAPLA_APPOINTMENT_ID, raplaId);
            final FindItemsResults<Item> items = service.findItems(folderId, searchFilter, view);
            if (items != null && !items.getItems().isEmpty())
            {
                final Item item = items.iterator().next();
                if (item instanceof microsoft.exchange.webservices.data.core.service.item.Appointment)
                {
                    return (microsoft.exchange.webservices.data.core.service.item.Appointment) item;
                }
                final ItemId id = item.getId();
                return microsoft.exchange.webservices.data.core.service.item.Appointment.bind(service, id);
            }
        }
        catch (ServiceResponseException e)
        {
        }
        return null;
    }

    @Nullable
    private FolderId getFolderId() {
        ReferenceInfo<Allocatable> resourceId = new ReferenceInfo<>(appointmentTask.getResourceId(), Allocatable.class);
        CalendarFolder calendarFolder = this.usedSharedMailboxes.get(resourceId);
        if ( calendarFolder == null) {
            return null;
        }
        FolderId folderId = calendarFolder.getId();
        return folderId;
    }

    private microsoft.exchange.webservices.data.core.service.item.Appointment getEquivalentExchangeAppointment(Appointment raplaAppointment) throws Exception
    {
        ExchangeService service = ewsConnector.getService();
        microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment = null;
        if (exchangeAppointment == null)
        {
            final String raplaId = raplaAppointment.getId();
            exchangeAppointment = getExchangeAppointmentByRaplaId(service, raplaId);
        }
        if (exchangeAppointment == null)
        {
            exchangeAppointment = new microsoft.exchange.webservices.data.core.service.item.Appointment(service);
        }
        else
        {
            // Maybe we need this 
            //    exchangeAppointment.load();
        }
        // Maybe use thie ical uid to refer to the original appointment, check if a url is expected

        Date start = raplaAppointment.getStart();
        Date end = raplaAppointment.getEnd();
        Date startDate = rapla2exchange(start);
        Date endDate = rapla2exchange(end);
        exchangeAppointment.setStart(startDate);
        //String[] availableIDs = TimeZone.getAvailableIDs();
        //        TimeZone timeZone = TimeZone.getTimeZone("Etc/UTC");//timeZoneConverter.getImportExportTimeZone();
        //        Collection<TimeZoneDefinition> serverTimeZones = service.getServerTimeZones();
        //        
        //        ArrayList list = new ArrayList();
        //        TimeZoneDefinition tDef = null;
        //        for ( TimeZoneDefinition def: serverTimeZones)
        //            
        //        {
        //            if ( def.getId().indexOf("Berlin")>=0 )
        //            {
        //                tDef = def;
        //                continue;
        //            }
        //        }
        String name = exchangeTimezoneId;
        TimeZoneDefinition tDef = new MyTimeZoneDefinition(exchangeTimezoneId, name);
        exchangeAppointment.setStartTimeZone(tDef);
        exchangeAppointment.setEnd(endDate);
        exchangeAppointment.setEndTimeZone(tDef);
        exchangeAppointment.setIsAllDayEvent(raplaAppointment.isWholeDaysSet());
        String subject = NameFormatUtil.getExportName(raplaAppointment, locale);
        exchangeAppointment.setSubject(subject);
        exchangeAppointment.setIsResponseRequested(false);
        exchangeAppointment.setIsReminderSet(ExchangeConnectorConfig.DEFAULT_EXCHANGE_REMINDER_SET);
        exchangeAppointment.setLegacyFreeBusyStatus(LegacyFreeBusyStatus.valueOf(ExchangeConnectorConfig.DEFAULT_EXCHANGE_FREE_AND_BUSY));

        exchangeAppointment.getCategories().clearList();

        // add category for filtering
        exchangeAppointment.getCategories().add(exchangeAppointmentCategory);
        // add category for each event type
        String categoryName = raplaAppointment.getReservation().getClassification().getType().getName(locale);
        exchangeAppointment.getCategories().add(categoryName);

        //setExchangeRecurrence( );
        Repeating repeating = raplaAppointment.getRepeating();
        if (repeating != null)
        {
            Recurrence recurrence = getExchangeRecurrence(repeating);
            exchangeAppointment.setRecurrence(recurrence);
        }
        String messageBody = getMessageBody();
        exchangeAppointment.setBody(new MessageBody(BodyType.Text, messageBody));

        exchangeAppointment.setExtendedProperty(RAPLA_APPOINTMENT_ID, raplaAppointment.getId());
        exchangeAppointment.setExtendedProperty(RAPLA_APPOINTMENT_MARKER, Boolean.TRUE);
        addPersonsAndResources(exchangeAppointment);

        return exchangeAppointment;
    }

    static class MyTimeZoneDefinition extends TimeZoneDefinition
    {
        public MyTimeZoneDefinition(String id, String name)
        {
            super();
            this.id = id;
            this.name = name;
        }

        @Override
        public void validate() throws ServiceLocalException
        {

        }
    }

    private Date rapla2exchange(Date date)
    {
        //        return new Date( date.getTime() - DateTools.MILLISECONDS_PER_HOUR);
        TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();
        long time = date.getTime();
        int offset = 0;//TimeZoneConverterImpl.getOffset(timeZone, systemTimeZone, time);
        Date offsetToSystemTime = new Date(time + offset);
        Date exportDate = timeZoneConverter.fromRaplaTime(timeZone, offsetToSystemTime);
        getLogger().debug("Rapladate " + date + " converted to exchange " + exportDate);
        return exportDate;
        //Date exchangeDate = timeZoneConverter.fromRaplaTime(systemTimeZone, exportDate);
        //return exchangeDate;

        //		return exchangeDate ;
    }

    private Date exchange2rapla(Date date)
    {
        Date importDate = timeZoneConverter.toRaplaTime(systemTimeZone, date);
        TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();
        Date raplaDate = timeZoneConverter.toRaplaTime(timeZone, importDate);
        return raplaDate;
    }

    protected static final String RAPLA_NOSYNC_KEYWORD = "<==8NO_SYNC8==>";
    protected static final String RAPLA_BODY_MESSAGE = "Please do not change this item, to prevent inconsistencies!\n\n\n";

    private String getMessageBody() throws Exception
    {
        Reservation reservation = raplaAppointment.getReservation();
        String content;
        if ( reservation.getClassification().getType().getAnnotation(DynamicTypeAnnotations.KEY_DESCRIPTION_FORMAT_EXPORT) != null)
        {
            content = reservation.formatAppointment(locale, DynamicTypeAnnotations.KEY_DESCRIPTION_FORMAT_EXPORT, raplaAppointment);
        }
        else
        {
            String bodyAttendeeList = BODY_ATTENDEE_LIST_OPENING_LINE + getStringForRessources(raplaAppointment) + LINE_BREAK;
            content = RAPLA_BODY_MESSAGE;
            content += bodyAttendeeList.isEmpty() ? "" : bodyAttendeeList;
            content += RAPLA_NOSYNC_KEYWORD;
        }
        return content;
    }

    private String getStringForRessources(Appointment raplaAppointment)
    {
        final StringBuilder result = new StringBuilder();
        // get all restricted resources
        raplaAppointment
                .getReservation()
                .getAllocatablesFor(raplaAppointment)
                .filter( Allocatable::isPerson)
                .map( alloc-> alloc.getName( locale))
                .forEach( name-> result.append(name).append(LINE_BREAK));
        return result.toString();
    }

    private void addPersonsAndResources(microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment) throws Exception
    {
        //final DynamicType roomType = getClientFacade().getDynamicTypes();
        // get all restricted resources
        // join and check for mail address, if so, add to reservation
        exchangeAppointment.getRequiredAttendees().clear();
        exchangeAppointment.getResources().clear();

        final List<String> locationList = new ArrayList<>();

        final List<Allocatable> allocatables = raplaAppointment.getReservation().getAllocatablesFor(raplaAppointment).collect(Collectors.toList());
        for (Allocatable restrictedAllocatable : allocatables)
        {
            //String emailAttribute = config.get(ExchangeConnectorConfig.RAPLA_EVENT_TYPE_ATTRIBUTE_EMAIL);
            final Classification classification = restrictedAllocatable.getClassification();
            final String email = getEmail(classification);
            final String name = restrictedAllocatable.getName(locale);
            if (restrictedAllocatable.isPerson())
            {
                if (email != null && !email.equalsIgnoreCase(raplaUser.getEmail())  && sendNotificationMail)
                {
                    exchangeAppointment.getOptionalAttendees().add(name, email);
                    //                    if (ExchangeConnectorConfig.DEFAULT_EXCHANGE_EXPECT_RESPONSE)
                    //                        exchangeAppointment.setIsResponseRequested(true);
                }
            }
            else if (classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_LOCATION, "false").equals("true"))
            {
                if (email != null  && sendNotificationMail)
                {
                    final Attendee attendee = new Attendee(email);
                    attendee.setMailboxType(MailboxType.Mailbox);
                    attendee.setRoutingType("SMTP");
                    attendee.setName(name);
                    exchangeAppointment.getResources().add(attendee);
                }
                locationList.add(name);
            }
        }

        if (locationList.size() > 0)
        {
            StringBuilder location = new StringBuilder();
            for (String name : locationList)
            {
                location.append(name);
                if (locationList.size() > 1)
                {
                    location.append(", ");
                }
            }
            exchangeAppointment.setLocation(location.toString());
        }
    }

    private String getEmail(final Classification classification)
    {
        Attribute emailAttribute = getEmailAttribute(classification);
        final String email = emailAttribute != null ? classification.getValueAsString(emailAttribute, null) : null;
        if (email != null && email.isEmpty())
        {
            return null;
        }
        return email;
    }

    private Attribute getEmailAttribute(final Classification classification)
    {
        Attribute[] attributes = classification.getType().getAttributes();
        for (Attribute att : attributes)
        {
            String isEmail = att.getAnnotation(AttributeAnnotations.KEY_EMAIL);
            if (isEmail != null)
            {
                if (isEmail.equals("true"))
                {
                    return att;
                }
            }
            else if (att.getKey().equalsIgnoreCase("email"))
            {
                return att;
            }
        }
        return null;
    }

    private Recurrence getExchangeRecurrence(Repeating repeating) throws ArgumentOutOfRangeException, ArgumentException
    {
        final Recurrence returnVal;
        Calendar calendar = new GregorianCalendar();
        Date start = raplaAppointment.getStart();
        calendar.setTime(start);
        int dayOfMonthInt = calendar.get(Calendar.DAY_OF_MONTH);

        Month month = Month.values()[calendar.get(Calendar.MONTH)];
        RepeatingType type = repeating.getType();
        int interval = repeating.getInterval();
        if (type.is(RepeatingType.DAILY))
        {
            returnVal = new Recurrence.DailyPattern(start, interval);
        }
        else if (type.is(RepeatingType.WEEKLY))
        {
            DayOfTheWeek dayOfWeek = getDayOfWeek(calendar);
            returnVal = new Recurrence.WeeklyPattern(start, interval, dayOfWeek);
        }
        else if (type.is(RepeatingType.MONTHLY))
        {
            DayOfTheWeekIndex weekOfMonth = getWeekOfMonth(calendar);
            DayOfTheWeek dayOfWeek = getDayOfWeek(calendar);
            returnVal = new Recurrence.RelativeMonthlyPattern(start, interval, dayOfWeek, weekOfMonth);
        }
        else
        {
            returnVal = new Recurrence.YearlyPattern(start, month, dayOfMonthInt);
        }
        if (repeating.isFixedNumber())
        {
            returnVal.setNumberOfOccurrences(repeating.getNumber());
        }
        else
        {

            Date end = repeating.getEnd();
            if (end != null)
            {
                returnVal.setEndDate(rapla2exchange(DateTools.subDay(end)));
            }
            else
            {
                returnVal.neverEnds();
            }
        }
        return returnVal;
    }

    private DayOfTheWeek getDayOfWeek(Calendar calendar)
    {
        DayOfTheWeek dayOfWeek;
        {
            DayOfTheWeek[] values = DayOfTheWeek.values();
            int i = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            if (i < 0 || i >= values.length)
            {
                getLogger().error("Illegal exchange values for repeating in day of week " + values + " does not have index " + i);
                dayOfWeek = DayOfTheWeek.Monday;
            }
            else
            {
                dayOfWeek = values[i];
            }
        }
        return dayOfWeek;
    }

    private DayOfTheWeekIndex getWeekOfMonth(Calendar calendar)
    {
        DayOfTheWeekIndex weekOfMonth;
        {
            DayOfTheWeekIndex[] values = DayOfTheWeekIndex.values();
            int i = calendar.get(Calendar.WEEK_OF_MONTH) - 1;
            if (i < 0 || i >= values.length)
            {
                getLogger().error("Illegal exchange values for repeating in week of month " + values + " does not have index " + i);
                weekOfMonth = DayOfTheWeekIndex.First;
            }
            else
            {
                weekOfMonth = values[i];
            }
        }
        return weekOfMonth;
    }

    private boolean isDeletedRecurrenceRemoved(microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment, Set<Date> exceptionDates)
    {
        boolean result = false;
        try
        {
            exchangeAppointment.load(new PropertySet(AppointmentSchema.DeletedOccurrences));
            final DeletedOccurrenceInfoCollection deletedOccurrences = exchangeAppointment.getDeletedOccurrences();
            if (deletedOccurrences != null)
            {
                final Iterator<DeletedOccurrenceInfo> iterator = deletedOccurrences.iterator();
                while (iterator.hasNext())
                {
                    final DeletedOccurrenceInfo next = iterator.next();
                    final Date originalStart = DateTools.cutDate(exchange2rapla(next.getOriginalStart()));
                    if (!exceptionDates.contains(originalStart))
                    {
                        result = true;
                        break;
                    }
                }
            }
        }
        catch (Exception e)
        {
        }
        return result;
    }

    private void removeRecurrenceExceptions(microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment) throws Exception
    {
        SortedSet<Date> exceptionDates = calcExceptionDates();
        if (exceptionDates.size() == 0)
        {
            return;
        }
        Date lastException = exceptionDates.last();
        ItemId id = exchangeAppointment.getId();
        final int MAX_TRIES=1000;
        for (int occurrenceIndex = 1;occurrenceIndex<MAX_TRIES;occurrenceIndex++)
        {
            ExchangeService service = ewsConnector.getService();
            microsoft.exchange.webservices.data.core.service.item.Appointment occurrence = null;
            try
            {
                occurrence = microsoft.exchange.webservices.data.core.service.item.Appointment.bindToOccurrence(service, id, occurrenceIndex);
            }
            catch (ServiceResponseException e)
            {
                if(e.getErrorCode() == ServiceError.ErrorItemNotFound || e.getErrorCode() == ServiceError.ErrorCalendarOccurrenceIndexIsOutOfRecurrenceRange)
                {
                    break;
                }
                logger.info(e.getMessage());
            }
            if (occurrence == null)
            {
                continue;
            }
            Date exchangeException = DateTools.cutDate(exchange2rapla(occurrence.getStart()));
            if (exchangeException.after(lastException))
            {
                break;
            }
            if (exceptionDates.contains(exchangeException))
            {
                getLogger().info(getMailboxName() + " Removing exception for " + occurrence.getId().getUniqueId() + " " + occurrence);
                occurrence.delete(DeleteMode.MoveToDeletedItems, SendCancellationsMode.SendOnlyToAll);
            }
        }
    }

    private SortedSet<Date> calcExceptionDates()
    {
        SortedSet<Date> exceptionDates = new TreeSet<>();
        if (raplaAppointment.isRepeatingEnabled())
        {
            Date[] exceptions = raplaAppointment.getRepeating().getExceptions();
            for (Date exceptionDate : exceptions)
            {
                exceptionDates.add(DateTools.cutDate(exceptionDate));
            }
        }
        return exceptionDates;
    }

}
