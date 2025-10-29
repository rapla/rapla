package org.rapla.plugin.tests;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.RaplaResources;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.logger.Logger;
import org.rapla.plugin.export2ical.server.Export2iCalConverter;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.server.internal.TimeZoneConverterImpl;
import org.rapla.test.util.RaplaTestCase;

import java.util.*;

@RunWith(JUnit4.class)
public class ICalExportTest {


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
	public void testICalExport() throws Exception{

        TimeZone timezone = TimeZone.getTimeZone("Europe/Berlin");
        TimeZoneConverterImpl converter = new TimeZoneConverterImpl();
        converter.setImportExportTimeZone(timezone);

        RaplaResources i18n =  ((FacadeImpl)facade).getI18n();
        Export2iCalConverter export = new Export2iCalConverter(converter, logger,facade, i18n);

        Preferences preferences = facade.getPreferences(user);
        Collection<Reservation> reservations = SynchronizedCompletablePromise.waitFor(facade.getReservationsAsync(user, Allocatable.ALLOCATABLE_ARRAY, new User[]{user}, null, null, null), 1000, logger);

        Collection<Appointment> appointments = new ArrayList<>();
        for ( Reservation res:reservations)
        {
        	appointments.addAll(Arrays.asList(res.getAppointments()));
        }
        export.createiCalender(appointments, preferences, user);

    }


}
