/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.storage.dbfile.tests;

import java.util.Date;
import java.util.concurrent.Semaphore;

import org.rapla.ServerTest;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.storage.CachableStorageOperator;

public class FileOperatorRemoteTest extends ServerTest {
    CachableStorageOperator operator;
    
    public FileOperatorRemoteTest(String name) {
        super(name);
    }
    
   protected String getStorageName() {
       return "storage-file";
   }
   
   public void testMultithread() throws Exception 
   {
	   facade1.login("homer","duffs".toCharArray());
	   ClientFacade facade2 = getContainer().lookup(ClientFacade.class, "remote-facade-2");
       facade2.login("homer","duffs".toCharArray());
       int count = 20;
       final Semaphore semaphore = new Semaphore(count);
       semaphore.acquire(count);
       for ( int i=0;i<count;i++)
	   {
		   Thread t = new Thread(){
			   public void run() {
				   try
				   {
					   _bla();
				   }
				   catch (Exception ex)
				   {
					   ex.printStackTrace();
				   }
				   semaphore.release(1);
			   };
			   
		   };
		   t.start();
	   }
       semaphore.acquire(count);
	   
   }
   private void _bla() throws Exception
   {
	   
	   {
	       facade1.removeObjects( facade1.getReservationsForAllocatable( null, null, null, null));
	   }
	   Date start =  new Date();
	   Date end = new Date( start.getTime() + DateTools.MILLISECONDS_PER_HOUR * 2);
	   {
	       Reservation r = facade1.newReservation(  );
	       r.getClassification().setValue("name","test-reservation");
	       Appointment a = facade1.newAppointment( start, end);
	       a.setRepeatingEnabled( true );
	       a.getRepeating().setType( Repeating.WEEKLY);
	       a.getRepeating().setInterval( 2 );
	       a.getRepeating().setNumber( 10 );
	       r.addAllocatable( facade1.getAllocatables()[0]);
	       r.addAppointment( a );
	       a.getRepeating().addException( start );
	       a.getRepeating().addException( new Date(start.getTime() + DateTools.MILLISECONDS_PER_WEEK));
	       facade1.store(  r  );
	      // facade1.logout();
	   }
	   {
	       ClientFacade facade2 = getContainer().lookup(ClientFacade.class, "remote-facade-2");
	       //facade2.login("homer","duffs".toCharArray());
	       facade2.refresh();
	       Reservation[] res = facade2.getReservationsForAllocatable( null,start, new Date( start.getTime()+ 8* DateTools.MILLISECONDS_PER_WEEK  ), null );
	       assertTrue( res.length > 0);
	       Thread.sleep( 100);
	       //facade2.logout();
	   }

   }  
}





