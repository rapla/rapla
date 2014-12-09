package org.rapla.plugin.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.rapla.RaplaTestCase;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.ical.server.RaplaICalImport;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.internal.TimeZoneConverterImpl;

public class ICalImportTest extends RaplaTestCase{

	public ICalImportTest(String name) {
		super(name);
	}
	
	public void testICalImport1() throws Exception{
        RaplaContext parentContext = getContext();
        RaplaDefaultContext context = new RaplaDefaultContext(parentContext);
        context.put( TimeZoneConverter.class, new TimeZoneConverterImpl());
        TimeZone timezone = TimeZone.getTimeZone("GMT+1");
        RaplaICalImport importer = new RaplaICalImport(context, timezone);
        boolean isUrl = true;
        String content = "https://www.google.com/calendar/ical/76kijffqdch1nkemshokjlf6r4%40group.calendar.google.com/private-e8c8772e35043055c7d9c16f366fdfbf/basic.ics";
        Allocatable newResource = getFacade().newResource();
        newResource.getClassification().setValue("name", "icaltest");
        getFacade().store( newResource);
        List<Allocatable> allocatables = Collections.singletonList( newResource);
        User user = getFacade().getUser("homer");
        String eventTypeKey = getFacade().getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].getKey();
        importer.importCalendar(content, isUrl, allocatables, user, eventTypeKey, "name");
    }
	
	public void testICalImport2() throws Exception{
        RaplaContext parentContext = getContext();
        RaplaDefaultContext context = new RaplaDefaultContext(parentContext);
        context.put( TimeZoneConverter.class, new TimeZoneConverterImpl());
        TimeZone timezone = TimeZone.getTimeZone("GMT+1");
        RaplaICalImport importer = new RaplaICalImport(context, timezone);
        boolean isUrl = false;
        String packageName = getClass().getPackage().getName().replaceAll("\\.", "/");
		String pathname = TEST_SRC_FOLDER_NAME + "/" + packageName + "/test.ics";
        BufferedReader reader = new BufferedReader(new FileReader( new File(pathname)));
        StringBuilder fileContent = new StringBuilder();
        while ( true)
        {
            String line = reader.readLine();
            if ( line == null)
            {
                break;
            }
            fileContent.append( line );
            fileContent.append( "\n");
        }
        reader.close();
        String content = fileContent.toString();
        Allocatable newResource = getFacade().newResource();
        newResource.getClassification().setValue("name", "icaltest");
        getFacade().store( newResource);
        List<Allocatable> allocatables = Collections.singletonList( newResource);
        User user = getFacade().getUser("homer");
        String eventTypeKey = getFacade().getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].getKey();
        importer.importCalendar(content, isUrl, allocatables, user, eventTypeKey, "name");
        Reservation[] reservations;
        {
            Date start = null;
            Date end = null;
            reservations = getFacade().getReservations( allocatables.toArray( Allocatable.ALLOCATABLE_ARRAY), start, end);
        }
        assertEquals( 1, reservations.length);
        Reservation event = reservations[0];
        Appointment[] appointments = event.getAppointments();
        assertEquals( 1, appointments.length);
        Appointment appointment = appointments[0];
        Date start= appointment.getStart();
        RaplaLocale raplaLocale = getRaplaLocale();
        // We expect a one our shift in time because we set GMT+1 in timezone settings and the timezone of the ical file is GMT+0
        Date time = raplaLocale.toTime(11+1, 30, 0);
        Date date = raplaLocale.toRaplaDate(2012, 11, 6);
        assertEquals( raplaLocale.toDate(date, time), start);
        
	}
}
