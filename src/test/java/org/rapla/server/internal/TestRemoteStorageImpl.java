package org.rapla.server.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Provider;

import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.ServletTestBase;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.test.util.RaplaTestCase;

@RunWith(JUnit4.class)
public class TestRemoteStorageImpl
{

    private Server server;

    private ClientFacade clientFacade;

    @Before
    public void setUp() throws Exception
    {
        Logger logger = RaplaTestCase.initLoger();
        int port = 8052;
        ServerContainerContext container = new ServerContainerContext();
        String xmlFile = "testdefault.xml";
        container.addFileDatasource("raplafile",RaplaTestCase.getTestDataFile(xmlFile));
        ServerServiceImpl serverService = (ServerServiceImpl) RaplaTestCase.createServer(logger, container);
        server = ServletTestBase.createServer(serverService, port);
        Provider<ClientFacade> clientFacadeProvider = RaplaTestCase.createFacadeWithRemote(logger, port);
        clientFacade = clientFacadeProvider.get();
        clientFacade.login("homer", "duffs".toCharArray());
    }

    @After
    public void cleanup() throws Exception
    {
        server.stop();
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
        parentCat.removeCategory(toDelCat);
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
            raplaFacade.storeAndRemove(new Entity[]{superCategory, cat1, cat2}, new Entity[]{cat1, cat3});
        }
    }
}
