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
package org.rapla.storage.tests;

import org.junit.Assert;
import org.junit.Test;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateResult;

import java.util.Collection;
import java.util.Date;

public abstract class AbstractOperatorTest  {

	abstract protected RaplaFacade getFacade();
	protected CachableStorageOperator getOperator()
	{
		RaplaFacade facade = getFacade();
		return (CachableStorageOperator)facade.getOperator();
	}

	@Test
    public void testReservationStore() throws RaplaException {
		RaplaFacade facade = getFacade();
        // abspeichern
        facade.login("homer", "duffs".toCharArray() );
        {
	        Reservation r = facade.newReservation();
	        r.getClassification().setValue("name","test");
	        Appointment app = facade.newAppointment( new Date(), new Date());
	        Appointment app2 = facade.newAppointment( new Date(), new Date());
	        Allocatable resource = facade.newResource();
	        r.addAppointment( app);
	        r.addAppointment( app2);
	        r.addAllocatable(resource );
	        r.setRestriction( resource, new Appointment[] {app});
	        app.setRepeatingEnabled( true );
	        app.getRepeating().setType(Repeating.DAILY);
	        app.getRepeating().setNumber( 10);
	        app.getRepeating().addException( new Date());
	        facade.storeObjects( new Entity[] { r, resource });
	    }
		CachableStorageOperator operator = getOperator();
        operator.disconnect();
        operator.connect();
        facade.login("homer", "duffs".toCharArray() );
        // einlesen
        {
	        String defaultReservation = "event";
	        ClassificationFilter filter = facade.getDynamicType( defaultReservation ).newClassificationFilter();
	        filter.addRule("name",new Object[][] { {"contains","test"}});
	        Reservation reservation = facade.getReservationsForAllocatable( null, null, null, new ClassificationFilter[] {filter} )[0];
	        Appointment[] apps = reservation.getAppointments();
	        Allocatable resource = reservation.getAllocatables()[0];
	        Assert.assertEquals(2, apps.length);
			Assert.assertEquals(1, reservation.getAppointmentsFor(resource).length);
	        Appointment app = reservation.getAppointmentsFor( resource )[0];
			Assert.assertEquals(1, app.getRepeating().getExceptions().length);
			Assert.assertEquals(Repeating.DAILY, app.getRepeating().getType());
			Assert.assertEquals(10, app.getRepeating().getNumber());
        }
    }

	@Test
    public void testUserStore() throws RaplaException {
		RaplaFacade facade = getFacade();
        facade.login("homer", "duffs".toCharArray() );
        {
            User u = facade.newUser();
            u.setUsername("kohlhaas");
	        u.setAdmin( false);
	        u.addGroup( facade.getUserGroupsCategory().getCategory("my-group"));
	        facade.store( u );
        }
		CachableStorageOperator operator = getOperator();
        operator.disconnect();
        operator.connect();
        facade.login("homer", "duffs".toCharArray() );
        {
            User u = facade.getUser("kohlhaas");
            Category[] groups = u.getGroupList().toArray( new Category [] {});
			Assert.assertEquals(groups.length, 4);
			Assert.assertEquals(facade.getUserGroupsCategory().getCategory("my-group"), groups[3]);
			Assert.assertFalse(u.isAdmin());
        }
    }

	@Test
    public void testCategoryAnnotation() throws RaplaException {
		RaplaFacade facade = getFacade();
    	String sampleDoc = "This is the category for user-groups";
    	String sampleAnnotationValue = "documentation";
    	facade.login("homer", "duffs".toCharArray() );
        {
        	Category userGroups = facade.edit( facade.getUserGroupsCategory());
        	userGroups.setAnnotation( sampleAnnotationValue, sampleDoc );
        	facade.store( userGroups );
        }
		CachableStorageOperator operator = getOperator();
        operator.disconnect();
        operator.connect();
        facade.login("homer", "duffs".toCharArray() );
        {
        	Category userGroups = facade.getUserGroupsCategory();
			Assert.assertEquals(sampleDoc, userGroups.getAnnotation(sampleAnnotationValue));
        }
    }

	@Test
    public void testAttributeStore() throws RaplaException {
		RaplaFacade facade = getFacade();
        facade.login("homer", "duffs".toCharArray() );
		CachableStorageOperator operator = getOperator();
        // abspeichern
        {
	        DynamicType type = facade.edit( facade.getDynamicType("event"));

	        Attribute att = facade.newAttribute( AttributeType.STRING );
	        att.setKey("test-att");
	        type.addAttribute( att );

	        Reservation r = facade.newReservation();
	        try {
	        	r.setClassification( type.newClassification() );
				Assert.fail("Should have thrown an IllegalStateException");
	        } catch (IllegalStateException ex) {
	        }

	        facade.store( type );

	        r.setClassification( facade.getPersistant(type).newClassification() );

	        r.getClassification().setValue("name","test");
	        r.getClassification().setValue("test-att","test-att-value");
	        Appointment app = facade.newAppointment( new Date(), new Date());
	        Appointment app2 = facade.newAppointment( new Date(), new Date());
	        Allocatable resource = facade.newResource();
	        r.addAppointment( app);
	        r.addAppointment( app2);
	        r.addAllocatable(resource );
	        r.setRestriction( resource, new Appointment[] {app});
	        app.setRepeatingEnabled( true );
	        app.getRepeating().setType(Repeating.DAILY);
	        app.getRepeating().setNumber( 10);
	        app.getRepeating().addException( new Date());
	        facade.storeObjects( new Entity[] { r, resource });
        	operator.disconnect();
	    }
        // einlesen
        {
	        operator.connect();
	        facade.login("homer", "duffs".toCharArray() );
	        String defaultReservation = "event";
	        ClassificationFilter filter = facade.getDynamicType( defaultReservation ).newClassificationFilter();
	        filter.addRule("name",new Object[][] { {"contains","test"}});
	        Reservation reservation = facade.getReservationsForAllocatable( null, null, null, new ClassificationFilter[] {filter} )[0];
	        Appointment[] apps = reservation.getAppointments();
	        Allocatable resource = reservation.getAllocatables()[0];
			Assert.assertEquals("test-att-value", reservation.getClassification().getValue("test-att"));
			Assert.assertEquals(2, apps.length);
			Assert.assertEquals(1, reservation.getAppointmentsFor(resource).length);
	        Appointment app = reservation.getAppointmentsFor( resource )[0];
			Assert.assertEquals(1, app.getRepeating().getExceptions().length);
			Assert.assertEquals(Repeating.DAILY, app.getRepeating().getType());
			Assert.assertEquals(10, app.getRepeating().getNumber());
        }
    }

	@Test
	public void testChangesAddChangeDelete()
	{
		Date startAll = new Date();
		CachableStorageOperator operator = getOperator();
		RaplaFacade facade = getFacade();
		final String resourceId;
		facade.login("homer", "duffs".toCharArray() );
		{// resources
			Date startDate=operator.getCurrentTimestamp();
			Allocatable resource = facade.newResource();
			resourceId = resource.getId();
			final String newValue = "New resource";
			resource.getClassification().setValue("name", newValue);
			facade.store( resource);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertTrue(updates.getIds(UpdateResult.Add.class).contains( resourceId));
			Collection<UpdateResult.HistoryEntry> historyEntries = updates.getUnresolvedHistoryEntry(resourceId);
			Assert.assertEquals(1, historyEntries.size());
			final Entity unresolvedEntity = historyEntries.iterator().next().getUnresolvedEntity();
			Assert.assertTrue(unresolvedEntity instanceof Allocatable);
			final Allocatable allocUnresolved = (Allocatable) unresolvedEntity;
			Assert.assertEquals(newValue, allocUnresolved.getClassification().getValue("name"));
			Assert.assertEquals(1, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertTrue(updates.getIds(UpdateResult.Change.class).isEmpty());
			Assert.assertTrue(updates.getIds(UpdateResult.Remove.class).isEmpty());
		}
		final String reservationId;
		{// Reservation
			Date startDate=operator.getCurrentTimestamp();
			Reservation reservation = facade.newReservation();
			reservationId = reservation.getId();
			final String newValue = "New resource";
			reservation.getClassification().setValue("name", newValue);
			Date appStartDate = new Date();
			Date appEndDate = new Date(appStartDate.getTime() + 120000);
			reservation.addAppointment(facade.newAppointment(appStartDate, appEndDate));
			facade.store( reservation);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertTrue(updates.getIds(UpdateResult.Add.class).contains( reservationId));
			Collection<UpdateResult.HistoryEntry> historyEntries = updates.getUnresolvedHistoryEntry(reservationId);
			Assert.assertEquals(1, historyEntries.size());
			final Entity unresolvedEntity = historyEntries.iterator().next().getUnresolvedEntity();
			Assert.assertTrue(unresolvedEntity instanceof Reservation);
			final Reservation reservationUnresolved = (Reservation) unresolvedEntity;
			Assert.assertEquals(newValue, reservationUnresolved.getClassification().getValue("name"));
			Assert.assertEquals(1, updates.getIds(UpdateResult.Add.class).size());
            Assert.assertTrue(updates.getIds(UpdateResult.Change.class).isEmpty());
			Assert.assertTrue(updates.getIds(UpdateResult.Remove.class).isEmpty());
		}
		final String userId;
		{// user
			Date startDate=operator.getCurrentTimestamp();
			User user = facade.newUser();
			userId = user.getId();
			final String newValue = "New resource";
			user.setName(newValue);
			facade.store( user);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertTrue(updates.getIds(UpdateResult.Add.class).contains( userId));
			Collection<UpdateResult.HistoryEntry> historyEntries = updates.getUnresolvedHistoryEntry(userId);
			Assert.assertEquals(1, historyEntries.size());
			final Entity unresolvedEntity = historyEntries.iterator().next().getUnresolvedEntity();
			Assert.assertTrue(unresolvedEntity instanceof User);
			final User allocUnresolved = (User) unresolvedEntity;
			Assert.assertEquals(newValue, allocUnresolved.getName());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertTrue(updates.getIds(UpdateResult.Add.class).isEmpty());
			Assert.assertTrue(updates.getIds(UpdateResult.Add.class).isEmpty());
		}
		final String categoryId;
		{// Category
			Date startDate=operator.getCurrentTimestamp();
			Category category = facade.newCategory();
			categoryId = category.getId();
			final String newValue = "New resource";
			Category superCategory = facade.edit(facade.getSuperCategory());
			superCategory.addCategory(category);
			facade.store( superCategory);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Change.class).size());
			Collection<UpdateResult.HistoryEntry> historyEntries = updates.getUnresolvedHistoryEntry(superCategory.getId());
			Assert.assertEquals(1, historyEntries.size());
			final Entity unresolvedEntity = historyEntries.iterator().next().getUnresolvedEntity();
			Assert.assertTrue(unresolvedEntity instanceof Category);
			final Category categoryUnresolved = (Category) unresolvedEntity;
			Assert.assertEquals(newValue, categoryUnresolved.hasCategory(category));
			Assert.assertTrue(updates.getIds(UpdateResult.Remove.class).isEmpty());
		}
		{// Preferences

		}
		// restart via disconnect and connect
		{
			operator.disconnect();
			operator.connect();
			final UpdateResult updates = operator.getUpdateResult(startAll);
			// we expect all changes
            Assert.assertEquals(3, updates.getIds(UpdateResult.Add.class).size());
            Assert.assertEquals(1, updates.getIds(UpdateResult.Change.class).size());
            Assert.assertEquals(0, updates.getIds(UpdateResult.Remove.class).size());
		}
		// Start changing
		{// resource
			Date startDate = new Date();
			final Allocatable resource = facade.edit(facade.getOperator().tryResolve(resourceId, Allocatable.class));
			final String newValue = "changedValue";
			final String attributeId = "name";
			resource.getClassification().setValue(attributeId, newValue);
			facade.store(resource);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Change.class).size());
			Assert.assertEquals(0, updates.getIds(UpdateResult.Remove.class).size());
			final String changedId = updates.getIds(UpdateResult.Change.class).iterator().next();
			final Collection<UpdateResult.HistoryEntry> unresolvedHistoryEntry = updates.getUnresolvedHistoryEntry(changedId);
			Assert.assertEquals(1, unresolvedHistoryEntry.size());
			final Entity unresolvedEntity = updates.getLastKnown(changedId);//.getUnresolvedEntity();
			Assert.assertTrue(unresolvedEntity instanceof Allocatable);
			final Allocatable unresolvedAllocatable = (Allocatable) unresolvedEntity;
			Assert.assertEquals(newValue, unresolvedAllocatable.getClassification().getValue(attributeId));
		}
		{// Reservation
			Date startDate = new Date();
			final Reservation reservation = facade.edit(facade.getOperator().tryResolve(reservationId, Reservation.class));
			final String newValue = "changedValue";
			final String attributeId = "name";
			reservation.getClassification().setValue(attributeId, newValue);
			final Date newAppStart = new Date();
			reservation.getAppointments()[0].move(newAppStart);
			facade.store(reservation);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Change.class).size());
			Assert.assertEquals(0, updates.getIds(UpdateResult.Remove.class).size());
			final String changedId = updates.getIds(UpdateResult.Change.class).iterator().next();
			final Collection<UpdateResult.HistoryEntry> unresolvedHistoryEntry = updates.getUnresolvedHistoryEntry(changedId);
			Assert.assertEquals(1, unresolvedHistoryEntry.size());
			final Entity unresolvedEntity = updates.getLastKnown(changedId);//.getUnresolvedEntity();
			Assert.assertTrue(unresolvedEntity instanceof Reservation);
			final Reservation unresolvedReservation = (Reservation) unresolvedEntity;
			Assert.assertEquals(newValue, unresolvedReservation.getClassification().getValue(attributeId));
			Assert.assertEquals(newAppStart, unresolvedReservation.getAppointments()[0].getStart());
		}
		{// User
			Date startDate = new Date();
			final User user = facade.edit(facade.getOperator().tryResolve(userId, User.class));
			final String newValue = "changedValue";
			user.setName(newValue);
			facade.store(user);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Change.class).size());
			Assert.assertEquals(0, updates.getIds(UpdateResult.Remove.class).size());
			final String changedId = updates.getIds(UpdateResult.Change.class).iterator().next();
			final Collection<UpdateResult.HistoryEntry> unresolvedHistoryEntry = updates.getUnresolvedHistoryEntry(changedId);
			Assert.assertEquals(1, unresolvedHistoryEntry.size());
			final Entity unresolvedEntity = updates.getLastKnown(changedId);//.getUnresolvedEntity();
			Assert.assertTrue(unresolvedEntity instanceof User);
			final User unresolvedUser = (User) unresolvedEntity;
			Assert.assertEquals(newValue, unresolvedUser.getName());
		}
		{// Category
			Date startDate = new Date();
			final Category category = facade.edit(facade.getOperator().tryResolve(categoryId, Category.class));
			final String newValue = "changedValue";
			category.setKey(newValue);
			facade.store(category);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Change.class).size());
			Assert.assertEquals(0, updates.getIds(UpdateResult.Remove.class).size());
			final String changedId = updates.getIds(UpdateResult.Change.class).iterator().next();
			final Collection<UpdateResult.HistoryEntry> unresolvedHistoryEntry = updates.getUnresolvedHistoryEntry(changedId);
			Assert.assertEquals(2, unresolvedHistoryEntry.size());
			final Entity unresolvedEntity = updates.getLastKnown(changedId);//.getUnresolvedEntity();
			Assert.assertTrue(unresolvedEntity instanceof Category);
			final Category unresolvedCategory = (Category) unresolvedEntity;
			Assert.assertEquals(newValue, unresolvedCategory.getKey());
		}
		// start deletion
		{// resource
			final Date startDate = new Date();
			final Entity entity = facade.getOperator().tryResolve(resourceId);
			facade.remove(entity);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(0, updates.getIds(UpdateResult.Change.class).size());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Remove.class).size());
            Assert.assertTrue(updates.getIds(UpdateResult.Remove.class).contains(resourceId));
		}
		{// Reservation
			final Date startDate = new Date();
			final Entity entity = facade.getOperator().tryResolve(reservationId);
			facade.remove(entity);
			operator.refresh();
			final UpdateResult updateResult = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updateResult.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(0, updateResult.getIds(UpdateResult.Change.class).size());
			Assert.assertEquals(1, updateResult.getIds(UpdateResult.Remove.class).size());
            Assert.assertTrue(updateResult.getIds(UpdateResult.Remove.class).contains(reservationId));
		}
		{// User
			final Date startDate = new Date();
			final Entity entity = facade.getOperator().tryResolve(userId);
			facade.remove(entity);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(0, updates.getIds(UpdateResult.Change.class).size());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Remove.class).size());
			Assert.assertTrue(updates.getIds(UpdateResult.Remove.class).contains(userId));
		}
		{// Category
			final Date startDate = new Date();
			final Category entity = facade.getOperator().resolve(resourceId, Category.class);
			final Category superCategory = facade.edit(facade.getSuperCategory());
			superCategory.removeCategory(entity);
			facade.store(superCategory);
			operator.refresh();
			final UpdateResult updates = operator.getUpdateResult(startDate);
			Assert.assertEquals(0, updates.getIds(UpdateResult.Add.class).size());
			Assert.assertEquals(1, updates.getIds(UpdateResult.Change.class).size());
			Assert.assertEquals(0, updates.getIds(UpdateResult.Remove.class).size());
			Assert.assertFalse(((Category)updates.getLastKnown(superCategory.getId())).hasCategory(entity));
		}
		final UpdateResult updates = operator.getUpdateResult(startAll);
		Assert.assertTrue(updates.getIds(UpdateResult.Add.class).isEmpty());
		Assert.assertTrue(updates.getIds(UpdateResult.Add.class).isEmpty());
		Assert.assertEquals("Only super category expected to be changed.",1, updates.getIds(UpdateResult.Change.class).size());

	}
}





