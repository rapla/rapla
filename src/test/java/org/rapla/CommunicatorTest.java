package org.rapla;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.TypedComponentRole;
import org.rapla.test.util.RaplaTestCase;

import java.util.Collection;
import java.util.Date;

@RunWith(JUnit4.class)
public class CommunicatorTest extends AbstractTestWithServer
{

    @Test
    public void testLargeform() throws Exception
    {
        ClientFacade clientFacade = createClientFacade();
        RaplaFacade facade = clientFacade.getRaplaFacade();
        login(clientFacade,"homer","duffs".toCharArray());
        Allocatable alloc = newResource(clientFacade);
        StringBuffer buf = new StringBuffer();
        int stringsize = 100000;
        for (int i=0;i< stringsize;i++)
        {
            buf.append( "xxxxxxxxxx");
        }
        String verylongname = buf.toString();
        alloc.getClassification().setValue("name", verylongname);
        facade.store( alloc);
    }
    

    @Test
    public void testClient() throws Throwable
    {
        ClientFacade clientFacade = createClientFacade();
        RaplaFacade facade = clientFacade.getRaplaFacade();
       boolean success = login(clientFacade,"admin","test".toCharArray());
       Assert.assertFalse("Login should fail", success);
        login(clientFacade,"homer","duffs".toCharArray());
       try 
       {
           Preferences preferences = facade.edit( facade.getSystemPreferences());
           TypedComponentRole<String> TEST_ENTRY = new TypedComponentRole<String>("test-entry");
           preferences.putEntry(TEST_ENTRY, "test-value");
           
           facade.store( preferences);
           preferences = facade.edit( facade.getSystemPreferences());
           preferences.putEntry(TEST_ENTRY, "test-value");
           facade.store( preferences);

           Allocatable[] allocatables = facade.getAllocatables();
           Assert.assertTrue(allocatables.length > 0);

           Reservation newEvent = newReservation(clientFacade);
           Appointment newApp = facade.newAppointmentDeprecated( new Date(), new Date());
           newEvent.addAppointment( newApp );
           newEvent.getClassification().setValue("name","Test Reservation");
           newEvent.addAllocatable( allocatables[0]);

           facade.store( newEvent );

           Collection<Reservation> events = RaplaTestCase.waitForWithRaplaException(facade.getReservationsForAllocatable(new Allocatable[] { allocatables[0] }, null,null,null), 10000);
           Assert.assertTrue(events.size() > 0);
           
           Reservation r = events.iterator().next();
           Reservation editable = facade.edit( r);
           facade.store( editable );

           facade.remove( newEvent);
       }
       finally
       {
           logout(clientFacade);
       }
    }

    @Ignore
    @Test
    public void testUmlaute() throws Exception
    {
        ClientFacade clientFacade = createClientFacade();
        RaplaFacade facade = clientFacade.getRaplaFacade();
        login(clientFacade,"homer","duffs".toCharArray());
        Allocatable alloc = newResource(clientFacade);
        String typeName = alloc.getClassification().getType().getKey();
        // AE = \u00C4
        // OE = \u00D6
        // UE = \u00DC
        // ae = \u00E4
        // oe = \u00F6
        // ue = \u00FC
        // ss = \u00DF
        String nameWithUmlaute = "\u00C4\u00D6\u00DC\u00E4\u00F6\u00FC\u00DF";
        alloc.getClassification().setValue("name", nameWithUmlaute);
        int allocSizeBefore = facade.getAllocatables().length;
        facade.store( alloc);

        logout(clientFacade);
        login(clientFacade,"homer","duffs".toCharArray());
        DynamicType type = facade.getDynamicType( typeName);
        ClassificationFilter filter = type.newClassificationFilter();
        filter.addEqualsRule("name", nameWithUmlaute);
        Allocatable[] allAllocs = facade.getAllocatables();
        Assert.assertEquals(allocSizeBefore + 1, allAllocs.length);
        Allocatable[] allocs = facade.getAllocatablesWithFilter( new ClassificationFilter[] {filter});
        Assert.assertEquals(1, allocs.length);
    }

    @Test
    public void testManyClients() throws Exception
    {
        int clientNum = 50;
        ClientFacade[] opts = new ClientFacade[ clientNum];


        for ( int i=0;i<clientNum;i++)
        {
            ClientFacade clientFacade = createClientFacade();
            login(clientFacade,"homer","duffs".toCharArray());
            opts[i] = clientFacade;
            System.out.println("JavaClient " + i + " successfully subscribed");
        }

        for ( int i=0;i<clientNum;i++)
        {
            ClientFacade opt = opts[i];
            logout(opt);
        }
    }
}
