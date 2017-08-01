/*--------------------------------------------------------------------------*
  | Copyright (C) 2014 Christopher Kohlhaas                                  |
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU General Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org .       |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/

package org.rapla.storage.xml;

import org.rapla.components.util.Assert;
import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;

import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

public class ReservationReader extends RaplaXMLReader {
    ReservationImpl reservation;
    private ReferenceInfo<Allocatable> allocatableId = null;
    private AppointmentImpl appointment = null;
    private Repeating repeating = null;
    
    private DynAttReader dynAttHandler;
	private String annotationKey;
	private Annotatable currentAnnotatable;
	private PermissionReader permissionHandler;
    public ReservationReader( RaplaXMLContext context) throws RaplaException  {
        super( context);
        dynAttHandler = new DynAttReader( context);
        permissionHandler = new PermissionReader( context );
        addChildHandler(dynAttHandler);
        addChildHandler( permissionHandler);
    }

    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
        throws RaplaSAXParseException
    {
    	if (namespaceURI.equals( DYNATT_NS ) || namespaceURI.equals( EXTENSION_NS )) 
        {
            dynAttHandler.setClassifiable(reservation);
            delegateElement(dynAttHandler,namespaceURI,localName,atts);
            return;
        }

        if (!namespaceURI.equals(RAPLA_NS))
            return;

        if (localName.equals( "permission" ))
        {
            permissionHandler.setContainer( reservation);
            delegateElement(
                    permissionHandler,
                    namespaceURI,
                    localName,
                    atts );
            return;
            
        }
        if ( localName.equals( "reservation" ) ) 
        {
            TimestampDates ts = readTimestamps( atts);
            reservation = new ReservationImpl( ts.createTime, ts.changeTime );
			reservation.setResolver( store );
            currentAnnotatable = reservation;
            setId(reservation, atts);
            setLastChangedBy( reservation, atts);
            setOwner(reservation, atts);
        }

        if (localName.equals("appointment")) 
        {
            String id = atts.getValue("id");
            String startDate=atts.getValue("start-date");
            String endDate= atts.getValue("end-date");
            if (endDate == null)
                endDate = startDate;

            String startTime = atts.getValue("start-time");
            String endTime = atts.getValue("end-time");

            Date start;
            Date end;
            if (startTime != null && endTime != null) 
            {
                start = parseDateTime(startDate,startTime);
                end = parseDateTime(endDate,endTime);
            } 
            else 
            {
                start = parseDate(startDate,false);
                end = parseDate(endDate,true);
            }

            appointment= new AppointmentImpl(start,end);
            appointment.setWholeDays(startTime== null && endTime==null);
            if (id!=null)
            {
                setId(appointment, atts);
            } 
            else 
            {
                setNewId(appointment);
            }
            addAppointment(appointment);
        }

        if (localName.equals("repeating")) {
            String type =atts.getValue("type");
            String interval =atts.getValue("interval");
            String enddate =atts.getValue("end-date");
            String number =atts.getValue("number");
            String weekdays =atts.getValue("weekdays");
            appointment.setRepeatingEnabled(true);
            repeating = appointment.getRepeating();
            repeating.setType( RepeatingType.findForString( type));
            if (interval != null)
            {
                repeating.setInterval(Integer.valueOf(interval).intValue());
            }
            if (enddate != null) 
            {
                repeating.setEnd(parseDate(enddate,true));
            } 
            else if (number != null) 
            {
                repeating.setNumber(Integer.valueOf(number).intValue());
            } 
            else 
            {
                repeating.setEnd(null);
            }
            if (weekdays != null)
            {
                String[] days =weekdays.split(",");
                Set<Integer> weekdaySet = new TreeSet();
                for ( String dayAsString: days)
                {
                    Integer day = Integer.parseInt( dayAsString);
                    weekdaySet.add(day);
                }
                repeating.setWeekdays( weekdaySet);
            }

            /*
            if (getLogger().enabled(6))
                getLogger().log(6, "Repeating " + repeating.toString() );
            */
        }

        if (localName.equals("allocate")) {
            String id = getString( atts, "idref" );
            allocatableId = getId( Allocatable.class, id);
            reservation.addId("resources", allocatableId.getId() );
            if ( appointment != null )
            {
                reservation.addRestrictionForId( allocatableId.getId(), appointment.getId());
            }
        }

        if (localName.equals( "annotation" ) && namespaceURI.equals( RAPLA_NS ))
        {
            annotationKey = atts.getValue( "key" );
            Assert.notNull( annotationKey, "key attribute cannot be null" );
            startContent();
        }
        
        
        if (localName.equals("exception")) {
        }

        if (localName.equals("date")) {
            String dateString =atts.getValue("date");
            if (dateString != null && repeating != null)
                repeating.addException(parseDate(dateString,false));
        }
    }

	protected void addAppointment(Appointment appointment) {
		reservation.addAppointment(appointment);
	}

	@Override
    public void processEnd(String namespaceURI,String localName) throws RaplaSAXParseException
    {
        if (!namespaceURI.equals(RAPLA_NS))
            return;

        if (localName.equals("appointment") && appointment != null )
        {
            add(appointment);
            appointment = null;
        }
        if (localName.equals("reservation"))
        {
            if (isBefore1_2())
            {
                @SuppressWarnings("deprecation")
                String groupCanReadEventsFromOthers = Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS;
                addNewPermissionWithGroup(reservation, Permission.READ, groupCanReadEventsFromOthers);
            }

            add(reservation);
        }
        
        if (localName.equals( "annotation" ) && namespaceURI.equals( RAPLA_NS ))
        {
            try
            {
                String annotationValue = readContent().trim();
				currentAnnotatable.setAnnotation( annotationKey, annotationValue );
            }
            catch (IllegalAnnotationException ex)
            {
            }
        }
    }

	private void addNewPermissionWithGroup(PermissionContainer container, Permission.AccessLevel acceassLevel, String groupKey) throws RaplaSAXParseException  {
        ReferenceInfo<Category> group = getGroup(groupKey);
        PermissionImpl permission = (PermissionImpl)container.newPermission();
        permission.setAccessLevel( acceassLevel);
        permission.setGroupId(group);
        container.addPermission( permission);
    }
}




