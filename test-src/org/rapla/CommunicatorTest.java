package org.rapla;

import java.util.Date;

import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.ConsoleLogger;
import org.rapla.storage.dbrm.RemoteOperator;
import org.rapla.storage.dbrm.RemoteServer;
import org.rapla.storage.dbrm.RemoteStorage;

public class CommunicatorTest extends ServletTestBase
{
    
    public CommunicatorTest( String name )
    {
        super( name );
    }

  
    public void testLargeform() throws Exception
    {
        ClientFacade facade = getContainer().lookup(ClientFacade.class, "remote-facade");
        facade.login("homer","duffs".toCharArray());
        Allocatable alloc = facade.newResource();
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
    
    
    public void testClient() throws Exception
    {
       ClientFacade facade = getContainer().lookup(ClientFacade.class, "remote-facade");
       boolean success = facade.login("admin","test".toCharArray());
       assertFalse( "Login should fail",success ); 
       facade.login("homer","duffs".toCharArray());
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
           assertTrue( allocatables.length > 0);
           Reservation[] events = facade.getReservations( new Allocatable[] {allocatables[0]}, null,null);
           assertTrue( events.length > 0);
           
           Reservation r = events[0];
           Reservation editable = facade.edit( r);
           facade.store( editable );
           
           Reservation newEvent = facade.newReservation();
           Appointment newApp = facade.newAppointment( new Date(), new Date());
           newEvent.addAppointment( newApp );
           newEvent.getClassification().setValue("name","Test Reservation");
           newEvent.addAllocatable( allocatables[0]);
           
           facade.store( newEvent );
           facade.remove( newEvent);
       }
       finally
       {
           facade.logout();
       }
    }

    public void testUmlaute() throws Exception
    {
        ClientFacade facade = getContainer().lookup(ClientFacade.class , "remote-facade");
        facade.login("homer","duffs".toCharArray());
        Allocatable alloc = facade.newResource();
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
        
        facade.logout();
        facade.login("homer","duffs".toCharArray());
        DynamicType type = facade.getDynamicType( typeName);
        ClassificationFilter filter = type.newClassificationFilter();
        filter.addEqualsRule("name", nameWithUmlaute);
        Allocatable[] allAllocs = facade.getAllocatables();
        assertEquals( allocSizeBefore + 1, allAllocs.length);
        Allocatable[] allocs = facade.getAllocatables( new ClassificationFilter[] {filter});
        assertEquals( 1, allocs.length);
        
    }
    public void testManyClients() throws Exception
    {
        RaplaContext context = getContext();
        int clientNum = 50;
        RemoteOperator [] opts = new RemoteOperator[ clientNum];
        DefaultConfiguration remoteConfig = new DefaultConfiguration("element");
        DefaultConfiguration serverParam = new DefaultConfiguration("server");
        serverParam.setValue("http://localhost:8052/");
        remoteConfig.addChild( serverParam );
     
        for ( int i=0;i<clientNum;i++)
        {
			RaplaMainContainer container = (RaplaMainContainer) getContainer();
            RemoteServer remoteServer = container.getRemoteMethod( context, RemoteServer.class);
			RemoteStorage remoteStorage = container.getRemoteMethod(context, RemoteStorage.class);
			RemoteOperator opt = new RemoteOperator(context,new ConsoleLogger(),remoteConfig, remoteServer, remoteStorage );
            opt.connect(new ConnectInfo("homer","duffs".toCharArray()));
            opts[i] = opt;
            System.out.println("Client " + i + " successfully subscribed");
        }
        testClient();
        
        for ( int i=0;i<clientNum;i++)
        {
            RemoteOperator opt = opts[i];
            opt.disconnect();
        }
    }
}
