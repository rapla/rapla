package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import net.fortuna.ical4j.model.Month;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.WeekDay;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TimeZoneConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.ical.ICalImport;
import org.rapla.server.RemoteSession;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@RestController
@ConditionalOnBean(RemoteSession.class)
@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.ical", matchIfMissing = true)
public class ICalImportController implements ICalImport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ICalImportController.class);
    private final TimeZoneConverter timeZoneConverter;
    private final RemoteSession session;
    private final RaplaFacade facade;
    private final org.rapla.storage.SyncStorageOperator syncOperator;
    private final HttpServletRequest request;

    public ICalImportController(TimeZoneConverter timeZoneConverter,
                                 RemoteSession session,
                                 RaplaFacade facade,
                                 org.rapla.storage.SyncStorageOperator syncOperator,
                                 HttpServletRequest request)
    {
        this.timeZoneConverter = timeZoneConverter;
        this.session = session;
        this.facade = facade;
        this.syncOperator = syncOperator;
        this.request = request;
    }

    @Override
    public Integer[] importICal(Import job) throws RaplaException
    {
        String content = job.getContent();
        boolean isURL = job.isURL();
        String[] allocatableIds = job.getAllocatableIds();
        String eventTypeKey = job.getEventTypeKey();
        String eventTypeNameAttributeKey = job.getEventTypeNameAttributeKey();
        List<Allocatable> allocatables = new ArrayList<>();
        for (String id : allocatableIds)
        {
            allocatables.add(getAllocatable(id));
        }
        User user = session.checkAndGetUser(request);
        return importCalendar(content, isURL, allocatables, user, eventTypeKey, eventTypeNameAttributeKey);
    }

    private Allocatable getAllocatable(final String id) throws EntityNotFoundException
    {
        AbstractCachableOperator operator = (AbstractCachableOperator) facade.getOperator();
        return operator.resolve(id, Allocatable.class);
    }

    /**
     * Parses iCal calendar and creates reservations for the contained VEVENTS.
     *
     * <p><b>NOTE:</b> the main VEVENT-iteration loop is currently commented out
     * (lines below) — it was disabled during the ical4j 4.x migration and not
     * yet restored. The method still runs the import-deduplication pipeline
     * over an empty event list, so it preserves the wire shape (returns four
     * integers) but never actually imports anything. See PRD 042 for the
     * iCal-import follow-up.
     */
    public Integer[] importCalendar(String content, boolean isURL, List<Allocatable> resources, User user,
                                     String eventTypeKey, String eventTypeNameAttributeKey) throws RaplaException
    {
        final TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();
        // CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_NOTES_COMPATIBILITY, true);
        // CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
        // CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
        // CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
        // CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, true);
        // CalendarBuilder builder = new CalendarBuilder();
        // Calendar calendar;
        // try {
        //     calendar = isURL
        //         ? builder.build(new URL(content).openStream())
        //         : builder.build(new StringReader(content));
        // } catch (IOException | ParserException | IllegalArgumentException ex) {
        //     throw new RaplaException(ex.getMessage());
        // }
        // List<CalendarComponent> events = calendar.getComponents();
        // Map<String, Reservation> reservationMap = new HashMap<>();
        // for (CalendarComponent component : events) {
        //     if (component.getName().equalsIgnoreCase("VEVENT")) {
        //         try {
        //             eventsInICal++;
        //             ... full VEVENT → Reservation logic ...
        //         } catch (ParseException ex) { /* swallow */ }
        //     }
        // }

        LocalDateTime minStart = null;
        final int eventsInICalFinal = 0;
        int eventsSkippedFinal = 0;
        List<Reservation> eventList = new ArrayList<>();

        Map<String, List<Entity<Reservation>>> imported = getImportedReservations(minStart);
        int eventsPresent = 0;
        int eventsImported = 0;
        List<Reservation> toImport = new ArrayList<>();
        for (Reservation reservation : eventList)
        {
            String uid = reservation.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
            if (uid == null)
            {
                eventsImported++;
                toImport.add(reservation);
            }
            else
            {
                List<Entity<Reservation>> alreadyImported = imported.get(uid);
                if (alreadyImported == null || alreadyImported.isEmpty())
                {
                    eventsImported++;
                    toImport.add(reservation);
                }
                else
                {
                    LOGGER.debug("Ignoring event with uid {} already imported. Ignoring", uid);
                    eventsPresent++;
                }
            }
        }

        facade.storeObjects(toImport.toArray(Reservation.RESERVATION_ARRAY));
        return new Integer[] { eventsInICalFinal, eventsImported, eventsPresent, eventsSkippedFinal };
    }

    private Map<String, List<Entity<Reservation>>> getImportedReservations(LocalDateTime start) throws RaplaException
    {
        User user = null;
        LocalDateTime end = null;
        Map<String, List<Entity<Reservation>>> keyMap = new LinkedHashMap<>();
        Collection<Reservation> reservations = syncOperator.getReservationsSync(user, null, null, start, end, null);
        for (Reservation r : reservations)
        {
            String key = r.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
            if (key != null)
            {
                keyMap.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
        }
        return keyMap;
    }

    private Appointment newAppointment(User user, LocalDateTime begin, LocalDateTime end) throws RaplaException
    {
        return facade.newAppointmentWithUser(begin, end, user);
    }

    /**
     * If the recurrences can be matched to one or more appointments, return the
     * appointment list — otherwise return null (caller falls back to expanding
     * the RRULE into individual single-block appointments).
     */
    private List<Appointment> calcRepeating(List<Recur> recurList, Appointment start) throws RaplaException
    {
        final TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();
        final List<Appointment> appointments = new ArrayList<>();
        for (Recur recur : recurList)
        {
            // TODO UTC mapping
            ReferenceInfo<User> ownerId = start.getOwnerRef();
            User owner = facade.tryResolve(ownerId);
            final Appointment appointment = facade.newAppointmentWithUser(start.getStart(), start.getEnd(), owner);
            appointment.setRepeatingEnabled(true);
            List<WeekDay> dayList = recur.getDayList();
            if (dayList.size() > 1 || (dayList.size() == 1 && !recur.getFrequency().equals(Recur.WEEKLY)))
            {
                return null;
            }
            if (!recur.getYearDayList().isEmpty()) return null;
            if (!recur.getWeekNoList().isEmpty()) return null;
            if (!recur.getHourList().isEmpty()) return null;
            List<Month> monthList = recur.getMonthList();
            if (monthList.size() > 1 || (monthList.size() == 1 && !recur.getFrequency().equals(Recur.MONTHLY)))
            {
                return null;
            }
            if (recur.getFrequency().equals(Recur.DAILY))
                appointment.getRepeating().setType(RepeatingType.DAILY);
            else if (recur.getFrequency().equals(Recur.MONTHLY))
                appointment.getRepeating().setType(RepeatingType.MONTHLY);
            else if (recur.getFrequency().equals(Recur.YEARLY))
                appointment.getRepeating().setType(RepeatingType.YEARLY);
            else if (recur.getFrequency().equals(Recur.WEEKLY))
                appointment.getRepeating().setType(RepeatingType.WEEKLY);
            else throw new IllegalStateException("Repeating type " + recur.getFrequency() + " not (yet) supported");

            int count = recur.getCount();
            appointment.getRepeating().setNumber(count);
            if (count <= 0)
            {
                Temporal until = recur.getUntil();
                LocalDateTime repeatingEnd;
                if (until == null)
                {
                    repeatingEnd = null;
                }
                else
                {
                    throw new UnsupportedOperationException("Repeating end time not supported");
                }
                appointment.getRepeating().setEnd(repeatingEnd);
            }
            appointment.getRepeating().setInterval(recur.getInterval());
            appointments.add(appointment);
        }
        return appointments;
    }
}
