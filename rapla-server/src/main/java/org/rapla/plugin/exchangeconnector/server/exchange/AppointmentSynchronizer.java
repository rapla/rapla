package org.rapla.plugin.exchangeconnector.server.exchange;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.error.ServiceError;
import microsoft.exchange.webservices.data.core.enumeration.property.*;
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
import microsoft.exchange.webservices.data.misc.OutParam;
import microsoft.exchange.webservices.data.property.complex.*;
import microsoft.exchange.webservices.data.property.complex.recurrence.pattern.Recurrence;
import microsoft.exchange.webservices.data.property.complex.time.TimeZoneDefinition;
import microsoft.exchange.webservices.data.property.definition.ExtendedPropertyDefinition;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask.SyncStatus;
import org.rapla.framework.TimeZoneConverter;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import java.time.LocalDateTime;
/**
 * 
 * synchronizes a rapla appointment with an exchange appointment.
 * contains the interface to the exchange api 
 *
 */
public class AppointmentSynchronizer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentSynchronizer.class);
    private static final Logger EXCHANGE_UPDATE_LOG = LoggerFactory.getLogger("rapla.exchangeupdate");

    private static final ExtendedPropertyDefinition RAPLA_APPOINTMENT_MARKER;
    private static final ExtendedPropertyDefinition RAPLA_APPOINTMENT_ID;
    private static final ExtendedPropertyDefinition RAPLA_LAST_UPDATED_TIME;
    public static final String T_00_00_00_Z = "1970-01-01T00:00:00Z";

    static
    {
        try
        {
            RAPLA_APPOINTMENT_ID = new ExtendedPropertyDefinition(DefaultExtendedPropertySet.Appointment, "raplaId", MapiPropertyType.String);
            RAPLA_APPOINTMENT_MARKER = new ExtendedPropertyDefinition(DefaultExtendedPropertySet.Appointment, "isRaplaMeeting", MapiPropertyType.Boolean);
            RAPLA_LAST_UPDATED_TIME = new ExtendedPropertyDefinition(DefaultExtendedPropertySet.Appointment, "raplaLastUpdate", MapiPropertyType.String);
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
    private final Map<ReferenceInfo<Allocatable>, CalendarFolder> usedSharedMailboxes;
    private final EWSConnector ewsConnector;
    private final String exchangeTimezoneId;
    private final String exchangeAppointmentCategory;
    private final Locale locale;


    public AppointmentSynchronizer(TimeZoneConverter converter, final String exchangeTimezoneId,
            final String exchangeAppointmentCategory, User user, EWSConnector ewsConnector, boolean sendNotificationMail,
            SynchronizationTask appointmentTask, Appointment appointment, Locale locale, Map<ReferenceInfo<Allocatable>, CalendarFolder> usedSharedMailboxes)
    {
        this.usedSharedMailboxes = usedSharedMailboxes;
        this.sendNotificationMail = sendNotificationMail;
        this.raplaUser = user;
        this.locale = locale;
        this.ewsConnector = ewsConnector;
        timeZoneConverter = converter;
        this.raplaAppointment = appointment;
        this.appointmentTask = appointmentTask;
        this.exchangeTimezoneId = exchangeTimezoneId;
        this.exchangeAppointmentCategory = exchangeAppointmentCategory;
    }

    static public Collection<String> remove(EWSConnector ewsConnector, CalendarFolder folder) throws RaplaException
    {
        Collection<String> errors = new LinkedHashSet<>();
        List<ExchangeAppointment> exchangeAppointments = getExchangeAppointments(ewsConnector, folder);
        if (exchangeAppointments.isEmpty())
            return errors;
        AffectedTaskOccurrence affectedTaskOccurrences = AffectedTaskOccurrence.AllOccurrences;
        SendCancellationsMode sendCancellationsMode = SendCancellationsMode.SendToNone;
        DeleteMode deleteMode = DeleteMode.SoftDelete;
        ServiceResponseCollection<ServiceResponse> deleteItems;
        try
        {
            ExchangeService service = ewsConnector.getService();
            Iterable<ItemId> itemIds = exchangeAppointments.stream().map(ExchangeAppointment::getItemId).collect(Collectors.toList());
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
                ExchangeAppointment item = exchangeAppointments.get(index);
                String errorMessage = resultItem.getErrorMessage();
                if (errorMessage == null || errorMessage.isEmpty())
                {
                    errorMessage = "UnknownError";
                }
                String subject;
                try
                {
                    subject = item.getExchangeAppointment().getSubject();
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

    @NotNull
    /** EWS {@code ItemView} offsets count items, so a full page advances by the page size; -1 = no further page. */
    public static int nextPageOffset(int offset, int pageSize, int returned) {
        return returned == pageSize ? offset + pageSize : -1;
    }

    private static final ExtendedPropertyDefinition PR_CREATOR_NAME = newMapiString(0x3FF8);

    private static ExtendedPropertyDefinition newMapiString(int tag) {
        try { return new ExtendedPropertyDefinition(tag, MapiPropertyType.String); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** An item is rapla's own if the sync account created it and the owner did not mark it private (PRD 114 hunks 7+10).
     *  Items the mailbox owner created (an Outlook copy keeps the rapla marker) are never rapla's; owner edits of rapla items
     *  do not change that, but a private item cannot be updated or deleted by a delegate (Exchange answers "not found").
     *  Names are compared by the login's local part (RaplaTermin ~ raplatermin@...). */
    public static boolean ownedBySyncAccount(String creatorName, Sensitivity sensitivity, String syncLogin) {
        if (sensitivity != null && sensitivity != Sensitivity.Normal) return false;
        return creatorName == null || isSyncAccountName(creatorName, syncLogin);
    }

    /** "RaplaTermin VS" ~ RaplaTermin.VS@…, "rapla-termin" ~ rapla-termin@…: compare letters/digits only, either side may be the shorter one */
    static boolean isSyncAccountName(String displayName, String syncLogin) {
        if (displayName == null || syncLogin == null) return false;
        String a = displayName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String b = syncLogin.replaceFirst("@.*", "").toLowerCase().replaceAll("[^a-z0-9]", "");
        return !a.isEmpty() && !b.isEmpty() && (a.startsWith(b) || b.startsWith(a));
    }

    /** Last writer wins (PRD 114 hunk 12): an own item the mailbox owner modified after rapla's last change is left alone. */
    public static boolean ownerEditedAfterRapla(String lastModifierName, java.util.Date itemModified, java.util.Date raplaLastChanged, String syncLogin) {
        if (lastModifierName == null || itemModified == null || raplaLastChanged == null) return false;
        return !isSyncAccountName(lastModifierName, syncLogin) && itemModified.after(raplaLastChanged);
    }

    private String skipReason;

    /** set when addOrUpdate() deliberately wrote nothing (owner edit wins); reported by the manager */
    public String getSkipReason() {
        return skipReason;
    }

    /** Start, end and subject are what the lecturer sees; equal means the owner's private/copied item is still up to date (PRD 114 hunk 11). */
    public static boolean sameStartEndSubject(java.util.Date raplaStart, java.util.Date raplaEnd, String raplaSubject, java.util.Date exchangeStart, java.util.Date exchangeEnd, String exchangeSubject) {
        return raplaStart != null && raplaStart.equals(exchangeStart) && raplaEnd != null && raplaEnd.equals(exchangeEnd)
                && (raplaSubject == null ? exchangeSubject == null : raplaSubject.equals(exchangeSubject));
    }

    /** true if the owner's foreign item for this rapla appointment shows the same start, end and subject rapla would write. */
    public static boolean foreignItemUpToDate(TimeZoneConverter converter, Locale locale, Appointment raplaAppointment, ExchangeAppointment foreignItem) {
        try {
            microsoft.exchange.webservices.data.core.service.item.Appointment ex = foreignItem.getExchangeAppointment();
            return sameStartEndSubject(rapla2exchange(converter, raplaAppointment.getStart()), rapla2exchange(converter, raplaAppointment.getEnd()),
                    NameFormatUtil.getExportName(raplaAppointment, locale), ex.getStart(), ex.getEnd(), ex.getSubject());
        } catch (Exception e) {
            return false;
        }
    }

    public static List<ExchangeAppointment> getExchangeAppointments(EWSConnector ewsConnector, CalendarFolder folder) throws RaplaException {
        List<ExchangeAppointment> exchangeAppointments = new ArrayList<>();
        try
        {
            int offset = 0;
            final int maxRequestedAppointments = 100;
            final SearchFilter searchFilter = new SearchFilter.Exists(RAPLA_APPOINTMENT_MARKER);
            ExchangeService service = ewsConnector.getService();
            while (offset >= 0)
            {
                final ItemView view = new ItemView(maxRequestedAppointments, offset);
                view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties, RAPLA_APPOINTMENT_ID, RAPLA_APPOINTMENT_MARKER, RAPLA_LAST_UPDATED_TIME, PR_CREATOR_NAME));
                final FindItemsResults<Item> foundItems = service.findItems(folder.getId(), searchFilter, view);
                offset = nextPageOffset(offset, maxRequestedAppointments, foundItems.getItems().size());

                for (Item item : foundItems)
                {

                    if (! (item instanceof microsoft.exchange.webservices.data.core.service.item.Appointment)) {
                        continue;
                    }

                    ItemId id = item.getId();
                    if (id == null) {
                        continue;
                    }

                    ExtendedPropertyCollection extendedProperties = item.getExtendedProperties();
                    String raplaAppointmentId;
                    {
                        OutParam<String> out = new OutParam<>();
                        if (!extendedProperties.tryGetValue(String.class, RAPLA_APPOINTMENT_ID, out)) {
                            continue;
                        }
                        raplaAppointmentId = out.getParam();
                    }
                    String raplaAppointmentLastChanged;
                    {
                        OutParam<String> out = new OutParam<>();
                        if (extendedProperties.tryGetValue(String.class, RAPLA_LAST_UPDATED_TIME, out)) {
                            raplaAppointmentLastChanged = out.getParam();
                        } else {
                            raplaAppointmentLastChanged = T_00_00_00_Z;
                        }
                    }
                    microsoft.exchange.webservices.data.core.service.item.Appointment appointment = (microsoft.exchange.webservices.data.core.service.item.Appointment) item;
                    String exchangeAppointmentId = id.getUniqueId();
                    ExchangeAppointment exchangeAppointment = new ExchangeAppointment(new ReferenceInfo<>(raplaAppointmentId, Appointment.class), exchangeAppointmentId, id, appointment, raplaAppointmentLastChanged);
                    OutParam<String> creator = new OutParam<>();
                    extendedProperties.tryGetValue(String.class, PR_CREATOR_NAME, creator);
                    exchangeAppointment.foreign = !ownedBySyncAccount(creator.getParam(), item.getSensitivity(), ewsConnector.getExchangeUsername());
                    exchangeAppointments.add(exchangeAppointment);
                }
            }
        }
        catch (Exception ex)
        {
            throw new RaplaException(ex.getMessage(), ex);
        }
        return exchangeAppointments;
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
        microsoft.exchange.webservices.data.core.service.item.Appointment existing = getExchangeAppointmentByRaplaId(service, raplaAppointment.getId());
        if (existing != null && !appointmentTask.isForced()
                && ownerEditedAfterRapla(existing.getLastModifiedName(), existing.getLastModifiedTime(), java.util.Date.from(raplaAppointment.getReservation().getLastChanged().toInstant(java.time.ZoneOffset.UTC)), ewsConnector.getExchangeUsername())
                && !sameStartEndSubject(rapla2exchange(raplaAppointment.getStart()), rapla2exchange(raplaAppointment.getEnd()), NameFormatUtil.getExportName(raplaAppointment, locale), existing.getStart(), existing.getEnd(), existing.getSubject())) {
            LOGGER.info("{} leaving {} as edited by the mailbox owner on {} (after rapla's last change)", getMailboxName(), raplaAppointment.getId(), existing.getLastModifiedTime());
            skipReason = "vom Postfach-Inhaber am " + existing.getLastModifiedTime() + " bearbeitet (nach Raplas letzter Aenderung) - unveraendert gelassen";
            return;
        }
        if (existing != null && isRecurringMaster(existing) != null && isRecurringMaster(existing) != (raplaAppointment.getRepeating() != null)) {
            LOGGER.info("{} re-creating {}: recurrence shape differs from the existing item", getMailboxName(), raplaAppointment.getId());
            existing.delete(DeleteMode.SoftDelete, SendCancellationsMode.SendToNone);
            existing = null;
        }
        if (existing == null) {
            for (ExchangeAppointment candidate : getExchangeAppointmentsById(service, raplaAppointment.getId())) {
                if (candidate.isForeign() && foreignItemUpToDate(timeZoneConverter, locale, raplaAppointment, candidate)) {
                    LOGGER.info("{} not creating {}: the owner's private/copied item already has the same start, end and subject", getMailboxName(), raplaAppointment.getId());
                    return;
                }
            }
        }
        microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment = getEquivalentExchangeAppointment(raplaAppointment);
        saveToExchangeServer(exchangeAppointment, sendNotificationMail);
        // FIXME it an error occurs exceptions may not be serialized correctly
        removeRecurrenceExceptions(exchangeAppointment);
        EXCHANGE_UPDATE_LOG.info("{} updated appointment {} took {} ms", getMailboxName(), raplaAppointment, System.currentTimeMillis() - time);
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
                    java.util.Date timeString = new SimpleDateFormat("hh:mm").parse(time);
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
        long time = System.currentTimeMillis();
        try
        {
            ExchangeService service = ewsConnector.getService();
            microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment = getExchangeAppointmentByRaplaId(service, identifier);
            if (exchangeAppointment != null)
            {
                LOGGER.debug("{}: Deleting {} {}", getMailboxName(), exchangeAppointment.getId().getUniqueId(), exchangeAppointment);
                exchangeAppointment.delete(DeleteMode.SoftDelete, SendCancellationsMode.SendToNone);
            }
        }
        catch (microsoft.exchange.webservices.data.core.exception.service.remote.ServiceResponseException e)
        {
            throw e;
        }
        //delete on the Exchange Server side
        //remove it from the "to-be-removed"-list
        EXCHANGE_UPDATE_LOG.info("{} Deleted appointment with id {} took {} ms", getMailboxName(), identifier, System.currentTimeMillis() - time);
    }

    private void saveToExchangeServer(microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment, boolean notify) throws Exception
    {
        // save the appointment to the server
        if (exchangeAppointment.isNew())
        {
            FolderId folderId = getFolderId();

            LOGGER.info("{}: Adding {} to exchange", getMailboxName(), exchangeAppointment.getSubject());
            SendInvitationsMode sendMode = notify ? SendInvitationsMode.SendOnlyToAll : SendInvitationsMode.SendToNone;
            exchangeAppointment.save(folderId,sendMode);
        }
        else
        {
            LOGGER.info("{}: Updating {} {},{}", getMailboxName(), exchangeAppointment.getId(), exchangeAppointment.getSubject(), exchangeAppointment.getWhen());
            SendInvitationsOrCancellationsMode sendMode = notify ? SendInvitationsOrCancellationsMode.SendOnlyToAll
                    : SendInvitationsOrCancellationsMode.SendToNone;
            exchangeAppointment.update(ConflictResolutionMode.AlwaysOverwrite, sendMode);
        }
    }

    private String getMailboxName() {
        return appointmentTask != null ? appointmentTask.getMailboxName() :"unkown";
    }

    /** all rapla-tagged items (own and foreign) carrying this rapla id in the task's calendar */
    private List<ExchangeAppointment> getExchangeAppointmentsById(ExchangeService service, String raplaId) throws Exception
    {
        List<ExchangeAppointment> result = new ArrayList<>();
        FolderId folderId = getFolderId();
        if (folderId == null) return result;
        ItemView view = new ItemView(10);
        view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties, RAPLA_APPOINTMENT_ID, RAPLA_LAST_UPDATED_TIME, PR_CREATOR_NAME));
        for (Item item : service.findItems(folderId, new SearchFilter.IsEqualTo(RAPLA_APPOINTMENT_ID, raplaId), view))
        {
            if (!(item instanceof microsoft.exchange.webservices.data.core.service.item.Appointment)) continue;
            OutParam<String> creator = new OutParam<>(), last = new OutParam<>();
            item.getExtendedProperties().tryGetValue(String.class, PR_CREATOR_NAME, creator);
            String lastChanged = item.getExtendedProperties().tryGetValue(String.class, RAPLA_LAST_UPDATED_TIME, last) ? last.getParam() : T_00_00_00_Z;
            ExchangeAppointment ea = new ExchangeAppointment(new ReferenceInfo<>(raplaId, Appointment.class), item.getId().getUniqueId(), item.getId(), (microsoft.exchange.webservices.data.core.service.item.Appointment) item, lastChanged);
            ea.foreign = !ownedBySyncAccount(creator.getParam(), item.getSensitivity(), ewsConnector.getExchangeUsername());
            result.add(ea);
        }
        return result;
    }

    private microsoft.exchange.webservices.data.core.service.item.Appointment getExchangeAppointmentByRaplaId(ExchangeService service, String raplaId) throws Exception
    {
        FolderId folderId = getFolderId();
        if (folderId == null) return null;
        try
        {
            final ItemView view = new ItemView(10);
            view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties, PR_CREATOR_NAME));
            final SearchFilter searchFilter = new SearchFilter.IsEqualTo(RAPLA_APPOINTMENT_ID, raplaId);
            final FindItemsResults<Item> items = service.findItems(folderId, searchFilter, view);
            for (Item item : items)
            {
                OutParam<String> creator = new OutParam<>();
                item.getExtendedProperties().tryGetValue(String.class, PR_CREATOR_NAME, creator);
                if (!ownedBySyncAccount(creator.getParam(), item.getSensitivity(), ewsConnector.getExchangeUsername()))
                {
                    continue;   // owner's copy carries the rapla marker too; private items are not writable for the delegate
                }
                if (item instanceof microsoft.exchange.webservices.data.core.service.item.Appointment)
                {
                    return (microsoft.exchange.webservices.data.core.service.item.Appointment) item;
                }
                return microsoft.exchange.webservices.data.core.service.item.Appointment.bind(service, item.getId());
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

        LocalDateTime start = raplaAppointment.getStart();
        LocalDateTime end = raplaAppointment.getEnd();
        java.util.Date startDate = rapla2exchange(start);
        java.util.Date endDate = rapla2exchange(end);

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
        Reservation reservation = raplaAppointment.getReservation();
        String lastUpdated = getLastUpdate(raplaAppointment);
        String categoryName = reservation.getClassification().getType().getName(locale);
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
        exchangeAppointment.setExtendedProperty(RAPLA_LAST_UPDATED_TIME, lastUpdated);
        addPersonsAndResources(exchangeAppointment);

        return exchangeAppointment;
    }

    // IsRecurring is false on a recurring master (it flags occurrences); the master is recognised by its AppointmentType
    public static Boolean isRecurringMaster(microsoft.exchange.webservices.data.core.service.item.Appointment item) {
        try { return item.getAppointmentType() == microsoft.exchange.webservices.data.core.enumeration.service.calendar.AppointmentType.RecurringMaster; }
        catch (Exception e) { return null; }   // unknown type: callers never act on it
    }

    public static String getLastUpdate(Appointment raplaAppointment) {
        Reservation reservation = raplaAppointment.getReservation();
        if ( reservation == null) {
            return T_00_00_00_Z;
        }
        LocalDateTime lastChanged = reservation.getLastChanged();
        String lastUpdated = lastChanged != null ? new SerializableDateTimeFormat().formatTimestamp(lastChanged) : T_00_00_00_Z;
        return lastUpdated;
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

    public static java.util.Date rapla2exchange(TimeZoneConverter timeZoneConverter, LocalDateTime date)
    {
        TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();
        LocalDateTime exportDate = timeZoneConverter.fromRaplaTime(timeZone, DateTools.toLocalDateTime(DateTools.toMilli(date)));
        return java.util.Date.from(exportDate.toInstant(java.time.ZoneOffset.UTC));
    }

    private java.util.Date rapla2exchange(LocalDateTime date)
    {
        TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();
        long time = DateTools.toMilli(date);
        int offset = 0;//TimeZoneConverterImpl.getOffset(timeZone, systemTimeZone, time);
        LocalDateTime offsetToSystemTime = DateTools.toLocalDateTime(time + offset);
        LocalDateTime exportDate = timeZoneConverter.fromRaplaTime(timeZone, offsetToSystemTime);
        LOGGER.debug("Rapladate {} converted to exchange {}", date, exportDate);
        return java.util.Date.from(exportDate.toInstant(java.time.ZoneOffset.UTC));
    }

    private LocalDateTime exchange2rapla(java.util.Date date)
    {
        if (date == null) return null;
        LocalDateTime ldt = LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneOffset.UTC);
        LocalDateTime importDate = timeZoneConverter.toRaplaTime(systemTimeZone, ldt);
        TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();
        return timeZoneConverter.toRaplaTime(timeZone, importDate);
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
        LocalDateTime start = raplaAppointment.getStart();
        java.util.Date startAsDate = java.util.Date.from(start.toInstant(java.time.ZoneOffset.UTC));
        calendar.setTime(startAsDate);
        int dayOfMonthInt = calendar.get(Calendar.DAY_OF_MONTH);

        Month month = Month.values()[calendar.get(Calendar.MONTH)];
        RepeatingType type = repeating.getType();
        int interval = repeating.getInterval();
        if (type.is(RepeatingType.DAILY))
        {
            returnVal = new Recurrence.DailyPattern(startAsDate, interval);
        }
        else if (type.is(RepeatingType.WEEKLY))
        {
            returnVal = new Recurrence.WeeklyPattern(startAsDate, interval, weeklyDays(repeating.getWeekdays()));
        }
        else if (type.is(RepeatingType.MONTHLY))
        {
            DayOfTheWeekIndex weekOfMonth = getWeekOfMonth(calendar);
            DayOfTheWeek dayOfWeek = getDayOfWeek(calendar);
            returnVal = new Recurrence.RelativeMonthlyPattern(startAsDate, interval, dayOfWeek, weekOfMonth);
        }
        else
        {
            returnVal = new Recurrence.YearlyPattern(startAsDate, month, dayOfMonthInt);
        }
        if (repeating.isFixedNumber())
        {
            returnVal.setNumberOfOccurrences(repeating.getNumber());
        }
        else
        {

            LocalDateTime end = repeating.getEnd();
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

    // rapla weekdays are SUNDAY=1..SATURDAY=7, the same order as DayOfTheWeek.values()
    public static DayOfTheWeek[] weeklyDays(java.util.Set<Integer> weekdays)
    {
        java.util.List<DayOfTheWeek> days = new java.util.ArrayList<DayOfTheWeek>();
        for (Integer weekday : new java.util.TreeSet<Integer>(weekdays))
        {
            days.add(DayOfTheWeek.values()[weekday - 1]);
        }
        return days.toArray(new DayOfTheWeek[days.size()]);
    }

    private DayOfTheWeek getDayOfWeek(Calendar calendar)
    {
        DayOfTheWeek dayOfWeek;
        {
            DayOfTheWeek[] values = DayOfTheWeek.values();
            int i = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            if (i < 0 || i >= values.length)
            {
                LOGGER.error("Illegal exchange values for repeating in day of week {} does not have index {}", values, i);
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
                LOGGER.error("Illegal exchange values for repeating in week of month {} does not have index {}", values, i);
                weekOfMonth = DayOfTheWeekIndex.First;
            }
            else
            {
                weekOfMonth = values[i];
            }
        }
        return weekOfMonth;
    }

    private boolean isDeletedRecurrenceRemoved(microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment, Set<LocalDateTime> exceptionDates)
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
                    final LocalDateTime originalStart = DateTools.cutDate(exchange2rapla(next.getOriginalStart()));
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
        SortedSet<LocalDateTime> exceptionDates = calcExceptionDates();
        if (exceptionDates.size() == 0)
        {
            return;
        }
        LocalDateTime lastException = exceptionDates.last();
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
                LOGGER.info(e.getMessage());
            }
            if (occurrence == null)
            {
                continue;
            }
            LocalDateTime exchangeException = DateTools.cutDate(exchange2rapla(occurrence.getStart()));
            if (exchangeException.isAfter(lastException))
            {
                break;
            }
            if (exceptionDates.contains(exchangeException))
            {
                LOGGER.info("{} Removing exception for {} {}", getMailboxName(), occurrence.getId().getUniqueId(), occurrence);
                occurrence.delete(DeleteMode.MoveToDeletedItems, SendCancellationsMode.SendOnlyToAll);
            }
        }
    }

    private SortedSet<LocalDateTime> calcExceptionDates()
    {
        SortedSet<LocalDateTime> exceptionDates = new TreeSet<>();
        if (raplaAppointment.isRepeatingEnabled())
        {
            LocalDateTime[] exceptions = raplaAppointment.getRepeating().getExceptions();
            for (LocalDateTime exceptionDate : exceptions)
            {
                exceptionDates.add(DateTools.cutDate(exceptionDate));
            }
        }
        return exceptionDates;
    }

}
