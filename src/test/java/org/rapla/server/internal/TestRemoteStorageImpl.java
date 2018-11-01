package org.rapla.server.internal;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.AbstractTestWithServer;
import org.rapla.RaplaResources;
import org.rapla.client.internal.DeleteUndo;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.Conflict;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.test.util.RaplaTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(JUnit4.class)
public class TestRemoteStorageImpl extends AbstractTestWithServer
{

    private ClientFacade clientFacade;

    @Before
    public void setUp() throws Exception
    {
        clientFacade = createClientFacade();
        login(clientFacade,"homer", "duffs".toCharArray());
    }

    @Test
    public void testAddCategoriesUnordered() throws Exception
    {
        Collection<Category> toStore = new ArrayList<Category>();
        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        final Category newCategory = raplaFacade.newCategory();
        newCategory.setKey("newCategory");
        final Category superCategory = raplaFacade.edit(raplaFacade.getSuperCategory());
        superCategory.addCategory(newCategory);
        final Category newChild = raplaFacade.newCategory();
        newChild.setKey("newChild");
        newCategory.addCategory(newChild);
        toStore.add(superCategory);
        toStore.add(newChild);
        toStore.add(newCategory);
        raplaFacade.storeAndRemove(toStore.toArray(new Entity[0]), Entity.ENTITY_ARRAY, clientFacade.getUser());
    }

    @Ignore
    @Test
    public void testDeleteCategoryWithParentSend() throws Exception
    {
        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        {// init test data
            Collection<Category> toStore = new ArrayList<Category>();
            final Category newCategory = raplaFacade.newCategory();
            newCategory.setKey("newCategory");
            final Category superCategory = raplaFacade.edit(raplaFacade.getSuperCategory());
            superCategory.addCategory(newCategory);
            final Category newChild = raplaFacade.newCategory();
            newChild.setKey("newChild");
            newCategory.addCategory(newChild);
            toStore.add(superCategory);
            toStore.add(newCategory);
            toStore.add(newChild);
            raplaFacade.storeAndRemove(toStore.toArray(new Entity[0]), Entity.ENTITY_ARRAY, clientFacade.getUser());

        }
        // register mod listener so we can see if cat was deleted
        final Semaphore waitFor = new Semaphore(0);
        final AtomicReference<ModificationEvent> eventRef = new AtomicReference<ModificationEvent>(null);
        clientFacade.addModificationListener(new ModificationListener()
        {
            @Override
            public void dataChanged(ModificationEvent evt) throws RaplaException
            {
                eventRef.set(evt);
                waitFor.release();
            }
        });
        // now delete parent, so category with key newCategory
        final Category parentCat = raplaFacade.edit(raplaFacade.getSuperCategory().getCategory("newCategory"));
        final Category toDelCat = parentCat.getCategory("newChild");
        parentCat.removeCategory(raplaFacade.edit(toDelCat));
        raplaFacade.store(parentCat);
        final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.SECONDS);
        Assert.assertTrue(tryAcquire);
        final ModificationEvent modificationEvent = eventRef.get();
        Assert.assertEquals(parentCat.getId(), modificationEvent.getChanged().iterator().next().getId());
        Assert.assertEquals(toDelCat.getId(), modificationEvent.getRemovedReferences().iterator().next().getId());
    }

    @Test
    public void testDeleteCategoryWithDeletedSend() throws Exception
    {
        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        {// init test data
            Collection<Category> toStore = new ArrayList<Category>();
            final Category newCategory = raplaFacade.newCategory();
            newCategory.setKey("newCategory");
            final Category superCategory = raplaFacade.edit(raplaFacade.getSuperCategory());
            superCategory.addCategory(newCategory);
            final Category newChild = raplaFacade.newCategory();
            newChild.setKey("newChild");
            newCategory.addCategory(newChild);
            toStore.add(superCategory);
            toStore.add(newCategory);
            toStore.add(newChild);
            raplaFacade.storeAndRemove(toStore.toArray(new Entity[0]), Entity.ENTITY_ARRAY, clientFacade.getUser());

        }
        // register mod listener so we can see if cat was deleted
        final Semaphore waitFor = new Semaphore(0);
        final AtomicReference<ModificationEvent> eventRef = new AtomicReference<ModificationEvent>(null);
        clientFacade.addModificationListener(new ModificationListener()
        {
            @Override
            public void dataChanged(ModificationEvent evt) throws RaplaException
            {
                eventRef.set(evt);
                waitFor.release();
            }
        });
        // now delete parent, so category with key newCategory
        final Category parentCat = raplaFacade.edit(raplaFacade.getSuperCategory().getCategory("newCategory"));
        final Category toDelCat = parentCat.getCategory("newChild");
        raplaFacade.remove(toDelCat);
        final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.SECONDS);
        Assert.assertTrue(tryAcquire);
        final ModificationEvent modificationEvent = eventRef.get();
        Assert.assertEquals(parentCat.getId(), modificationEvent.getChanged().iterator().next().getId());
        Assert.assertEquals(toDelCat.getId(), modificationEvent.getRemovedReferences().iterator().next().getId());
    }

    @Test
    public void testInsertChildCategory() throws Exception
    {
        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        Collection<Category> toStore = new ArrayList<Category>();
        final Category newCategory = raplaFacade.newCategory();
        newCategory.setKey("newCategory");
        final Category superCategory = raplaFacade.edit(raplaFacade.getSuperCategory());
        superCategory.addCategory(newCategory);
        toStore.add(newCategory);
        raplaFacade.storeAndRemove(toStore.toArray(new Entity[0]), Entity.ENTITY_ARRAY, clientFacade.getUser());
        // check if we can resolve the new category
        Assert.assertNotNull(raplaFacade.getSuperCategory().getCategory("newCategory"));
    }
    
    @Test
    public void testDeleteUpdateCategory() throws Exception
    {
        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        {// init data
            final Category superCategory = raplaFacade.edit(raplaFacade.getSuperCategory());
            final Category newCategory = raplaFacade.newCategory();
            newCategory.setKey("cat1");
            final Category newCategory2 = raplaFacade.newCategory();
            newCategory2.setKey("cat2");
            final Category newCategory3 = raplaFacade.newCategory();
            newCategory3.setKey("cat3");
            superCategory.addCategory(newCategory);
            newCategory.addCategory(newCategory2);
            newCategory2.addCategory(newCategory3);
            raplaFacade.storeObjects(new Entity[]{newCategory2, newCategory, superCategory, newCategory3});
        }
        {// now delete cat1 and cat2 but also say cat1 has changed as cat2 is no more child of cat1
            final Category superCategory = raplaFacade.edit(raplaFacade.getSuperCategory());
            final Category cat1 = raplaFacade.edit(superCategory.getCategory("cat1"));
            final Category cat2 = raplaFacade.edit(cat1.getCategory("cat2"));
            final Category cat3 = raplaFacade.edit(cat2.getCategory("cat3"));
            superCategory.removeCategory(cat1);
            cat2.removeCategory(cat3);
            RaplaResources i18n = ((FacadeImpl) raplaFacade).getI18n();
            final List<Category> entities = Arrays.asList(new Category[] { cat1, cat3 });
            DeleteUndo<Category> undo = new DeleteUndo<Category>(raplaFacade, i18n, entities,clientFacade.getUser());
            final Logger logger = getLogger();
            SynchronizedCompletablePromise.waitFor(undo.execute(),1000, logger);
            raplaFacade.refresh();
            TestCase.assertNull(raplaFacade.getSuperCategory().getCategory("cat1"));
            SynchronizedCompletablePromise.waitFor(undo.undo(),1000, logger);
            raplaFacade.refresh();
            TestCase.assertNotNull(raplaFacade.getSuperCategory().getCategory("cat1"));
        }
    }

    
    @Test
    public void testGroupConflicts() throws Exception
    {
        final RaplaFacade facade = clientFacade.getRaplaFacade();
        Classification classification = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification();
        User user = clientFacade.getUser();
        Date startDate = DateTools.toDateTime(new Date(System.currentTimeMillis()), new Date(DateTools.toTime(10, 00, 00)));
        Date endDate = DateTools.toDateTime(new Date(System.currentTimeMillis()), new Date(DateTools.toTime(12, 00, 00)));
        {// Store new Reservation with resource
            final Reservation newReservation = facade.newReservation(classification, user);
            final Allocatable montyAllocatable = facade.getOperator().tryResolve("r9b69d90-46a0-41bb-94fa-82079b424c03", Allocatable.class);//facade.getOperator().tryResolve("f92e9a11-c342-4413-a924-81eee17ccf92", Allocatable.class);
            newReservation.addAllocatable(montyAllocatable);
            newReservation.addAppointment(facade.newAppointmentWithUser(startDate, endDate, user));
            facade.store(newReservation);
        }
        // createInfoDialog reservation with group allocatable
        final Reservation newReservation = facade.newReservation(classification, user);
        final Allocatable dozGroupAllocatable = facade.getOperator().tryResolve("f92e9a11-c342-4413-a924-81eee17ccf92", Allocatable.class);//facade.getOperator().tryResolve("r9b69d90-46a0-41bb-94fa-82079b424c03", Allocatable.class);
        newReservation.addAllocatable(dozGroupAllocatable);
        newReservation.addAppointment(facade.newAppointmentWithUser(startDate, endDate, user));
        final Collection<Conflict> conflicts = RaplaTestCase.waitForWithRaplaException(facade.getConflictsForReservation(newReservation), 10000);
        Assert.assertEquals(1, conflicts.size());
    }
    
    @Test
    public void testBelongsToConflicts() throws Exception
    {
        final RaplaFacade facade = clientFacade.getRaplaFacade();
        Classification classification = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification();
        User user = clientFacade.getUser();
        Date startDate = DateTools.toDateTime(new Date(System.currentTimeMillis()), new Date(DateTools.toTime(10, 00, 00)));
        Date endDate = DateTools.toDateTime(new Date(System.currentTimeMillis()), new Date(DateTools.toTime(12, 00, 00)));
        {// Store new Reservation with resource
            final Reservation newReservation = facade.newReservation(classification, user);
            final Allocatable roomA66Allocatable = facade.getOperator().tryResolve("c24ce517-4697-4e52-9917-ec000c84563c", Allocatable.class);
            newReservation.addAllocatable(roomA66Allocatable);
            newReservation.addAppointment(facade.newAppointmentWithUser(startDate, endDate, user));
            facade.store(newReservation);
        }
        // createInfoDialog reservation with group allocatable
        final Reservation newReservation = facade.newReservation(classification, user);
        final Allocatable partRoomAllocatable = facade.getOperator().tryResolve("rdd6b473-7c77-4344-a73d-1f27008341cb", Allocatable.class);
        newReservation.addAllocatable(partRoomAllocatable);
        newReservation.addAppointment(facade.newAppointmentWithUser(startDate, endDate, user));
        final Collection<Conflict> conflicts = RaplaTestCase.waitForWithRaplaException(facade.getConflictsForReservation(newReservation), 10000);
        Assert.assertEquals(1, conflicts.size());
    }

}
