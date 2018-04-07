package org.rapla;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.test.util.RaplaTestCase;

import java.util.Date;

@Ignore
@RunWith(JUnit4.class)
public class HugeDataFileTest
{
    @Test
    public void testHuge() throws Exception
    {
        ClientFacade clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        RaplaFacade facade = clientFacade.getRaplaFacade();
        int RESERVATION_COUNT =15000;
        Reservation[] events = new Reservation[RESERVATION_COUNT];
        
        for ( int i=0;i<RESERVATION_COUNT;i++)
        {
            Reservation event = facade.newReservationDeprecated();
            Appointment app1 = facade.newAppointmentDeprecated(new Date(), new Date());
            Appointment app2 = facade.newAppointmentDeprecated(new Date(), new Date());
            event.addAppointment( app1);
            event.addAppointment( app2);
            event.getClassification().setValue("name", "Test-Event " + i);
            events[i] = event;
        }

        facade.storeObjects(events);
        clientFacade.logout();

        
        clientFacade.login("homer", "duffs".toCharArray());
    }
    

}
