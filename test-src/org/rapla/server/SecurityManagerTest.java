package org.rapla.server;

import java.util.Date;
import java.util.Locale;

import org.rapla.ServletTestBase;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.storage.RaplaSecurityException;

public class SecurityManagerTest extends ServletTestBase {


	protected ClientFacade facade1;
	
	Locale locale;

	public SecurityManagerTest(String name) 
	{
		super(name);
	}

	protected void setUp() throws Exception 
	{
		super.setUp();
		// start the server
		getContainer().lookup(ServerServiceContainer.class, getStorageName());

		// start the client service
		facade1 = getContainer().lookup(ClientFacade.class , "remote-facade");
		locale = Locale.getDefault();
	}

	protected String getStorageName() 
	{
		return "storage-file";
	}
	
	public void testConflictForbidden() throws Exception
	{
		// We test conflict prevention for an appointment that is in the future
		Date start = new Date(facade1.today().getTime() + DateTools.MILLISECONDS_PER_DAY  +  10 * DateTools.MILLISECONDS_PER_HOUR);
		Date end = new Date( start.getTime() + 2 * DateTools.MILLISECONDS_PER_HOUR);
		
		facade1.login("homer", "duffs".toCharArray());
		DynamicType roomType = facade1.getDynamicType("room");
		ClassificationFilter filter = roomType.newClassificationFilter();
		filter.addEqualsRule("name", "erwin");
		Allocatable resource = facade1.getAllocatables( filter.toArray())[0];
		Appointment app1;
		{
			app1 = facade1.newAppointment( start, end ) ;
			// First we create a reservation for the resource
			Reservation event = facade1.newReservation();
			event.getClassification().setValue("name", "taken");
			event.addAppointment( app1 );
			event.addAllocatable( resource );
			facade1.store( event );
		}
		facade1.logout();
		// Now we login as a non admin user, who isnt allowed to create conflicts on the resource erwin
		facade1.login("monty", "burns".toCharArray());
		{
			Reservation event = facade1.newReservation();
			// A new event with the same time for the same resource should fail. 
			event.getClassification().setValue("name", "conflicting event");
			Appointment app = facade1.newAppointment( start, end ) ;
			event.addAppointment( app);
			event.addAllocatable( resource );
			try 
			{
				facade1.store( event );
				fail("Security Exception expected");
			}
			catch ( Exception ex)
			{
				Throwable ex1 = ex;
				boolean secExceptionFound = false;
				while ( ex1 != null)
				{
					if ( ex1 instanceof RaplaSecurityException)
					{
						secExceptionFound = true;
					}
					ex1 = ex1.getCause();
				}
				if ( !secExceptionFound)
				{
					ex.printStackTrace();
					fail("Exception expected but was not security exception");
				}
			}
			// moving the start of the second appointment to the end of the first one should work 
			app.move( end );
			facade1.store( event );
		}
		{
			// We have to reget the event
			DynamicType eventType = facade1.getDynamicType("event");
			ClassificationFilter eventFilter = eventType.newClassificationFilter();
			eventFilter.addEqualsRule("name", "conflicting event");
			
			Reservation event = facade1.getReservationsForAllocatable( null, null, null, eventFilter.toArray())[0];
				// But moving back the appointment to today should fail
			event = facade1.edit( event );
			Date startPlus1 = new Date( start.getTime() + DateTools.MILLISECONDS_PER_HOUR) ;
			event.getAppointments()[0].move( startPlus1,end);
			try 
			{
				facade1.store( event );
				fail("Security Exception expected");
			}
			catch ( Exception ex)
			{
				Throwable ex1 = ex;
				boolean secExceptionFound = false;
				while ( ex1 != null)
				{
					if ( ex1 instanceof RaplaSecurityException)
					{
						secExceptionFound = true;
					}
					ex1 = ex1.getCause();
				}
				if ( !secExceptionFound)
				{
					ex.printStackTrace();
					fail("Exception expected but was not security exception");
				}
			}
			facade1.logout();
		    Thread.sleep(100);
		}

	}
	
}
