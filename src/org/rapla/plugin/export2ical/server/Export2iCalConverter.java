package org.rapla.plugin.export2ical.server;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.WeekDay;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.CompatibilityHints;

import org.rapla.components.util.DateTools;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.server.TimeZoneConverter;

public class Export2iCalConverter extends RaplaComponent {

    public boolean attendeeToTitle = true;
    net.fortuna.ical4j.model.TimeZone timeZone;
    private java.util.Calendar calendar;
    private String exportAttendeesAttribute;
    private String exportAttendeesParticipationStatus;
    private boolean doExportAsMeeting;
    TimeZoneConverter timezoneConverter;

    public Export2iCalConverter(RaplaContext context, TimeZone zone, Preferences preferences, Configuration config) throws RaplaContextException {
        super(context);
        timezoneConverter = context.lookup( TimeZoneConverter.class);
        calendar = context.lookup(RaplaLocale.class).createCalendar();
        doExportAsMeeting = false;
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, true);
        if (config != null)
        {
            boolean global_export_attendees = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES).getValueAsBoolean(Export2iCalPlugin.DEFAULT_exportAttendees);
            String global_export_attendees_participation_status = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS).getValue(Export2iCalPlugin.DEFAULT_attendee_participation_status);

            doExportAsMeeting = preferences == null ? global_export_attendees : preferences.getEntryAsBoolean(Export2iCalPlugin.EXPORT_ATTENDEES_PREFERENCE, global_export_attendees);
            exportAttendeesParticipationStatus = preferences == null ? global_export_attendees_participation_status : preferences.getEntryAsString(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE, global_export_attendees_participation_status);

            try {
                exportAttendeesAttribute = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_EMAIL_ATTRIBUTE).getValue();
            } catch (ConfigurationException e) {
                exportAttendeesAttribute = "";
                getLogger().info("ExportAttendeesMailAttribute is not set. So do not export as meeting");
            }
            //ensure the stored value is not empty string, if so, do not export attendees
            doExportAsMeeting = doExportAsMeeting && (exportAttendeesAttribute != null && exportAttendeesAttribute.trim().length() > 0);
        }

        if (zone != null) {
            final String timezoneId = zone.getID();

            try {
                TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
                timeZone = registry.getTimeZone(timezoneId);
            } catch (Exception rc) {
                final VTimeZone vTimeZone = new VTimeZone();
                timeZone = new net.fortuna.ical4j.model.TimeZone(vTimeZone);
                final int rawOffset = zone.getRawOffset();
                timeZone.setRawOffset(rawOffset);
            }
        }
    }


    public Calendar createiCalender(Reservation[] reservations) {

        Calendar calendar = initiCalendar();
        addICalMethod(calendar, Method.PUBLISH);
        addVTimeZone(calendar);

        if (reservations.length == 0) {
            return calendar;
        }

        for (int resIt = 0, resLen = reservations.length; resIt < resLen; resIt++) {
            Appointment[] appointments = reservations[resIt].getAppointments();

            for (int appIt = 0, appLen = appointments.length; appIt < appLen; appIt++) {
                calendar.getComponents().add(createVEvent(appointments[appIt]));
            }
        }

        return calendar;
    }

    private void addVTimeZone(Calendar calendar) {

        if (timeZone != null) {
            VTimeZone tz = timeZone.getVTimeZone();
            calendar.getComponents().add(tz);
        }
    }

    /**
     * Initialisiert ein neues leeres iCalendar-Objekt.
     *
     * @return Ein neues leeres iCalendar-Objekt.
     */
    public Calendar initiCalendar() {
        Calendar calendar = new Calendar();
        calendar.getProperties().add(new ProdId("-//Rapla//iCal Plugin//EN"));
        calendar.getProperties().add(Version.VERSION_2_0);
        return calendar;

    }

    public void addICalMethod(Calendar iCalendar, Method method) {
        iCalendar.getProperties().add(method);
    }

    public void addVEvent(Calendar iCalendar, Appointment appointment) {
        iCalendar.getComponents().add(createVEvent(appointment));
    }

    /**
     * Erstellt anhand des &uuml;bergebenen Appointment-Objekts einen
     * iCalendar-Event.
     *
     * @param appointment Ein Rapla Appointment.
     * @return Ein iCalendar-Event mit den Daten des Appointments.
     */
    private VEvent createVEvent(Appointment appointment) {

        PropertyList properties = new PropertyList();


        boolean isAllDayEvent = appointment.isWholeDaysSet() ;
        addDateStampToEvent(appointment, properties);
        addCreateDateToEvent(appointment, properties);
        addStartDateToEvent(appointment, properties, isAllDayEvent);
        addLastModifiedDateToEvent(appointment, properties);
        addEndDateToEvent(appointment, properties, isAllDayEvent);
        addEventNameToEvent(appointment, properties);
        addUidToEvent(appointment, properties);
        addLocationToEvent(appointment, properties);
        addCategories(appointment, properties);
        addOrganizer(appointment, properties);
        addAttendees(appointment, properties);
        addRepeatings(appointment, properties);


        VEvent event = new VEvent(properties);

        return event;
    }

    /**
     * add organizer to properties
     *
     * @param appointment
     * @param properties
     */
    private void addOrganizer(Appointment appointment, PropertyList properties) {
        // means we do not export attendees so we do not have a meeting
        if (!doExportAsMeeting)
           return;
        final User owner = appointment.getReservation().getOwner();
        try {
            Organizer organizer = null;
            if (owner.getEmail() != null && owner.getEmail().trim().length() > 0) {
                try {
                    final URI uri = new URI("MAILTO:" + owner.getEmail().trim());
                    organizer = new Organizer(uri);
                } catch (URISyntaxException e) {
                }
            }
            if (organizer == null) {
                organizer = new Organizer("MAILTO:" + URLEncoder.encode(owner.getUsername(), "UTF-8"));
            }
            if (!"".equals(owner.getName()))
                organizer.getParameters().add(new Cn(owner.getName()));
            properties.add(organizer);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }


    /**
     * add attenddees if system property ical4j.validation.relaxed is set ony
     *
     * @param appointment
     * @param properties
     */
    private void addAttendees(Appointment appointment, PropertyList properties) {
        if (!doExportAsMeeting)
            return;

        Allocatable[] persons = appointment.getReservation().getAllocatablesFor(appointment);

        for (Allocatable person : persons) {
        	if ( !person.isPerson() )
        	{
        		continue;
        	}
            String email = null;
            Attribute attr = person.getClassification().getAttribute(exportAttendeesAttribute);
            if (attr != null && person.getClassification().getValue(attr) != null)
                email = person.getClassification().getValue(attr).toString().trim();
            // determine if person has email attribute
            if (email != null && email.length() > 0) {
                try {
                    Attendee attendee = new Attendee(new URI(email));
                    attendee.getParameters().add(Role.REQ_PARTICIPANT);
                    attendee.getParameters().add(new Cn(person.getName(Locale.getDefault())));
                    attendee.getParameters().add(new PartStat(exportAttendeesParticipationStatus));
                    properties.add(attendee);
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
    }


    /**
     * Fuegt dem Termin das Modifizierungsdatum hinzu
     *
     * @param appointment
     * @param properties
     */
    private void addDateStampToEvent(Appointment appointment, PropertyList properties) {
        Date lastChange = appointment.getReservation().getLastChangeTime();
        properties.add(new LastModified(convertRaplaLocaleToUTC(lastChange)));
    }

    /**
     * Fuegt dem Termin den DTSTAMP hinzu lt. rfc muss dies dem Erstellungdatum
     * des Objektes entsprechen. Da dies jedoch den Import erschwert und sinnlos
     * ist, wird es auf das letzte Modifizierungsdatum geschrieben.
     *
     * @param appointment
     * @param properties
     */
    private void addLastModifiedDateToEvent(Appointment appointment, PropertyList properties) {
        Date lastChange = appointment.getReservation().getLastChangeTime();
        properties.add(new DtStamp(convertRaplaLocaleToUTC(lastChange)));
    }

    /**
     * F&uuml;gt die Wiederholungen des &uuml;bergebenen Appointment-Objekts dem
     * &uuml;bergebenen Event-Objekt hinzu.
     *
     * @param appointment Ein Rapla Appointment.
     * @param properties       Ein iCalendar Event.
     */
    private void addRepeatings(Appointment appointment, PropertyList properties) {
        Repeating repeating = appointment.getRepeating();

        if (repeating == null) {
            return;
        }

        // This returns the strings DAYLY, WEEKLY, MONTHLY, YEARLY
        String type = repeating.getType().toString().toUpperCase();

        Recur recur;

        // here is evaluated, if a COUNT is set in Rapla, or an enddate is
        // specified
        if (repeating.getNumber() == -1) {
            recur = new Recur(type, -1);
        } else if (repeating.isFixedNumber()) {
            recur = new Recur(type, repeating.getNumber());
        } else {
            net.fortuna.ical4j.model.Date endDate = new net.fortuna.ical4j.model.Date(repeating.getEnd());
            // TODO do we need to translate the enddate in utc?
            recur = new Recur(type, endDate);
        }

        if (repeating.isDaily()) {
            // DAYLY -> settings : intervall
            recur.setInterval(repeating.getInterval());

        } else if (repeating.isWeekly()) {
            // WEEKLY -> settings : every nTh Weekday
            recur.setInterval(repeating.getInterval());

            calendar.setTime(appointment.getStart());
            recur.getDayList().add(WeekDay.getWeekDay(calendar));
        } else if (repeating.isMonthly()) {
            // MONTHLY -> settings : every nTh Weekday
            recur.setInterval(repeating.getInterval());
            calendar.setTime(appointment.getStart());
            int weekofmonth = Math.round(calendar.get(java.util.Calendar.DAY_OF_MONTH) / DateTools.DAYS_PER_WEEK) + 1;
            recur.getDayList().add(new WeekDay(WeekDay.getWeekDay(calendar), weekofmonth));
        } else if (repeating.isYearly()) {
            // YEARLY -> settings : every nTh day mTh Monthname
            calendar.setTime(appointment.getStart());
            calendar.get(java.util.Calendar.DAY_OF_YEAR);
        } else {
            getLogger().warn("Invalid data in recurrency rule!");
        }

        properties.add(new RRule(recur));

        // bugfix - if rapla has no exceptions, an empty EXDATE: element is
        // produced. This may bother some iCal tools
        if (repeating.getExceptions().length == 0) {
            return;
        }

        // Add exception dates
        //DateList dl = new DateList(Value.DATE);

        ExDate exDate = new ExDate();
        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        net.fortuna.ical4j.model.TimeZone tz = registry.getTimeZone(timeZone.getID());
        
        // rku: use seperate EXDATE for each exception
        for (Iterator<Date> itExceptions = Arrays.asList(repeating.getExceptions()).iterator(); itExceptions.hasNext(); ) {
            //DateList dl = new DateList(Value.DATE);
            Date date = itExceptions.next();
			//dl.add(new net.fortuna.ical4j.model.Date( date));
            java.util.Calendar cal = getRaplaLocale().createCalendar();
            cal.setTime( date );
            int year = cal.get( java.util.Calendar.YEAR );
            int day_of_year = cal.get( java.util.Calendar.DAY_OF_YEAR);
            cal.setTime( appointment.getStart());
            cal.set(java.util.Calendar.YEAR, year);
            cal.set(java.util.Calendar.DAY_OF_YEAR, day_of_year);
            int offset =(int) (tz.getOffset( DateTools.cutDate( date).getTime())/ DateTools.MILLISECONDS_PER_HOUR);
            cal.add(java.util.Calendar.HOUR, -offset);
            Date dateToSave = cal.getTime();
            net.fortuna.ical4j.model.DateTime dateTime = new net.fortuna.ical4j.model.DateTime();
            dateTime.setTime( dateToSave.getTime());
			exDate.getDates().add( dateTime);
        }
        exDate.setTimeZone(tz );
        
        properties.add(exDate);
        //properties.add(new ExDate(dl));
    }

    public String getAttendeeString(Appointment appointment) {

        String attendeeString = "";

        Reservation raplaReservation = appointment.getReservation();
        Allocatable[] raplaPersons = raplaReservation.getPersons();

        for (int i = 0; i < raplaPersons.length; i++) {
            if (!isReserved(raplaPersons[i], appointment))
                continue;

            attendeeString += raplaPersons[i].getName(Locale.getDefault());
            attendeeString = attendeeString.trim();

            if (i != raplaPersons.length - 1) {
                attendeeString += ", ";
            } else {
                attendeeString = " [" + attendeeString + "]";
            }
        }

        return attendeeString;
    }

    /**
     * F&uuml;gt die Kategorien hinzu.
     *
     * @param appointment Ein Rapla Appointment.
     * @param properties       Ein iCalendar Event.
     */
    private void addCategories(Appointment appointment, PropertyList properties) {
        Classification cls = appointment.getReservation().getClassification();
        Categories cat = new Categories();
        cat.getCategories().add(cls.getType().getName(Locale.getDefault()));
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
    private boolean isReserved(Allocatable alloc, Appointment when) {
        Reservation reservation = when.getReservation();
        Appointment[] restrictions = reservation.getRestriction(alloc);

        for (int restIt = 0, restLen = restrictions.length; restIt < restLen; restIt++) {
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
     * @param properties
     */
    private void addLocationToEvent(Appointment appointment, PropertyList properties) {
        Allocatable[] allocatables = appointment.getReservation().getAllocatablesFor(appointment);

        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < allocatables.length; i++) {
            if (allocatables[i].isPerson())
                continue;

            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(allocatables[i].getName(Locale.getDefault()));
        }

        properties.add(new Location(buffer.toString()));
    }

    /**
     * F&uuml;gt einem iCal-Event die Termin-ID aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     * @param properties
     */
    private void addUidToEvent(Appointment appointment, PropertyList properties) {
        // multiple vevents can have the same id
        String uid = getId(appointment);
/*
        try {
            uid += "@"+getContainer().getStartupEnvironment().getConfigURL().getHost();
        } catch (RaplaException e) {
            getLogger().error(e.getMessage(), e);
        }
*/
        //String uid = getId(appointment.getReservation());// + getId(appointment);
        properties.add(new Uid(uid));
    }

    /**
     * Erzeugt eine eindeutige ID f&uuml;r den &uuml;bergebenen Appointment.
     *
     * @param entity
     * @return
     */
    private String getId(RaplaObject entity) {
        SimpleIdentifier id = (SimpleIdentifier) ((RefEntity<?>) entity).getId();
        return entity.getRaplaType().getLocalName() + "_" + id.getKey();
    }

    /**
     * F&uuml;gt einem iCal-Event den Termin-Namen aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     * @param properties
     */
    private void addEventNameToEvent(Appointment appointment, PropertyList properties) {

        String eventDescription = appointment.getReservation().getName(Locale.getDefault());
        if (attendeeToTitle) {
            eventDescription += getAttendeeString(appointment);
        }
        properties.add(new Summary(eventDescription));
    }

    /**
     * F&uuml;gt einem iCal-Event das Enddatum aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     * @param properties
     */
    private void addEndDateToEvent(Appointment appointment, PropertyList properties, boolean isAllDayEvent) {

        Date endDate = appointment.getEnd();
        java.util.Calendar calendar = java.util.Calendar.getInstance();

        if (isAllDayEvent) {
            DtEnd end = getDtEndFromAllDayEvent(endDate, calendar);
            properties.add(end);
        } else {
            if (appointment.getRepeating() == null) {
                DtEnd end = new DtEnd(convertRaplaLocaleToUTC(endDate));
                end.setUtc(true);
                properties.add(end);
            } else {
                DtEnd end = getEndDateProperty( endDate);
                properties.add(end);
            }
        }
    }

    private DtEnd getEndDateProperty( Date endDate) {

        DateTime date = convertRaplaLocaleToUTC(endDate);
        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        net.fortuna.ical4j.model.TimeZone tz = registry.getTimeZone(timeZone.getID());
        date.setTimeZone(tz);
        return new DtEnd(date);
    }

    private DtEnd getDtEndFromAllDayEvent(Date endDate, java.util.Calendar calendar) {

        calendar.clear();
        calendar.setTime(endDate);
        int year = calendar.get(java.util.Calendar.YEAR);
		calendar.add(java.util.Calendar.DATE, 1);
        int month = calendar.get(java.util.Calendar.MONTH);
        int date = calendar.get(java.util.Calendar.DAY_OF_MONTH);
        calendar.clear();
        calendar.set(year, month, date);
        DtEnd end = new DtEnd(new net.fortuna.ical4j.model.Date(calendar.getTime()));
        return end;
    }

    /**
     * F&uuml;gt einem iCal-Event das Startdatum aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     * @param properties
     */
    private void addStartDateToEvent(Appointment appointment, PropertyList properties, boolean isAllDayEvent) {

        Date startDate = appointment.getStart();

        if (isAllDayEvent) {
            DtStart start = getDtStartFromAllDayEvent(startDate, calendar);
            properties.add(start);
        } else {
            if (appointment.getRepeating() == null) {
                DtStart start = new DtStart(convertRaplaLocaleToUTC(startDate));
                start.setUtc(true);
                properties.add(start);
            } else {
                DtStart start = getStartDateProperty(startDate);
                properties.add(start);
            }
        }
    }

    private DtStart getStartDateProperty( Date startDate) {

        DateTime date = convertRaplaLocaleToUTC(startDate);
        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        net.fortuna.ical4j.model.TimeZone tz = registry.getTimeZone(timeZone.getID());
        date.setTimeZone(tz);
        return new DtStart(date);
    }

    private DtStart getDtStartFromAllDayEvent(Date startDate, java.util.Calendar calendar) {

        calendar.clear();
        calendar.setTime(startDate);
        int year = calendar.get(java.util.Calendar.YEAR);
        int month = calendar.get(java.util.Calendar.MONTH);
        int date = calendar.get(java.util.Calendar.DAY_OF_MONTH);
        calendar.clear();
        calendar.set(year, month, date);
        DtStart start = new DtStart(new net.fortuna.ical4j.model.Date(calendar.getTime()));
        return start;
    }

    /**
     * F&uuml;gt einem iCal-Event das Erstelldatum aus dem &uuml;bergebenen
     * Appointment-Objekt hinzu.
     *
     * @param appointment
     * @param properties
     */
    private void addCreateDateToEvent(Appointment appointment, PropertyList properties) {

        Date createTime = appointment.getReservation().getCreateTime();
        properties.add(new Created(convertRaplaLocaleToUTC(createTime)));
    }

    private DateTime convertRaplaLocaleToUTC(Date date) {
        Date converted = timezoneConverter.fromRaplaTime(timeZone, date);
        DateTime result = new DateTime(converted.getTime());
        return result;
    }
}