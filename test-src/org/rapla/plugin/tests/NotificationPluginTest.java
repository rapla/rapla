/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.plugin.tests;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.rapla.MockMailer;
import org.rapla.ServletTestBase;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.server.MailInterface;
import org.rapla.plugin.notification.NotificationPlugin;
import org.rapla.server.ServerService;
import org.rapla.server.ServerServiceContainer;

/** listens for allocation changes */
public class NotificationPluginTest extends ServletTestBase
{
    ServerService raplaServer;

    ClientFacade facade1;
    Locale locale;

    public NotificationPluginTest( String name )
    {
        super( name );
    }

    protected void setUp() throws Exception
    {
        super.setUp();
        // start the server
        ServerServiceContainer raplaServerContainer =  getContainer().lookup( ServerServiceContainer.class, getStorageName() );
        raplaServer = raplaServerContainer.getContext().lookup( ServerService.class );

        // start the client service
        facade1 = getContainer().lookup( ClientFacade.class , "remote-facade" );
        facade1.login( "homer", "duffs".toCharArray() );
        locale = Locale.getDefault();
    }

    protected void tearDown() throws Exception
    {
        facade1.logout();
        super.tearDown();
    }

    protected String getStorageName()
    {
        return "storage-file";
    }

    private void add( Allocatable allocatable,         Preferences preferences ) throws RaplaException
    {
        Preferences copy = facade1.edit( preferences );
        RaplaMap<Allocatable> raplaEntityList =  copy.getEntry( NotificationPlugin.ALLOCATIONLISTENERS_CONFIG );
        List<Allocatable> list;
        if ( raplaEntityList != null )
        {
            list = new ArrayList<Allocatable>( raplaEntityList.values() );
        }
        else
        {
            list = new ArrayList<Allocatable>();
        }
        list.add( allocatable );
        //getLogger().info( "Adding notificationEntry " + allocatable );
        RaplaMap<Allocatable> newRaplaMap = facade1.newRaplaMap( list );
		copy.putEntry( NotificationPlugin.ALLOCATIONLISTENERS_CONFIG, newRaplaMap );
        copy.putEntry( NotificationPlugin.NOTIFY_IF_OWNER_CONFIG,  true  );
        facade1.store( copy );
    }

    public void testAdd() throws Exception
    {
        Allocatable allocatable = facade1.getAllocatables()[0];
        Allocatable allocatable2 = facade1.getAllocatables()[1];

        add( allocatable, facade1.getPreferences() );
        User user2 = facade1.getUser("monty");
		add( allocatable2, facade1.getPreferences(user2) );

        Reservation r = facade1.newReservation();
        String reservationName = "New Reservation";
        r.getClassification().setValue( "name", reservationName );
        Appointment appointment = facade1.newAppointment( new Date(), new Date( new Date().getTime()
                + DateTools.MILLISECONDS_PER_HOUR ) );
        r.addAppointment( appointment );
        r.addAllocatable( allocatable );
        r.addAllocatable( allocatable2 );

        
        System.out.println( r.getLastChanged() );

        facade1.store( r );

        System.out.println( r.getLastChanged() );

        MockMailer mailMock = (MockMailer) raplaServer.getContext().lookup( MailInterface.class );
        for ( int i=0;i<1000;i++ )
        {
        	if (mailMock.getMailBody()!= null)
        	{
        		break;
        	}
        	Thread.sleep( 100 );
        }
        
        assertTrue( mailMock.getMailBody().indexOf( reservationName ) >= 0 );

       
        assertEquals( 2, mailMock.getCallCount() );
        
        reservationName = "Another name";
        r=facade1.edit( r);
        r.getClassification().setValue( "name", reservationName );
        r.getAppointments()[0].move( new Date( new Date().getTime() + DateTools.MILLISECONDS_PER_HOUR ) );
        facade1.store( r );

        System.out.println( r.getLastChanged() );

        Thread.sleep( 1000 );
        assertEquals( 4, mailMock.getCallCount() );

        assertNotNull( mailMock.getMailBody() );
        assertTrue( mailMock.getMailBody().indexOf( reservationName ) >= 0 );

       

    }

    public void testRemove() throws Exception
    {
        Allocatable allocatable = facade1.getAllocatables()[0];

        add( allocatable, facade1.getPreferences() );

        Reservation r = facade1.newReservation();
        String reservationName = "New Reservation";
        r.getClassification().setValue( "name", reservationName );
        Appointment appointment = facade1.newAppointment( new Date(), new Date( new Date().getTime()
                + DateTools.MILLISECONDS_PER_HOUR ) );
        r.addAppointment( appointment );
        r.addAllocatable( allocatable );


        facade1.store( r );
        facade1.remove( r );

        MockMailer mailMock = (MockMailer) raplaServer.getContext().lookup( MailInterface.class );
        for ( int i=0;i<1000;i++ )
        {
        	if (mailMock.getMailBody()!= null)
        	{
        		break;
        	}
        	Thread.sleep( 100 );
        }
        assertEquals( 2, mailMock.getCallCount() );
        String body = mailMock.getMailBody();
        assertTrue( "Body doesnt contain delete text\n" + body, body.indexOf( "gel\u00f6scht" ) >= 0 );
     
    }

}