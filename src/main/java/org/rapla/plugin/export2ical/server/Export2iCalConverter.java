package org.rapla.plugin.export2ical.server;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import net.fortuna.ical4j.transform.recurrence.Frequency;
import net.fortuna.ical4j.util.CompatibilityHints;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.server.TimeZoneConverter;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Date;
import java.util.TimeZone;

public class Export2iCalConverter
{

    private final boolean global_export_attendees;
    private final String global_export_attendees_participation_status;

    net.fortuna.ical4j.model.TimeZone timeZone;
    private String exportAttendeesAttribute;
    final TimeZoneConverter timezoneConverter;
    TimeZoneRegistry registry;
    boolean hasLocationType;
    final Locale locale;
    final Logger logger;
    final RaplaFacade facade;
    final RaplaResources i18n;

    @Inject
    public Export2iCalConverter(TimeZoneConverter timezoneConverter, Logger logger, RaplaFacade facade, RaplaResources i18n)
            throws RaplaInitializationException
    {
        this.timezoneConverter = timezoneConverter;
        this.facade = facade;
        this.locale = i18n.getLocale();
        this.logger = logger;
        this.i18n = i18n;
        TimeZone zone = timezoneConverter.getImportExportTimeZone();
        DynamicType[] dynamicTypes;
        RaplaConfiguration config;
        try
        {
            config = facade.getSystemPreferences().getEntry(Export2iCalPlugin.ICAL_CONFIG, new RaplaConfiguration());
            dynamicTypes = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        for (DynamicType type : dynamicTypes)
        {
            if (type.getAnnotation(DynamicTypeAnnotations.KEY_LOCATION) != null)
            {
                hasLocationType = true;
            }
        }
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, true);

        global_export_attendees = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES).getValueAsBoolean(Export2iCalPlugin.DEFAULT_exportAttendees);
        global_export_attendees_participation_status = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS)
                .getValue(Export2iCalPlugin.DEFAULT_attendee_participation_status);

        try
        {
            exportAttendeesAttribute = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_EMAIL_ATTRIBUTE).getValue();
        }
        catch (ConfigurationException e)
        {
            exportAttendeesAttribute = "";
            getLogger().info("ExportAttendeesMailAttribute is not set. So do not export as meeting");
        }
        if (zone != null)
        {
            final String timezoneId = zone.getID();

            try
            {
                registry = TimeZoneRegistryFactory.getInstance().createRegistry();
                timeZone = registry.getTimeZone(timezoneId);
            }
            catch (Exception rc)
            {
                final VTimeZone vTimeZone = new VTimeZone();
                timeZone = new net.fortuna.ical4j.model.TimeZone(vTimeZone);
                final int rawOffset = zone.getRawOffset();
                timeZone.setRawOffset(rawOffset);
            }
        }
    }

    protected Logger getLogger()
    {
        return logger;
    }

    public Calendar createiCalender(Collection<Appointment> appointments, Preferences preferences, User user)
    {

        boolean doExportAsMeeting = preferences == null ?
                global_export_attendees :
                preferences.getEntryAsBoolean(Export2iCalPlugin.EXPORT_ATTENDEES_PREFERENCE, global_export_attendees);

        String exportAttendeesParticipationStatus = preferences == null ?
                global_export_attendees_participation_status :
                preferences.getEntryAsString(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE, global_export_attendees_participation_status);

        //ensure the stored value is not empty string, if so, do not export attendees
        doExportAsMeeting = doExportAsMeeting && (exportAttendeesAttribute != null && exportAttendeesAttribute.trim().length() > 0);

        Calendar calendar = initiCalendar();

        addVTimeZone(calendar);
        for (Appointment app : appointments)
        {
            VEvent event = createVEvent(app, doExportAsMeeting, exportAttendeesParticipationStatus, user);
            calendar.add(event);
        }
        return calendar;
    }

    private void addVTimeZone(Calendar calendar)
    {

        if (timeZone != null)
        {
            VTimeZone tz = timeZone.getVTimeZone();
            calendar.add(tz);
        }
    }

    /**
     * Initialisiert ein neues leeres iCalendar-Objekt.
     *
     * @return Ein neues leeres iCalendar-Objekt.
     */
    public Calendar initiCalendar()
    {
        Calendar calendar = new Calendar();
        calendar.add(new ProdId("-//Rapla//iCal Plugin//EN"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableMethod.PUBLISH);
        return calendar;

    }



    /**
     * Erstellt anhand des &uuml;bergebenen Appointment-Objekts einen
     * iCalendar-Event.
     *
     * @param appointment Ein Rapla Appointment.
     * @return Ein iCalendar-Event mit den Daten des Appointments.
     */
    private VEvent createVEvent(Appointment appointment, boolean doExportAsMeeting, String exportAttendeesParticipationStatus, User user)
    {
        VEvent event = new VEvent();

        boolean isAllDayEvent = appointment.isWholeDaysSet();
        addCreateDateToEvent(appointment, event);
        addStartDateToEvent(appointment, event, isAllDayEvent);
        addLastModifiedDateToEvent(appointment, event);
        addEndDateToEvent(appointment, event, isAllDayEvent);

        boolean canRead = facade.getPermissionController().canRead(appointment, user);
        addUidToEvent(appointment, event);
        addEventNameToEvent(appointment, event, canRead);
        if (canRead)
        {
            addDescriptionToEvent(appointment, event);
            addLocationToEvent(appointment, event, user);
            addCategories(appointment, event);
            addOrganizer(appointment, event, doExportAsMeeting);
            addAttendees(appointment, event, doExportAsMeeting, exportAttendeesParticipationStatus, user);
        }

        addRepeatings(appointment, event);


        return event;
    }

    private String getResourceName(Allocatable alloc, User user)
    {
        if (facade.getPermissionController().canRead(alloc, user))
        {
            final String name = NameFormatUtil.getExportName(alloc, locale);
            return name;
        }
        else
        {
            return i18n.getString("not_visible");
        }
    }

    /**
     * add organizer to properties
     *
     * @param appointment
     * @param properties
     */
    private void
    addOrganizer(Appointment appointment, VEvent properties, boolean doExportAsMeeting)
    {
        // means we do not export attendees so we do not have a meeting
        if (!doExportAsMeeting)
            return;
        ReferenceInfo<User> ownerId = appointment.getOwnerRef();
        User owner;
        try
        {
            owner = facade.resolve(ownerId);
        }
        catch (EntityNotFoundException e1)
        {
            getLogger().error("Error getting user for Export2iCal: " + e1.getMessage(), e1);
            return;
        }
        try
        {
            Organizer organizer = null;
            ParameterList params = new ParameterList();
            if (!"".equals(owner.getName())) {
                params = new ParameterList(Collections.singletonList(new Cn(owner.getName())));
            } else {
                params = new ParameterList();
            }
            if (owner.getEmail() != null && !owner.getEmail().trim().isEmpty())
            {
                try
                {
                    final URI uri = new URI("MAILTO:" + owner.getEmail().trim());
                    organizer = new Organizer(params,uri);
                }
                catch (URISyntaxException e)
                {
                }
            }
            if (organizer == null)
            {
                organizer = new Organizer(params,"MAILTO:" + URLEncoder.encode(owner.getUsername(), "UTF-8"));
            }
            properties.add(organizer);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * add attenddees if system property ical4j.validation.relaxed is set ony
     *
     * @param appointment
     * @param properties
     */
    private void addAttendees(Appointment appointment, VEvent properties, boolean doExportAsMeeting, String exportAttendeesParticipationStatus, User user)
    {
        if (!doExportAsMeeting)
            return;

        appointment.getReservation().getAllocatablesFor(appointment).filter(Allocatable::isPerson).forEach(person -> {
            String email = null;
            Attribute attr = person.getClassification().getAttribute(exportAttendeesAttribute);
            if (attr != null && person.getClassification().getValueForAttribute(attr) != null)
                email = person.getClassification().getValueForAttribute(attr).toString().trim();
            // determine if person has email attribute
            if (email != null && email.length() > 0)
            {
                try
                {
                    final String resourceName = getResourceName(person, user);
                    if (!isEmpty(resourceName))
                    {
                        ParameterList parameterList = new ParameterList(Arrays.asList(Role.REQ_PARTICIPANT, new Cn(resourceName), new PartStat(exportAttendeesParticipationStatus)));
                        Attendee attendee = new Attendee(parameterList,"MAILTO:" + new URI(email.trim()));
                        properties.add(attendee);
                    }
                }
                catch (URISyntaxException e)
                {
                    throw new IllegalArgumentException(e);
                }
            }
        });
    }

    private boolean isEmpty(String allocatableName)
    {
        return allocatableName == null || allocatableName.trim().isEmpty();
    }

    private boolean notEmpty(String allocatableName)
    {
        return !isEmpty(allocatableName);
    }



    /**
     * Fuegt dem Termin den DTSTAMP hinzu lt. rfc muss dies dem Erstellungdatum
     * des Objektes entsprechen. Da dies jedoch den Import erschwert und sinnlos
     * ist, wird es auf das letzte Modifizierungsdatum geschrieben.
     *
     */
    private void addLastModifiedDateToEvent(Appointment appointment, VEvent properties)
    {
        Date lastChange = appointment.getReservation().getLastChanged();
        properties.add(new DtStamp(convertRaplaLocaleToUTC(lastChange).toInstant()));
    }

    /**
     * F&uuml;gt die Wiederholungen des &uuml;bergebenen Appointment-Objekts dem
     * &uuml;bergebenen Event-Objekt hinzu.
     *
     * @param appointment Ein Rapla Appointment.
     */
    private void addRepeatings(Appointment appointment, VEvent properties)
    {
        Repeating repeating = appointment.getRepeating();

        if (repeating == null)
        {
            return;
        }

        Frequency frequency;
        switch (repeating.getType())
        {
            case DAILY:
                frequency = Frequency.DAILY;
                break;
            case WEEKLY:
                frequency = Frequency.WEEKLY;
                break;
            case MONTHLY:
                frequency = Frequency.MONTHLY;
                break;
            case YEARLY:
                frequency = Frequency.YEARLY;
                break;
            default:
                getLogger().warn("Invalid data in recurrency rule!");
                return;
        }

        Recur recur;

        // here is evaluated, if a COUNT is set in Rapla, or an enddate is
        // specified
        if (repeating.getNumber() == -1)
        {
            recur = new Recur(frequency );
        }
        else if (repeating.isFixedNumber())
        {
            recur = new Recur(frequency, repeating.getNumber());
        }
        else
        {
            LocalDate endDate = repeating.getEndDateTime().toLocalDate();
            // TODO do we need to translate the enddate in utc?
            recur = new Recur(frequency, endDate);
        }

        if (repeating.isDaily())
        {
            // DAYLY -> settings : intervall
            recur.setInterval(repeating.getInterval());

        }
        else if (repeating.isWeekly())
        {
            // WEEKLY -> settings : every nTh Weekday
            recur.setInterval(repeating.getInterval());
            final int weekday = DateTools.getWeekday(appointment.getStart());
            recur.getDayList().add(WeekDay.getDay(weekday));
        }
        else if (repeating.isMonthly())
        {
            // MONTHLY -> settings : every nTh Weekday
            recur.setInterval(repeating.getInterval());
            final int weekday = DateTools.getWeekday(appointment.getStart());
            int weekofmonth = Math.round(DateTools.getDayOfMonth(appointment.getStart()) / DateTools.DAYS_PER_WEEK);
            recur.getDayList().add(new WeekDay(WeekDay.getDay(weekday), weekofmonth));
        }
        else if (repeating.isYearly())
        {
            recur.getYearDayList().add(DateTools.getDayInYear(appointment.getStart()));
        }
        else
        {
            getLogger().warn("Invalid data in recurrency rule!");
        }

        properties.add(new RRule(recur));

        // bugfix - if rapla has no exceptions, an empty EXDATE: element is
        // produced. This may bother some iCal tools
        if (repeating.getExceptions().length == 0)
        {
            return;
        }

        // Add exception dates
        //DateList dl = new DateList(Value.DATE);



        // rku: use seperate EXDATE for each exception
        List<ZonedDateTime> exceptionDates = new ArrayList<>();
        for (Iterator<Date> itExceptions = Arrays.asList(repeating.getExceptions()).iterator(); itExceptions.hasNext(); )
        {
            Date date = itExceptions.next();
            LocalDateTime startDateTime = appointment.getStartDateTime();
            LocalDateTime localDateTime = DateTools.toLocalDateTime(date);
            LocalDateTime exceptionDate = LocalDateTime.of(localDateTime.toLocalDate(),startDateTime.toLocalTime());
            ZonedDateTime dateTime = exceptionDate.atZone(timeZone.toZoneId());
            exceptionDates.add( dateTime );
        }

        ZonedDateTime[] array = exceptionDates.toArray(new ZonedDateTime[0]);
        DateList<ZonedDateTime> zonedDateTimeDateList = new DateList<>(registry, array);
        ExDate<ZonedDateTime> exDate = new ExDate<>(zonedDateTimeDateList);
        exDate.add(new TzId(timeZone.toZoneId().getId()));
        properties.add(exDate);
        //properties.add(new ExDate(dl));
    }

    public String getAttendeeString(Appointment appointment)
    {

        String attendeeString = "";

        Reservation raplaReservation = appointment.getReservation();
        Allocatable[] raplaPersons = raplaReservation.getPersons();

        for (int i = 0; i < raplaPersons.length; i++)
        {
            if (!isReserved(raplaPersons[i], appointment))
                continue;

            attendeeString += raplaPersons[i].getName(locale);
            attendeeString = attendeeString.trim();

            if (i != raplaPersons.length - 1)
            {
                attendeeString += ", ";
            }
            else
            {
                attendeeString = " [" + attendeeString + "]";
            }
        }

        return attendeeString;
    }

    /**
     * F&uuml;gt die Kategorien hinzu.
     *
     * @param appointment Ein Rapla Appointment.
     */
    private void addCategories(Appointment appointment, VEvent properties)
    {
        Classification cls = appointment.getReservation().getClassification();
        Categories cat = new Categories(cls.getType().getName(locale));
        properties.add(cat);
    }

    /**
     * Pr&uuml;fe, ob eine Ressource f&uuml;r ein bestimmten Appointment
     * reserviert ist.
     *
     * @param alloc
     * @param when
     * @return <code>true</code>, wenn die Ressource reserviert ist.
     *         <code>false</code> sonst
     */
    private boolean isReserved(Allocatable alloc, Appointment when)
    {
        Reservation reservation = when.getReservation();
        Appointment[] restrictions = reservation.getRestriction(alloc);

        for (int restIt = 0, restLen = restrictions.length; restIt < restLen; restIt++)
        {
            if (when.equals(restrictions[restIt]))
                return true;
        }

        return (restrictions.length == 0);
    }

    /**
     * F&uuml;gt einem iCal-Event den Ort aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     */
    private void addLocationToEvent(Appointment appointment, VEvent properties, User user)
    {
        final StringBuffer buffer = new StringBuffer();
        appointment
                .getReservation()
                .getAllocatablesFor(appointment)
                .filter(this::isLocation).
                map(alloc -> getResourceName(alloc, user))
                .filter(this::notEmpty)
                .forEach(resourceName ->
                {
            if (buffer.length() > 0)
            {
                buffer.append(", ");
            }
            buffer.append(resourceName);
        });
        properties.add(new Location(buffer.toString()));
    }

    private boolean isLocation(Allocatable alloc)
    {
        if (hasLocationType)
        {
            return alloc.getClassification().getType().getAnnotation(DynamicTypeAnnotations.KEY_LOCATION) != null;
        }
        else return !alloc.isPerson();
    }

    /**
     * F&uuml;gt einem iCal-Event die Termin-ID aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     */
    private void addUidToEvent(Appointment appointment, VEvent properties)
    {
        // multiple vevents can have the same id
        String uid = getId(appointment);// + getId(appointment);
        properties.add(new Uid(uid));
    }

    /**
     * Erzeugt eine eindeutige ID f&uuml;r den &uuml;bergebenen Appointment.
     *
     * @param entity
     * @return
     */
    private String getId(Entity entity)
    {
        String id = entity.getId();
        return id;
    }

    /**
     * F&uuml;gt einem iCal-Event den Termin-Namen aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     */
    private void addEventNameToEvent(Appointment appointment, VEvent properties, boolean canRead)
    {
        Reservation reservation = appointment.getReservation();
        final Locale locale = this.locale;
        String eventDescription = NameFormatUtil.getExportName(appointment, locale);

        if (!canRead)
        {
            eventDescription = i18n.getString("not_visible");
        }
        else
        {
            eventDescription = NameFormatUtil.getExportName(appointment, locale);
            // add Attendees if not otherwise configured
            if (reservation.getClassification().getType().getAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT) == null)
            {
                eventDescription += getAttendeeString(appointment);
            }
        }

        properties.add(new Summary(eventDescription));
    }

    private void addDescriptionToEvent(Appointment appointment, VEvent properties)
    {

        Reservation reservation = appointment.getReservation();
        String eventDescription;
        if (reservation.getClassification().getType().getAnnotation(DynamicTypeAnnotations.KEY_DESCRIPTION_FORMAT_EXPORT) != null)
        {
            eventDescription = reservation.formatAppointment(locale, DynamicTypeAnnotations.KEY_DESCRIPTION_FORMAT_EXPORT, appointment);
        }
        else
        {
            eventDescription = null;
        }
        if (eventDescription != null)
        {
            properties.add(new Description(eventDescription));
        }
    }

    /**
     * F&uuml;gt einem iCal-Event das Enddatum aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     */
    private void addEndDateToEvent(Appointment appointment, VEvent properties, boolean isAllDayEvent)
    {

        Date endDate = appointment.getEnd();
        if (isAllDayEvent)
        {
            DtEnd end = getDtEndFromAllDayEvent(endDate);
            properties.add(end);
        }
        else
        {
            if (appointment.getRepeating() == null)
            {
                DtEnd end = new DtEnd(convertRaplaLocaleToUTC(endDate));
                properties.add(end);
            }
            else
            {
                DtEnd end = getEndDateProperty(endDate);
                properties.add(end);
            }
        }
    }

    private DtEnd getEndDateProperty(Date endDate)
    {

        ZonedDateTime date = convertRaplaLocaleToUTC(endDate);
        return new DtEnd(date);
    }

    private DtEnd getDtEndFromAllDayEvent(Date endDate)
    {
        LocalDateTime date = DateTools.toLocalDateTime(endDate);

        DtEnd end = new DtEnd(date.toLocalDate());
        return end;
    }

    /**
     * F&uuml;gt einem iCal-Event das Startdatum aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     */
    private void addStartDateToEvent(Appointment appointment, VEvent properties, boolean isAllDayEvent)
    {

        Date startDate = appointment.getStart();

        if (isAllDayEvent)
        {
            DtStart start = getDtStartFromAllDayEvent(startDate);
            properties.add(start);
        }
        else
        {
            if (appointment.getRepeating() == null)
            {
                DtStart<Instant> start = new DtStart(convertRaplaLocaleToUTC(startDate));
                properties.add(start);
            }
            else
            {
                DtStart start = getStartDateProperty(startDate);
                properties.add(start);
            }
        }
    }

    private DtStart getStartDateProperty(Date startDate)
    {

        ZonedDateTime date = convertRaplaLocaleToUTC(startDate);
        return new DtStart(date);
    }

    private DtStart getDtStartFromAllDayEvent(Date startDate)
    {
        LocalDateTime localDateTime = DateTools.toLocalDateTime(startDate);
        DtStart start = new DtStart(localDateTime.atZone(timeZone.toZoneId()).toInstant());
        return start;
    }

    /**
     * F&uuml;gt einem iCal-Event das Erstelldatum aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     */
    private void addCreateDateToEvent(Appointment appointment, VEvent properties)
    {

        Date createTime = appointment.getReservation().getCreateDate();
        properties.add(new Created(convertRaplaLocaleToUTC(createTime).toInstant()));
    }

    private ZonedDateTime convertRaplaLocaleToUTC(Date date)
    {
        LocalDateTime localDateTime = DateTools.toLocalDateTime(date);
        ZonedDateTime instant = localDateTime.atZone(timeZone.toZoneId());
        return instant;
    }
}
