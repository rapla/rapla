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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.MockMailer;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.plugin.notification.NotificationPlugin;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.test.util.RaplaTestCase;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** listens for allocation changes */
@RunWith(JUnit4.class)
public class NotificationPluginTest
{
    ServerServiceImpl raplaServer;

    RaplaFacade facade1;
    Locale locale;
    Logger logger;

    @Before
    public void setUp() throws Exception
    {
        // start the server
        logger = RaplaTestCase.initLoger();
        raplaServer = (ServerServiceImpl)RaplaTestCase.createServiceContainer(logger,"testdefault.xml");

        // start the client service
        facade1 = raplaServer.getFacade();
        locale = Locale.getDefault();
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

    @Test
    public void testAdd() throws Exception
    {
        Allocatable allocatable = facade1.getAllocatables()[0];
        Allocatable allocatable2 = facade1.getAllocatables()[1];

        add( allocatable, facade1.getPreferences() );
        User user2 = facade1.getUser("monty");
		add(allocatable2, facade1.getPreferences(user2));

        Reservation r = facade1.newReservation();
        String reservationName = "New Reservation";
        r.getClassification().setValue("name", reservationName);
        Appointment appointment = facade1.newAppointment( new Date(), new Date( new Date().getTime()
                + DateTools.MILLISECONDS_PER_HOUR ) );
        r.addAppointment(appointment);
        r.addAllocatable( allocatable );
        r.addAllocatable(allocatable2);

        
        System.out.println(r.getLastChanged());

        facade1.store(r);

        System.out.println(r.getLastChanged());

        MockMailer mailMock = null;
        for ( int i=0;i<1000;i++ )
        {
        	if (mailMock.getMailBody()!= null)
        	{
        		break;
        	}
        	Thread.sleep( 100 );
        }
        
        Assert.assertTrue(mailMock.getMailBody().indexOf(reservationName) >= 0);


        Assert.assertEquals(2, mailMock.getCallCount());
        
        reservationName = "Another name";
        r=facade1.edit( r);
        r.getClassification().setValue( "name", reservationName );
        r.getAppointments()[0].move( new Date( new Date().getTime() + DateTools.MILLISECONDS_PER_HOUR ) );
        facade1.store( r );

        System.out.println( r.getLastChanged() );

        Thread.sleep( 1000 );
        Assert.assertEquals(4, mailMock.getCallCount());

        Assert.assertNotNull(mailMock.getMailBody());
        Assert.assertTrue(mailMock.getMailBody().indexOf(reservationName) >= 0);

       

    }

    @Test
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

        MockMailer mailMock = null;
        for ( int i=0;i<1000;i++ )
        {
        	if (mailMock.getMailBody()!= null)
        	{
        		break;
        	}
        	Thread.sleep( 100 );
        }
        Assert.assertEquals(2, mailMock.getCallCount());
        String body = mailMock.getMailBody();
        Assert.assertTrue("Body doesnt contain delete text\n" + body, body.indexOf("gel\u00f6scht") >= 0);
     
    }

}