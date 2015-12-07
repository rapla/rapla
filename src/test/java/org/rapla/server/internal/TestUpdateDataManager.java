package org.rapla.server.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.AppointmentFormaterImpl;
import org.rapla.RaplaResources;
import org.rapla.RaplaTestCase;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.permission.DefaultPermissionControllerSupport;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateEvent;

@RunWith(JUnit4.class)
public class TestUpdateDataManager
{
    private Logger logger;
    private ClientFacade facade;
    private UpdateDataManager updateManager;

    @Before
    public void setUp()
    {
        logger = RaplaTestCase.initLoger();
//        JDBCDataSource datasource = new org.hsqldb.jdbc.JDBCDataSource();
//        datasource.setUrl("jdbc:hsqldb:target/test/rapla-hsqldb");
//        datasource.setUser("db_user");
//        datasource.setPassword("your_pwd");
        String xmlFile = "testdefault.xml";
//        facade = RaplaTestCase.createFacadeWithDatasource(logger, datasource, xmlFile);
        facade = RaplaTestCase.createFacadeWithFile(logger, xmlFile);
        CachableStorageOperator operator = (CachableStorageOperator) facade.getOperator();
        operator.connect();
//        ((DBOperator) operator).removeAll();
//        operator.disconnect();
//        operator.connect();
        DefaultBundleManager bundleManager = new DefaultBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        final RaplaLocaleImpl raplaLocale = new RaplaLocaleImpl(bundleManager);
        AppointmentFormater appointmentFormater = new AppointmentFormaterImpl(i18n, raplaLocale);
        PermissionController permissionController = DefaultPermissionControllerSupport.getController();
        SecurityManager securityManager = new SecurityManager(logger, i18n, appointmentFormater, facade, permissionController);
        updateManager = new UpdateDataManagerImpl(logger, facade, operator, securityManager, permissionController);
    }

    @Test
    public void testNothingChangeFastUpdate()
    {
        Assert.assertTrue(facade.login("homer", "duffs".toCharArray()));
        final User user = facade.getUser();
        Date lastSynced = new Date();
        final long start = System.currentTimeMillis();
        final UpdateEvent updateEvent = updateManager.createUpdateEvent(user, lastSynced);
        final long end = System.currentTimeMillis();
        Assert.assertTrue(50 > (end - start));
        Assert.assertTrue(updateEvent.getRemoveIds().isEmpty());
        Assert.assertTrue(updateEvent.getStoreObjects().isEmpty());
    }

    @Test
    public void testInsertChangeAndDelete()
    {
        // create second user first
        Assert.assertTrue(facade.login("monty", "burns".toCharArray()));
        final User readUser = facade.getUser();
        facade.logout();
        Assert.assertTrue(facade.login("homer", "duffs".toCharArray()));
        final User writeUser = facade.getUser();
        // do an init so we don't get the resources and reservations from the test data
        Date lastSynced = updateManager.createUpdateEvent(readUser, new Date()).getLastValidated();
        final int storedReservations;
        final int storedAllocatables;
        {// create some Data
            List<Entity> entitiesToStore = new ArrayList<Entity>();
            Allocatable[] allocatables = facade.getAllocatables();
            final int maxNumberGeneratedItems = 500;
            for (int i = allocatables.length; i < maxNumberGeneratedItems; i++)
            {
                final Allocatable newResource = facade.newResource();
                newResource.getClassification().setValue("name", "newResource" + i);
                entitiesToStore.add(newResource);
            }
            storedAllocatables = entitiesToStore.size();
            facade.storeObjects(entitiesToStore.toArray(new Entity[0]));
            entitiesToStore.clear();
            allocatables = facade.getAllocatables();
            for (int i = 0; i < maxNumberGeneratedItems; i++)
            {
                final Reservation newReservation = facade.newReservation();
                newReservation.getClassification().setValue("name", "newReservation" + i);
                Date startDate = new Date();
                // half an hour duration
                Date endDate = new Date(startDate.getTime() + 30000);
                newReservation.addAppointment(facade.newAppointment(startDate, endDate));
                // we take always another allocatable as we want no conflicts for now
                newReservation.addAllocatable(allocatables[i]);
                entitiesToStore.add(newReservation);
            }
            storedReservations = entitiesToStore.size();
            facade.storeObjects(entitiesToStore.toArray(new Entity[0]));
        }
        {
            // check the created entities
            final UpdateEvent updateEvent = updateManager.createUpdateEvent(readUser, lastSynced);
            lastSynced = updateEvent.getLastValidated();
            final Collection<Entity> storeObjects = updateEvent.getStoreObjects();
//            Assert.assertEquals(storedAllocatables + storedReservations, storeObjects.size());
            Assert.assertEquals(storeObjects.toString(), storedAllocatables, storeObjects.size());
            for (Entity<?> storeObject : storeObjects)
            {
                if (storeObject instanceof Allocatable)
                {
                    final Object value = ((Allocatable) storeObject).getClassification().getValue("name");
                    Assert.assertTrue("name should start with newResource but found " + value, value.toString().startsWith("newResource"));
                }
//                else if (storeObject instanceof Reservation)
//                {
//                    final Object value = ((Reservation) storeObject).getClassification().getValue("name");
//                    Assert.assertTrue("name should start with newReservation but found " + value, value.toString().startsWith("newReservation"));
//                    Assert.fail("Only allocatables and reservations are created but found " + storeObject);
//                }
                else
                {
                    Assert.fail("Only allocatables and reservations are created and reservations should not be within the update event but found " + storeObject);
                }
            }
        }
        final int updatedAllocatables;
        final int updatedReservations;
        {// change some reservations and allocatables
            final Allocatable[] allocatables = facade.getAllocatables();
            final int maxChangesPerType = 100;
            final ArrayList<Entity> changedEntities = new ArrayList<Entity>();
            for(int i = 0; i < maxChangesPerType; i++)
            {
                final Allocatable allocatable = facade.edit(allocatables[i+50]);
                allocatable.getClassification().setValue("name", "changedAllocatable"+i);
                changedEntities.add(allocatable);
            }
            updatedAllocatables = changedEntities.size();
            final Reservation[] reservations = facade.getReservations(allocatables, null, null);
            for(int i = 0; i < maxChangesPerType; i++)
            {
                final Reservation reservation = facade.edit(reservations[i]);
                reservation.getClassification().setValue("name", "changedReservation"+i);
                changedEntities.add(reservation);
            }
            updatedReservations = changedEntities.size() - updatedAllocatables;
            facade.storeObjects(changedEntities.toArray(new Entity[changedEntities.size()]));
        }
        {
            final UpdateEvent updateEvent = updateManager.createUpdateEvent(readUser, lastSynced);
            lastSynced = updateEvent.getLastValidated();
            final Collection<Entity> storeObjects = updateEvent.getStoreObjects();
            Assert.assertEquals(updatedAllocatables /*+ updatedReservations*/, storeObjects.size());
            for (Entity updatedObject : storeObjects)
            {
                if(updatedObject instanceof Allocatable)
                {
                    final Object value = ((Allocatable) updatedObject).getClassification().getValue("name");
                    Assert.assertTrue("name should start with changedAllocatable but found " + value, value.toString().startsWith("changedAllocatable"));
                }
//                else if (updatedObject instanceof Reservation)
//                {
//                    final Object value = ((Reservation) updatedObject).getClassification().getValue("name");
//                    Assert.assertTrue("name should start with changedReservation but found " + value, value.toString().startsWith("changedReservation"));
//                }
                else
                {
                    Assert.fail("Only Allocatables and Reservations should been changed, but found " + updatedObject);
                }
            }
        }
        {
            final Allocatable[] allocatables = facade.getAllocatables();
            final int maxToDelete = 150;
            List<Entity> toDelete = new ArrayList<Entity>();
            Set<String> removedIds = new HashSet<String>();
            for(int i = allocatables.length - 1; i > allocatables.length-1-maxToDelete; i--)
            {
                toDelete.add(allocatables[i]);
                removedIds.add(allocatables[i].getId());
            }
            final Reservation[] reservations = facade.getReservations(toDelete.toArray(new Allocatable[0]), null, null);
            toDelete.addAll(0, Arrays.asList(reservations));
            facade.removeObjects(toDelete.toArray(new Entity[0]));
            final UpdateEvent updateEvent = updateManager.createUpdateEvent(readUser, lastSynced);
            final Collection<String> removeIds = updateEvent.getRemoveIds();
            for (String removedId : removeIds)
            {
                Assert.assertTrue("found removed id (" + removedId + ") which is not in the list of deleted resources: " + removedIds,
                        removedIds.contains(removeIds));
            }
        }
    }

    @Test
    public void testConflictUpdate()
    {
        // FIXME add a test for conflicts (create, change and delete)
        Assert.fail("Not implemented yet");
    }
    
    @Test
    public void testAllocationChange()
    {
        // FIXME add a test for conflicts (create, change and delete)
        Assert.fail("Not implemented yet");
    }

}
