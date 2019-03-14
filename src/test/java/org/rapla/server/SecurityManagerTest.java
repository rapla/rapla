package org.rapla.server;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.AbstractTestWithServer;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.RaplaTestCase;

import java.util.Collection;
import java.util.Date;
import java.util.Locale;

@RunWith(JUnit4.class)
public class SecurityManagerTest extends AbstractTestWithServer {


	protected ClientFacade clientFacade1;
	protected RaplaFacade facade1;
	
	Locale locale;

	@Before public void setUp() throws Exception
	{
		clientFacade1 = createClientFacade();
		facade1 = clientFacade1.getRaplaFacade();
		//facade2 = clientFacadeProvider.get();
		//facade2.login("homer", "duffs".toCharArray());
		locale = Locale.getDefault();
	}

	@Test
	public void testConflictForbidden() throws Exception
	{
		// We test conflict prevention for an appointment that is in the future
		Date start = new Date(facade1.today().getTime() + DateTools.MILLISECONDS_PER_DAY  +  10 * DateTools.MILLISECONDS_PER_HOUR);
		Date end = new Date( start.getTime() + 2 * DateTools.MILLISECONDS_PER_HOUR);

		login(clientFacade1,"homer", "duffs".toCharArray());
		DynamicType roomType = facade1.getDynamicType("room");
		ClassificationFilter filter = roomType.newClassificationFilter();
		filter.addEqualsRule("name", "erwin");
		Allocatable resource = facade1.getAllocatablesWithFilter( filter.toArray())[0];
		Appointment app1;
		{
			app1 = facade1.newAppointmentDeprecated( start, end ) ;
			// First we createInfoDialog a reservation for the resource
			Reservation event = facade1.newReservationDeprecated();
			event.getClassification().setValue("name", "taken");
			event.addAppointment( app1 );
			event.addAllocatable( resource );
			facade1.store( event );
		}
		logout(clientFacade1);
		// Now we login as a non admin user, who isnt allowed to create conflicts on the resource erwin
		login(clientFacade1,"monty", "burns".toCharArray());
		{
			Reservation event = facade1.newReservationDeprecated();
			// A new event with the same time for the same resource should fail. 
			event.getClassification().setValue("name", "conflicting event");
			Appointment app = facade1.newAppointmentDeprecated( start, end ) ;
			event.addAppointment( app);
			event.addAllocatable( resource );
			try 
			{
				facade1.store( event );
				Assert.fail("Security Exception expected");
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
					Assert.fail("Exception expected but was not security exception");
				}
			}
			// moving the start of the second appointment to the end of the first one should work 
			app.moveTo( end );
			facade1.store( event );
		}
		{
			// We have to re-get the event
			DynamicType eventType = facade1.getDynamicType("event");
			ClassificationFilter eventFilter = eventType.newClassificationFilter();
			eventFilter.addEqualsRule("name", "conflicting event");
			
			Reservation event = RaplaTestCase.waitForWithRaplaException(facade1.getReservationsForAllocatable( null, null, null, eventFilter.toArray()), 10000).iterator().next();
				// But moving back the appointment to today should fail
			event = facade1.edit( event );
			Date startPlus1 = new Date( start.getTime() + DateTools.MILLISECONDS_PER_HOUR) ;
			event.getAppointments()[0].move( startPlus1,end);
			try 
			{
				facade1.store( event );
				Assert.fail("Security Exception expected");
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
					Assert.fail("Exception expected but was not security exception");
				}
			}
			logout(clientFacade1);
		    Thread.sleep(100);
		}

	}

	@Test
	public void testUserAdminGroup() throws Exception
	{
		login(clientFacade1,"monty", "burns".toCharArray());
		final RaplaFacade raplaFacade = clientFacade1.getRaplaFacade();
		final Category userGroupsCategory = raplaFacade.getUserGroupsCategory();
		Category powerplant = userGroupsCategory.getCategory("powerplant");
		Category powerplantStaff = powerplant.getCategory("powerplant-staff");
		final Category newNonAdminableUserGroup = raplaFacade.newCategory();
		{
			newNonAdminableUserGroup.getName().setName("en", "new catgory");
            newNonAdminableUserGroup.setKey("newNonAdminableUserGroup");
			final Category edit = getServerRaplaFacade().edit(userGroupsCategory);
			edit.addCategory( newNonAdminableUserGroup );
			getServerRaplaFacade().store(edit);
		}
		raplaFacade.refresh();
		final User newUser;
		{
			newUser = raplaFacade.newUser();
			TestCase.assertTrue( newUser.getGroupList().contains( powerplant));
			newUser.setUsername("waylon");
			newUser.setEmail("smithers@rapla.dummy.rapla");
			newUser.getGroupList().stream().forEach( newUser::removeGroup);
			newUser.addGroup(powerplant);
			newUser.addGroup(powerplantStaff);
			raplaFacade.store(newUser);
		}
		final User[] users = raplaFacade.getUsers();
		TestCase.assertEquals(2, users.length);
		Category newCategory;
		{
			final Category editablePowerplant = raplaFacade.edit(powerplant);
			newCategory = raplaFacade.newCategory();
			newCategory.setKey("testkey");
			newCategory.getName().setName("en", "new catgory");
			editablePowerplant.addCategory(newCategory);
			raplaFacade.store(editablePowerplant);
		}
		{
			final User editUser = raplaFacade.edit(newUser);
			editUser.removeGroup(powerplantStaff);
			raplaFacade.store(editUser);
		}
		{
			final User editUser = raplaFacade.edit(newUser);
			editUser.addGroup(newNonAdminableUserGroup);
			try
			{
				raplaFacade.store(editUser);
				TestCase.fail("Security Exception should be thrown");
			}
			catch (RaplaSecurityException ex)
			{
			}
		}
		{
			final User editUser = raplaFacade.edit(newUser);
			editUser.addGroup(userGroupsCategory.getCategory("my-group"));
			try
			{
				raplaFacade.store(editUser);
				TestCase.fail("Security Exception should be thrown");
			}
			catch (RaplaSecurityException ex)
			{
			}
		}
		{
			final User editUser = raplaFacade.edit(newUser);
			raplaFacade.removeObjects(new Entity[] { editUser });
		}

		{
			raplaFacade.remove(raplaFacade.edit(newCategory));
		}
		try
		{
			raplaFacade.remove(newNonAdminableUserGroup);
			TestCase.fail("Security Exception should be thrown. Category should not be removeable");
		}
		catch (RaplaSecurityException ex)
		{
		}


	}
	
}
