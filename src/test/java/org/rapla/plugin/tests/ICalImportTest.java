package org.rapla.plugin.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.logger.Logger;
import org.rapla.plugin.ical.server.RaplaICalImport;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.RemoteSessionImpl;
import org.rapla.server.internal.TimeZoneConverterImpl;
import org.rapla.test.util.RaplaTestCase;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class ICalImportTest {


    Logger logger;
    RaplaFacade facade;
    User user;

    @Before
    public void setUp() throws Exception
    {
        logger = RaplaTestCase.initLoger();
        ClientFacade clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        user = clientFacade.getUser();
        facade = clientFacade.getRaplaFacade();
    }

    @Test
	public void testICalImport1() throws Exception{

        TimeZone timezone = TimeZone.getTimeZone("GMT+1");
        TimeZoneConverterImpl converter = new TimeZoneConverterImpl();
        converter.setImportExportTimeZone(timezone);

        RemoteSession session = new RemoteSessionImpl(logger, user);
        RaplaICalImport importer = new RaplaICalImport(converter, session, facade, logger, null);
        boolean isUrl = true;
        String content = "https://www.google.com/calendar/ical/76kijffqdch1nkemshokjlf6r4%40group.calendar.google.com/private-e8c8772e35043055c7d9c16f366fdfbf/basic.ics";
        Allocatable newResource = facade.newResourceDeprecated();
        newResource.getClassification().setValue("name", "icaltest");
        facade.store(newResource);
        List<Allocatable> allocatables = Collections.singletonList( newResource);
        User user = facade.getUser("homer");
        String eventTypeKey = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].getKey();
        importer.importCalendar(content, isUrl, allocatables, user, eventTypeKey, "name");
    }

    @Ignore
    @Test
	public void testICalImport2() throws Exception{
        TimeZone timezone = TimeZone.getTimeZone("GMT+1");
        TimeZoneConverterImpl converter = new TimeZoneConverterImpl();
        converter.setImportExportTimeZone(timezone);
        RemoteSession session = new RemoteSessionImpl(logger, user);
        RaplaICalImport importer = new RaplaICalImport(converter,session,facade,logger, null);
        boolean isUrl = false;
        String packageName = getClass().getPackage().getName().replaceAll("\\.", "/");
        final String name = "/" + packageName + "/test.ics";
        StringBuilder fileContent = new StringBuilder();
        try (final InputStream resource1 = getClass().getResourceAsStream(name))
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource1));
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
        }
        String content = fileContent.toString();
        Allocatable newResource = facade.newResourceDeprecated();
        newResource.getClassification().setValue("name", "icaltest");
        facade.store(newResource);
        List<Allocatable> allocatables = Collections.singletonList( newResource);
        User user = facade.getUser("homer");
        String eventTypeKey = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].getKey();
        importer.importCalendar(content, isUrl, allocatables, user, eventTypeKey, "name");
        Collection<Reservation> reservations;
        {
            Date start = null;
            Date end = null;
            reservations = RaplaTestCase
                    .waitForWithRaplaException(facade.getReservationsForAllocatable(allocatables.toArray(Allocatable.ALLOCATABLE_ARRAY), start, end, null), 10000);
        }
        Assert.assertEquals(1, reservations.size());
        Reservation event = reservations.iterator().next();
        Appointment[] appointments = event.getAppointments();
        Assert.assertEquals(1, appointments.length);
        Appointment appointment = appointments[0];
        Date start= appointment.getStart();
        // We expect a one our shift in time because we set GMT+1 in timezone settings and the timezone of the ical file is GMT+0
        Date time = new Date(DateTools.toTime(11 + 1, 30, 0));
        Date date = new Date(DateTools.toDate(2012, 11, 6));
        Assert.assertEquals(DateTools.toDateTime(date, time), start);
        
	}
}
