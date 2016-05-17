package org.rapla.server.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.AppointmentFormaterImpl;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.server.PromiseSynchroniser;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.test.util.RaplaTestCase;

@RunWith(JUnit4.class)
public class TestUpdateDataManager
{
    private Logger logger;
    private RaplaFacade facade;
    private UpdateDataManager updateManager;
    CachableStorageOperator operator;

    @Before
    public void setUp() throws Exception
    {
        logger = RaplaTestCase.initLoger();
        JDBCDataSource datasource = new org.hsqldb.jdbc.JDBCDataSource();
        datasource.setUrl("jdbc:hsqldb:target/test/rapla-hsqldb");
        datasource.setUser("db_user");
        datasource.setPassword("your_pwd");
        String xmlFile = "testdefault.xml";
                facade = RaplaTestCase.createFacadeWithDatasource(logger, datasource, xmlFile);
//        facade = RaplaTestCase.createFacadeWithFile(logger, xmlFile);
        operator = (CachableStorageOperator) facade.getOperator();
        DefaultBundleManager bundleManager = new DefaultBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        final RaplaLocaleImpl raplaLocale = new RaplaLocaleImpl(bundleManager);
        AppointmentFormater appointmentFormater = new AppointmentFormaterImpl(i18n, raplaLocale);
        SecurityManager securityManager = new SecurityManager(logger, i18n, appointmentFormater, facade);
        updateManager = new UpdateDataManagerImpl(logger, operator, securityManager);
    }

    @Test
    public void testNothingChangeFastUpdate() throws Exception
    {
        final User user = facade.getUser("homer");
        Date lastSynced = new Date();
        final long start = System.currentTimeMillis();
        final UpdateEvent updateEvent = updateManager.createUpdateEvent(user, lastSynced);
        final long end = System.currentTimeMillis();
        Assert.assertTrue(50 > (end - start));
        Assert.assertTrue(updateEvent.getRemoveIds().isEmpty());
        Assert.assertTrue(updateEvent.getStoreObjects().isEmpty());
    }

    @Test
    public void testInsertChangeAndDeleteSimple() throws Exception
    {
        testInsertChangeAndDelete(1, 1);
    }

    @Test
    public void testInsertChangeAndDelete() throws Exception
    {
        testInsertChangeAndDelete(500, 150);
    }

    private void testInsertChangeAndDelete(final int countInsert, final int countDelete) throws RaplaException
    {
        // create second user first
        final User readUser = facade.getUser("monty");
        final User writeUser = facade.getUser("homer");
        // do an init so we don't get the resources and reservations from the test data
        final int startAllocatables;
        boolean createAndRemoveResources = true;
        boolean createAndRemoveEvents = true;
        Date lastSynced = updateManager.createUpdateEvent(readUser, new Date()).getLastValidated();
        final int storedReservations;
        final int maxNumberGeneratedItems = countInsert;
        final int storedAllocatables;
        {// create some Data
            List<Entity> entitiesToStore = new ArrayList<Entity>();
            Allocatable[] allocatables = facade.getAllocatables();
            startAllocatables = allocatables.length;
            if ( createAndRemoveResources)
            {
                for (int i = 0; i < maxNumberGeneratedItems; i++)
                {
                    Classification classification = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0].newClassification();
                    final Allocatable newResource = facade.newAllocatable(classification, writeUser);
                    newResource.getClassification().setValue("name", "newResource" + i);
                    entitiesToStore.add(newResource);
                }
                storedAllocatables = entitiesToStore.size();
                facade.storeAndRemove(entitiesToStore.toArray(new Entity[0]), Entity.ENTITY_ARRAY, writeUser);
                entitiesToStore.clear();
            }
            else
            {
                storedAllocatables = 0;
            }
            allocatables = facade.getAllocatables();
            if ( createAndRemoveEvents)
            {
                for (int i = 0; i < maxNumberGeneratedItems; i++)
                {
                    Classification classification = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification();
                    final Reservation newReservation = facade.newReservation(classification, writeUser);
                    newReservation.getClassification().setValue("name", "newReservation" + i);
                    Date startDate = new Date();
                    // half an hour duration
                    Date endDate = new Date(startDate.getTime() + 30000);
                    newReservation.addAppointment(facade.newAppointment(startDate, endDate, writeUser));
                    // we take always another allocatable as we want no conflicts for now
                    newReservation.addAllocatable(allocatables[startAllocatables + i]);
                    entitiesToStore.add(newReservation);
                }
                storedReservations = entitiesToStore.size();
                facade.storeAndRemove(entitiesToStore.toArray(new Entity[0]), Entity.ENTITY_ARRAY, writeUser);
            }
            else
            {
                storedReservations = 0;
            }
            }
        {
            // check the created entities
            final UpdateResult updateResult = operator.getUpdateResult(lastSynced,readUser);
            Assert.assertEquals(storedAllocatables + storedReservations,updateResult.getOperations(UpdateResult.Add.class).size());

            final UpdateEvent updateEvent = updateManager.createUpdateEvent(readUser, lastSynced);
            lastSynced = updateEvent.getLastValidated();
            final Collection<Entity> storeObjects = updateEvent.getStoreObjects();
            Assert.assertEquals(storedAllocatables , storeObjects.size());
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
                    Assert.fail(
                            "Only allocatables and reservations are created and reservations should not be within the update event but found " + storeObject);
                }
            }
        }
        final int updatedAllocatables;
        final int updatedReservations;

        {// change some reservations and allocatables
            final Allocatable[] allocatables = facade.getAllocatables();
            final int maxChangesPerType = countInsert;
            final ArrayList<Entity> changedEntities = new ArrayList<Entity>();
            if ( createAndRemoveResources)
            {
                for (int i = 0; i < maxChangesPerType; i++)
                {
                    final Allocatable allocatable = facade.edit(allocatables[i + startAllocatables]);
                    allocatable.getClassification().setValue("name", "changedAllocatable" + i);
                    changedEntities.add(allocatable);
                }
                updatedAllocatables = changedEntities.size();
            }
            else
            {
                updatedAllocatables = 0;
            }
            if ( createAndRemoveEvents)
            {
                final Collection<Reservation> reservations = PromiseSynchroniser.waitForWithRaplaException(facade.getReservationsForAllocatable(allocatables, null, null, null), 10000);
                int i = 0;
                for (Iterator<Reservation> it = reservations.iterator(); it.hasNext(); )
                {
                    final Reservation reservation = facade.edit(it.next());
                    reservation.getClassification().setValue("name", "changedReservation" + i);
                    i++;
                    changedEntities.add(reservation);
                }
                updatedReservations = changedEntities.size() - updatedAllocatables;
            }
            else
            {
                updatedReservations = 0;
            }
            facade.storeAndRemove(changedEntities.toArray(new Entity[changedEntities.size()]), Entity.ENTITY_ARRAY, writeUser);
        }
        {
            final UpdateEvent updateEvent = updateManager.createUpdateEvent(readUser, lastSynced);
            lastSynced = updateEvent.getLastValidated();
            final Collection<Entity> storeObjects = updateEvent.getStoreObjects();
            Assert.assertEquals(updatedAllocatables , storeObjects.size());
            for (Entity updatedObject : storeObjects)
            {
                if (updatedObject instanceof Allocatable)
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
            final int maxToDelete = countDelete;
            List<Entity> toDelete = new ArrayList<Entity>();
            Set<ReferenceInfo> removedIds = new HashSet<ReferenceInfo>();
            if ( createAndRemoveResources)
            {
                for (int i = allocatables.length - 1; i > allocatables.length - 1 - maxToDelete; i--)
                {
                    toDelete.add(allocatables[i]);
                    removedIds.add(allocatables[i].getReference());
                }
            }
            final Collection<Reservation> reservations = PromiseSynchroniser.waitForWithRaplaException(facade.getReservationsForAllocatable(toDelete.toArray(new Allocatable[0]), null, null, null), 10000);
            if ( createAndRemoveEvents)
            {
                for ( Reservation event:reservations)
                {
                    toDelete.add(event);
                    removedIds.add( event.getReference());
                }

            }
            facade.storeAndRemove(Entity.ENTITY_ARRAY, toDelete.toArray(new Entity[0]), writeUser);
            final UpdateEvent updateEvent = updateManager.createUpdateEvent(readUser, lastSynced);
            final Collection<ReferenceInfo> removeIds = updateEvent.getRemoveIds();
            Assert.assertFalse(removeIds.isEmpty());
            for (ReferenceInfo removedId : removeIds)
            {
                Assert.assertTrue("found removed id (" + removedId + ") which is not in the list of deleted resources: " + removedIds,
                        removedIds.contains(removedId));
            }
        }
    }
    
    /**
     * Test weather insert and deletion of the same resource in one updateEvent are
     * removed, so not affected by the client.
     */
    @Test
    public void testInsertDeleteInOne() throws Exception
    {
        // create second user first
        Date lastSynced = new Date();
        final User readUser = facade.getUser("monty");
        //facade.logout();
        final User writeUser = facade.getUser("homer");
        final UpdateEvent updateEvent = updateManager.createUpdateEvent(readUser, lastSynced);
        lastSynced = updateEvent.getLastValidated();
        Classification classification = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0].newClassification();
        final Allocatable newResource = facade.newAllocatable(classification, writeUser);
        newResource.getClassification().setValue("name", "newResource");
        facade.storeAndRemove(new Entity[]{newResource}, Entity.ENTITY_ARRAY, writeUser);
        final UpdateEvent updateWithInsert = updateManager.createUpdateEvent(readUser, lastSynced);
        final Date lastValidatedAfterInsert = updateWithInsert.getLastValidated();
        Assert.assertEquals(1, updateWithInsert.getStoreObjects().size());
        Assert.assertEquals(0, updateWithInsert.getRemoveIds().size());
        facade.storeAndRemove(Entity.ENTITY_ARRAY, new Entity[]{newResource}, writeUser);
        final UpdateEvent updateWithInsertAndDelete = updateManager.createUpdateEvent(readUser, lastSynced);
        Assert.assertEquals(0, updateWithInsertAndDelete.getStoreObjects().size());
        Assert.assertEquals(1, updateWithInsertAndDelete.getRemoveIds().size());
        final UpdateEvent updateEventWithRemove = updateManager.createUpdateEvent(readUser, lastValidatedAfterInsert);
        Assert.assertEquals(0, updateEventWithRemove.getStoreObjects().size());
        Assert.assertEquals(1, updateEventWithRemove.getRemoveIds().size());
    }
}
