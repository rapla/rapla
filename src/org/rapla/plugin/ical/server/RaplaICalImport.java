package org.rapla.plugin.ical.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.NumberList;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.WeekDayList;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.util.CompatibilityHints;

import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.ical.ICalImport;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.ResultImpl;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.server.TimeZoneConverter;
import org.rapla.storage.impl.AbstractCachableOperator;


public class RaplaICalImport extends RaplaComponent implements RemoteMethodFactory<ICalImport> {
	private TimeZone timeZone;
	private TimeZoneConverter timeZoneConverter;

	public RaplaICalImport( RaplaContext context) throws RaplaContextException{
		this( context, context.lookup(TimeZoneConverter.class).getImportExportTimeZone());
	}
	
    public RaplaICalImport( RaplaContext context, TimeZone timeZone) throws RaplaContextException{
	    super( context);
	    this.timeZone = timeZone;
	    this.timeZoneConverter = context.lookup( TimeZoneConverter.class);
    }
	
	public ICalImport createService(final RemoteSession remoteSession) {
        return new ICalImport() {
        	@Override
            public FutureResult<Integer[]> importICal(String content, boolean isURL, String[] allocatableIds, String eventTypeKey, String eventTypeNameAttributeKey)  
            {
                try
                {
                	List<Allocatable> allocatables = new ArrayList<Allocatable>();
                    if ( allocatableIds.length > 0)
                    {
                        for ( String id:allocatableIds)
                        {
                            Allocatable allocatable = getAllocatable(id);
                            allocatables.add ( allocatable);
                        }
                    }
                    User user = remoteSession.getUser();
                    Integer[] count = importCalendar(content, isURL, allocatables, user, eventTypeKey, eventTypeNameAttributeKey);
                    return new ResultImpl<Integer[]>(count);
                } catch (RaplaException ex)
                {
                	return new ResultImpl<Integer[]>(ex);
                }
            }
        };
	}
	    
	private Allocatable getAllocatable( final String id)  throws EntityNotFoundException
	{
	    AbstractCachableOperator operator = (AbstractCachableOperator) getClientFacade().getOperator();
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
	public Integer[] importCalendar(String content, boolean isURL, List<Allocatable> resources, User user, String eventTypeKey, String eventTypeNameAttributeKey) throws RaplaException {
	    
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_NOTES_COMPATIBILITY, true);
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_RELAXED_PARSING, true);
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
	    CompatibilityHints.setHintEnabled( CompatibilityHints.KEY_RELAXED_VALIDATION, true);
	    CalendarBuilder builder = new CalendarBuilder();
		Calendar calendar = null;
		int eventsInICal = 0;
		int eventsImported = 0;
		int eventsPresent = 0;
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
		List<Reservation> eventList = new ArrayList<Reservation>();
	    Map<String, Reservation> reservationMap = new HashMap<String, Reservation>();
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
    					getLogger().debug("Ignoring event with empty name [" + uid +  "]" );
				    	eventsSkipped ++;
				    	continue;
    				}

                	if ( lookupEvent == null)
    				{
    					// create the reservation
    					Classification classification = getClientFacade().getDynamicType(eventTypeKey).newClassification();
    				    lookupEvent = getClientFacade().newReservation(classification,user);
    				    if ( uid != null)
    				    {
    				    	lookupEvent.setAnnotation( RaplaObjectAnnotations.KEY_EXTERNALID, uid);
    				    }
    		            classification.setValue(eventTypeNameAttributeKey, name);
    				}
                	Reservation event = lookupEvent;
    			    DateProperty startDateProperty = (DateProperty)component.getProperty("DTSTART");
                    Date startdate =  startDateProperty.getDate();
                    Date enddate =  null;
    				boolean wholeDay = false;
    				long duration_millis = 0;
    				Dur duration;
    				if (component.getProperties("DTEND").size() > 0) {
    				    DateProperty endDateProperty = (DateProperty)component.getProperty("DTEND");
    				    enddate = endDateProperty.getDate();
    					duration = null;
    				} else if (component.getProperties("DURATION").size() > 0) {
    				    duration = new Dur(component.getProperty("DURATION").getValue());
    			        Date t1 = new Date();
    			        Date t2 = duration.getTime(t1);
    			        duration_millis = t2.getTime() - t1.getTime();
    				} else {
    					getLogger().warn("Error in ics File. There is an event without DTEND or DURATION. "
    							+ "SUMMARY: " + name + ", DTSTART: " + startdate);
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
		            // create appointment
		            

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
		            
		            PropertyList rrules = component.getProperties("RRULE");
		            if ( rrules.size() >0)
		            {
		                List<Recur> recurList = new ArrayList<Recur>(rrules.size());
                        for ( int i=0;i<rrules.size();i++)
                        {
                            Property prop = (Property) rrules.get( i);
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
		Map<String, List<Entity<Reservation>>> imported = getImportedReservations(minStart);
        List<Reservation> toImport = new ArrayList<Reservation>();
		for (Reservation reservation:eventList)
		{
		    String uid = reservation.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
		    if ( uid == null)
		    {
		    	eventsImported++;
		    	toImport.add( reservation);
		    }
		    else
		    {
				List<Entity<Reservation>> alreadyImported = imported.get( uid);
			    if ( alreadyImported == null || alreadyImported.isEmpty())
			    {
			    	eventsImported++;
			    	toImport.add( reservation);
			    }
			    else
			    {
			    	getLogger().debug("Ignoring event with uid " + uid +  " already imported. Ignoring" );
			    	eventsPresent++;
			    }
		    }
		}

		getClientFacade().storeObjects(toImport.toArray(Reservation.RESERVATION_ARRAY));
		return new Integer[] {eventsInICal, eventsImported, eventsPresent, eventsSkipped};
	}

	protected Map<String, List<Entity<Reservation>>> getImportedReservations(Date start)
			throws RaplaException {
		Map<String,List<Entity<Reservation>>> keyMap;
		 User user = null;
	     Date end = null;
	     keyMap = new LinkedHashMap<String, List<Entity<Reservation>>>();
	     Reservation[] reservations = getQuery().getReservations(user, start, end, null);
	     for ( Reservation r:reservations)
	     {
	         String key = r.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
	         if  ( key != null )
	         {
	             List<Entity<Reservation>> list = keyMap.get( key);
	             if ( list == null)
	             {
	                 list = new ArrayList<Entity<Reservation>>();
	                 keyMap.put( key, list);
	             }
	             list.add( r);
	         }
	     }
		return keyMap;
	}

//    
//	private Date toRaplaDate(TimeZone timeZone2,
//			net.fortuna.ical4j.model.Date startdate) 
//	{
//		java.util.Calendar cal = java.util.Calendar.getInstance( getRaplaLocale().getSystemTimeZone());
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
        Appointment appointment = getClientFacade().newAppointment(begin,end,user);
        return appointment;
    }


	/** if the recurances can be matched to one or more appointments return the appointment list else return null*/
	private List<Appointment> calcRepeating(List<Recur> recurList, Appointment start) throws RaplaException {
        final List<Appointment> appointments = new ArrayList<Appointment>();
        for (Recur recur : recurList) {
        	// FIXME need to implement UTC mapping
            final Appointment appointment = getClientFacade().newAppointment(start.getStart(), start.getEnd(), start.getOwner());
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
            NumberList monthList = recur.getMonthList();
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
