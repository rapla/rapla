package org.rapla.plugin.ical.server;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.util.CompatibilityHints;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.plugin.ical.ICalImport;
import org.rapla.scheduler.Promise;
import org.rapla.server.PromiseWait;
import org.rapla.server.RemoteSession;
import org.rapla.server.TimeZoneConverter;
import org.rapla.storage.impl.AbstractCachableOperator;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@DefaultImplementation(context=InjectionContext.server, of=ICalImport.class)
public class RaplaICalImport implements ICalImport {
	@Inject
	TimeZoneConverter timeZoneConverter;
	@Inject
	RemoteSession session;
	@Inject
	RaplaFacade facade;
	@Inject
	Logger logger;
    @Inject
    PromiseWait promiseWait;

    private final HttpServletRequest request;


    @Inject
	public RaplaICalImport(@Context HttpServletRequest request ) {
        this.request = request;
	}
	public RaplaICalImport(TimeZoneConverter timeZoneConverter, RemoteSession session, RaplaFacade facade, Logger logger, HttpServletRequest request)
    {
        this.timeZoneConverter = timeZoneConverter;
        this.session = session;
        this.facade = facade;
        this.logger = logger;
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
        for (String id : allocatableIds) {
            Allocatable allocatable = getAllocatable(id);
            allocatables.add(allocatable);
        }
        User user = session.checkAndGetUser(request);
            final Promise<Integer[]> count = importCalendar(content, isURL, allocatables, user, eventTypeKey, eventTypeNameAttributeKey);
            return promiseWait.waitForWithRaplaException( count, 10000);

	}
	private Allocatable getAllocatable( final String id)  throws EntityNotFoundException
	{
	    AbstractCachableOperator operator = (AbstractCachableOperator) facade.getOperator();
	    final Allocatable refEntity = operator.resolve( id, Allocatable.class);
	    return refEntity;
	}



	/**
	 * parses iCal calendar and creates reservations for the contained VEVENTS.
	 * SUMMARY will match to the name of the reservation.
	 * can handle events with DTEND or DURATION.
	 * recurrance information from RRULE will be handled by google-rfc-2445.
	 * reads TZID information from given file. If the is none, passedTimezone
	 * will be used.
	 * 
	 * @param content
	 *            path of the *.ics file
	 * @param isURL
	 *            set true if the path is a URL and no local filepath
	 * @param resources
	 *            if you want to add resources to all event in the given
	 *            calendar, pass a list.
	 *            each element of the list has to be a String[] and stands for
	 *            one resource.
	 * 
	 *            <pre>
	 * element[0] contains the name of the dynamic Type for the allocatable
	 * element[1] contains the name of the allocatable
	 * </pre>
	 * @param user 
	 * @param eventTypeKey 
	 * @throws FileNotFoundException
	 * @throws MalformedURLException
	 * @throws RaplaException
	 */
	public Promise<Integer[]> importCalendar(String content, boolean isURL, List<Allocatable> resources, User user, String eventTypeKey, String eventTypeNameAttributeKey) throws RaplaException {
        final TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();

	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_NOTES_COMPATIBILITY, true);
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_RELAXED_PARSING, true);
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_RELAXED_VALIDATION, true);
	    CalendarBuilder builder = new CalendarBuilder();
		Calendar calendar = null;
        int eventsInICal = 0;
		int eventsSkipped = 0;
		try {
    		if (isURL) {
    		    InputStream in = new URL(content).openStream();
    		    calendar = builder.build(in);
    		    in.close();
    		} 
    		else 
    		{
    		    StringReader fin = new StringReader(content);
    		    calendar = builder.build(fin);
    		}
		} catch (IOException ioe) {
		    throw new RaplaException(ioe.getMessage());
		} catch (ParserException pe) {
            throw new RaplaException(pe.getMessage());
        } catch (IllegalArgumentException pe) {
          throw new RaplaException(pe.getMessage());
        }	
		
		ComponentList events = calendar.getComponents();
		@SuppressWarnings("unchecked")
        Iterator<Component> iterator = events.iterator();
		List<Reservation> eventList = new ArrayList<>();
	    Map<String, Reservation> reservationMap = new HashMap<>();
	    while (iterator.hasNext()) {
			Component component = iterator.next();
			if (component.getName().equalsIgnoreCase("VEVENT") ) {
                try {
                	eventsInICal ++;
                	String uid = null;
    				Reservation lookupEvent = null;
    				String name = component.getProperty("SUMMARY").getValue();
    				Property uidProperty = component.getProperty("UID");
    				if ( uidProperty != null)
    				{
    				    uid = uidProperty.getValue();
    				    lookupEvent = reservationMap.get( uid);
    				}
    				if (name == null || name.trim().length() == 0)
    				{
    					logger.debug("Ignoring event with empty name [" + uid + "]");
				    	eventsSkipped ++;
				    	continue;
    				}

                	if ( lookupEvent == null)
    				{
    					// createInfoDialog the reservation
    					Classification classification = facade.getDynamicType(eventTypeKey).newClassification();
    				    lookupEvent = facade.newReservation(classification,user);
    				    if ( uid != null)
    				    {
    				    	lookupEvent.setAnnotation( RaplaObjectAnnotations.KEY_EXTERNALID, uid);
    				    }
    		            classification.setValue(eventTypeNameAttributeKey, name);
    				}
                	Reservation event = lookupEvent;
    			    DateProperty startDateProperty = component.getProperty("DTSTART");
                    Date startdate =  startDateProperty.getDate();
                    Date enddate =  null;
    				boolean wholeDay = false;
    				long duration_millis = 0;
    				Dur duration;
    				if (component.getProperties("DTEND").size() > 0) {
    				    DateProperty endDateProperty = component.getProperty("DTEND");
    				    enddate = endDateProperty.getDate();
    					duration = null;
    				} else if (component.getProperties("DURATION").size() > 0) {
    				    duration = new Dur(component.getProperty("DURATION").getValue());
    			        Date t1 = new Date();
    			        Date t2 = duration.getTime(t1);
    			        duration_millis = t2.getTime() - t1.getTime();
    				} else {
    					logger.warn("Error in ics File. There is an event without DTEND or DURATION. " + "SUMMARY: " + name + ", DTSTART: " + startdate);
    					eventsSkipped ++;
    					continue;
    				}
    
    				
    				
		            for (Allocatable allocatable: resources)
                    {
                        event.addAllocatable(allocatable);
                    }
		            
		            if (duration_millis == 0 && enddate != null) {
                        duration_millis = enddate.getTime() - startdate.getTime();
                    }
		            // createInfoDialog appointment
		            

		            Appointment appointment;
		        	if ( !(startdate instanceof DateTime))
		            {
		            	Date begin = new Date( startdate.getTime());
			            Date end = new Date(begin.getTime() + duration_millis);
			            appointment = newAppointment(user,begin, end);
		            	wholeDay = true;
			            appointment.setWholeDays(wholeDay);
		            }
		            else
		            {
                        Date begin = timeZoneConverter.toRaplaTime(timeZone, startdate);
			            Date end = new Date(begin.getTime() + duration_millis);
			            appointment = newAppointment(user,begin, end);
		            }
		            
		            PropertyList<Property> rrules = component.getProperties("RRULE");
		            if ( rrules.size() >0)
		            {
		                List<Recur> recurList = new ArrayList<>(rrules.size());
                        for ( int i=0;i<rrules.size();i++)
                        {
                            Property prop = rrules.get( i);
                            RRule rrule = new RRule(prop.getParameters(),prop.getValue());
                            recurList.add(rrule.getRecur());
                        }
                        List<Appointment> list = calcRepeating( recurList, appointment);
                        if  ( list != null)
                        {
                            for ( Appointment app: list)
                            {
                                event.addAppointment( app);
                            }
                        }
                        else
                        {
                            net.fortuna.ical4j.model.Date periodEnd = null;
                            for (Recur recur: recurList)
                            {
                                net.fortuna.ical4j.model.Date until = recur.getUntil();
                                if ( until != null)
                                {
                                    if ( periodEnd == null || periodEnd.before( until))
                                    {
                                        periodEnd = until;
                                    }
                                }
                            }
                           
                            if ( periodEnd == null)
                            {
                                periodEnd = new net.fortuna.ical4j.model.Date(startdate.getTime() + DateTools.MILLISECONDS_PER_WEEK * 52 *6);
                            }
                            PeriodList periodList;
                            {
                                long e4 = DateTools.addDays( periodEnd, 1).getTime();
                                long s1 = startdate.getTime();
                                Period period = new Period(new DateTime( s1), new DateTime(e4));
                                periodList = component.calculateRecurrenceSet(period);
                            }
                            for ( @SuppressWarnings("unchecked") Iterator<Period> it = periodList.iterator();it.hasNext();)
                            {
                                Period p = it.next();
                               
								Date s = timeZoneConverter.toRaplaTime(timeZone, p.getStart());
                                Date e = timeZoneConverter.toRaplaTime(timeZone,p.getEnd());
                                Appointment singleAppointment = newAppointment( user,s, e);
                                event.addAppointment( singleAppointment);
                            }
                        }
		            }
		            else
		            {
		                event.addAppointment( appointment);
		            }

		            		            
					/*
					 * get a date iterable for given startdate and recurrance
					 * rule.
					 * timezone passed is UTC, because timezone calculation has
					 * already been taken care of earlier
					 */
		            if (uid  != null)
				    {
				        reservationMap.put( uid, lookupEvent);
				    }
		            eventList.add( lookupEvent);
                }
                catch (ParseException ex)
                {
                    
                }

			} else if (component.getName().equalsIgnoreCase("VTIMEZONE")) {
				if (component.getProperties("TZID").size() > 0) {
				//	TimeZone timezoneFromICal = TimeZone.getTimeZone(component.getProperty("TZID").getValue());
				}
			}
        }
		Date minStart = null;
        final int eventsInICalFinal = eventsInICal;
        int eventsSkippedFinal = eventsSkipped;
		Promise<Map<String, List<Entity<Reservation>>>> importedPromise = getImportedReservations(minStart);
        return importedPromise.thenApply((imported) ->
        {
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
                        logger.debug("Ignoring event with uid " + uid + " already imported. Ignoring");
                        eventsPresent++;
                    }
                }
            }

            facade.storeObjects(toImport.toArray(Reservation.RESERVATION_ARRAY));
            return new Integer[] { eventsInICalFinal, eventsImported, eventsPresent, eventsSkippedFinal };
        });
	}

    protected Promise<Map<String, List<Entity<Reservation>>>> getImportedReservations(Date start)
    {
        Map<String, List<Entity<Reservation>>> keyMap;
        User user = null;
        Date end = null;
        keyMap = new LinkedHashMap<>();
        Promise<Collection<Reservation>> reservationsPromise = facade.getReservations(user, start, end, null);
        return reservationsPromise.thenApply((reservations) ->
        {
            for (Reservation r : reservations)
            {
                String key = r.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
                if (key != null)
                {
                    List<Entity<Reservation>> list = keyMap.get(key);
                    if (list == null)
                    {
                        list = new ArrayList<>();
                        keyMap.put(key, list);
                    }
                    list.add(r);
                }
            }
            return keyMap;
        });
    }

//    
//	private Date toRaplaDate(TimeZone timeZone2,
//			net.fortuna.ical4j.model.Date startdate) 
//	{
//		java.util.Calendar cal = java.util.Calendar.inject( getRaplaLocale().getSystemTimeZone());
//		cal.setTime(startdate);
//		cal.add( java.util.Calendar.HOUR_OF_DAY, 2);
//		int date = cal.get( java.util.Calendar.DATE);
//		int month = cal.get( java.util.Calendar.MONTH);
//		int year = cal.get( java.util.Calendar.YEAR);
//		
//		Date time = cal.getTime();
//		Date convertedDate = getRaplaLocale().toRaplaTime(timeZone2, time);
//		return null;
//	}

	private Appointment newAppointment(User user,Date begin, Date end) throws RaplaException {
        Appointment appointment = facade.newAppointmentWithUser(begin,end,user);
        return appointment;
    }


	/** if the recurances can be matched to one or more appointments return the appointment list else return null*/
	private List<Appointment> calcRepeating(List<Recur> recurList, Appointment start) throws RaplaException {
        final TimeZone timeZone = timeZoneConverter.getImportExportTimeZone();
        final List<Appointment> appointments = new ArrayList<>();
        for (Recur recur : recurList) {
        	// FIXME need to implement UTC mapping
			ReferenceInfo<User> ownerId = start.getOwnerRef();
			User owner = facade.tryResolve(ownerId) ;
            final Appointment appointment = facade.newAppointmentWithUser(start.getStart(), start.getEnd(), owner);
            appointment.setRepeatingEnabled(true);
            WeekDayList dayList = recur.getDayList();
            if (dayList.size() > 1 || (dayList.size() == 1 && !recur.getFrequency().equals(Recur.WEEKLY)  ))
            {
            	return null;
            }
            NumberList yearDayList = recur.getYearDayList();
            if ( yearDayList.size() > 0)
            {
            	return null;
            }
            NumberList weekNoList = recur.getWeekNoList();
            if ( weekNoList.size() > 0){
            	return null;
            }
            NumberList hourList = recur.getHourList();
            if ( hourList.size() > 0){
            	return null;
            }
            MonthList monthList = recur.getMonthList();
			if (monthList.size() > 1 || (monthList.size() == 1 && !recur.getFrequency().equals(Recur.MONTHLY)  ))
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
            else throw new IllegalStateException("Repeating type "+recur.getFrequency()+" not (yet) supported");
            //recur.getCount() == -1 when it never ends
            int count = recur.getCount();
			appointment.getRepeating().setNumber(count);
            if (count <= 0) 
            {
				net.fortuna.ical4j.model.Date until = recur.getUntil();
				Date repeatingEnd;
				if ( until instanceof DateTime)
				{
					repeatingEnd = timeZoneConverter.toRaplaTime( timeZone, until);
				}
				else if ( until != null)
				{
					repeatingEnd = new Date(until.getTime());
				}
				else
				{
					repeatingEnd = null;
				}
				appointment.getRepeating().setEnd(repeatingEnd);

			}
            //interval
            appointment.getRepeating().setInterval(recur.getInterval());
            appointments.add(appointment);

        }
        return appointments;
    }

    
	


}
