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
package org.rapla.storage.dbsql.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.RaplaTestCase;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.RaplaBootstrapLogger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.tests.AbstractOperatorTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(JUnit4.class)
public class SQLOperatorTest extends AbstractOperatorTest
{

    ClientFacade facade;
    Logger logger;
    org.hsqldb.jdbc.JDBCDataSource datasource;
    
    @Before
    public void setUp() throws SQLException
    {
        logger = RaplaBootstrapLogger.createRaplaLogger();
        datasource = new org.hsqldb.jdbc.JDBCDataSource();
        datasource.setUrl("jdbc:hsqldb:target/test/rapla-hsqldb");
        datasource.setUser("db_user");
        datasource.setPassword("your_pwd");
        String xmlFile = "testdefault.xml";
        facade = RaplaTestCase.createFacadeWithDatasource(logger, datasource, xmlFile);
        CachableStorageOperator operator = getOperator();
        operator.connect();
        ((DBOperator) operator).removeAll();
        operator.disconnect();
        operator.connect();
    }
    
    @Test
    public void testExport() throws Exception
    {

        ImportExportManager conv = ((DBOperator)getOperator()).getImportExportManager();
        conv.doExport();
        {
            CachableStorageOperator operator = getOperator();
            operator.connect();
            operator.getVisibleEntities(null);
            Thread.sleep(1000);
        }
        //
        //       {
        //	       CachableStorageOperator operator = 	context.lookupDeprecated(CachableStorageOperator.class ,"file");
        //
        //	      operator.connect();
        //	      operator.getVisibleEntities( null );
        //	      Thread.sleep( 1000 );
        //       }
    }

    @Override protected ClientFacade getFacade()
    {
        return facade;
    }

    @Test
    /** exposes a bug in 1.1
     * @throws RaplaException */
    public void testPeriodInfitiveEnd() throws RaplaException
    {
        ClientFacade facade = getFacade();
        CachableStorageOperator operator = getOperator();
        facade.login("homer", "duffs".toCharArray());
        Reservation event = facade.newReservation();
        Appointment appointment = facade.newAppointment(new Date(), new Date());
        event.getClassification().setValue("name", "test");
        appointment.setRepeatingEnabled(true);
        appointment.getRepeating().setEnd(null);
        event.addAppointment(appointment);
        facade.store(event);
        operator.refresh();

        Set<Reservation> singleton = Collections.singleton(event);
        final Map<Entity, Entity> persistantMap = operator.getPersistant(singleton);
        Reservation event1 = (Reservation) persistantMap.get(event);
        Repeating repeating = event1.getAppointments()[0].getRepeating();
        Assert.assertNotNull(repeating);
        Assert.assertNull(repeating.getEnd());
        Assert.assertEquals(-1, repeating.getNumber());
    }

    @Test
    public void testPeriodStorage() throws RaplaException
    {
        CachableStorageOperator operator = getOperator();
        ClientFacade facade = getFacade();
        facade.login("homer", "duffs".toCharArray());
        Date start = DateTools.cutDate(new Date());
        Date end = new Date(start.getTime() + DateTools.MILLISECONDS_PER_WEEK);
        Allocatable period = facade.newPeriod();
        Classification c = period.getClassification();
        String name = "TEST PERIOD2";
        c.setValue("name", name);
        c.setValue("start", start);
        c.setValue("end", end);
        facade.store(period);
        operator.refresh();

        //Allocatable period1 = (Allocatable) operator.getPersistant( Collections.singleton( period )).get( period);
        Period[] periods = facade.getPeriods();
        for (Period period1 : periods)
        {
            if (period1.getName(null).equals(name))
            {
                Assert.assertEquals(start, period1.getStart());
                Assert.assertEquals(end, period1.getEnd());
            }
        }
    }

    @Test
    public void testCategoryChange() throws RaplaException
    {
        ClientFacade facade = getFacade();
        CachableStorageOperator operator = getOperator();
        facade.login("homer", "duffs".toCharArray());
        {
            Category category1 = facade.newCategory();
            Category category2 = facade.newCategory();
            category1.setKey("users1");
            category2.setKey("users2");
            Category groups = facade.edit(facade.getUserGroupsCategory());
            groups.addCategory(category1);
            groups.addCategory(category2);
            facade.store(groups);
            Category[] categories = facade.getUserGroupsCategory().getCategories();
            Assert.assertEquals("users1", categories[5].getKey());
            Assert.assertEquals("users2", categories[6].getKey());
            operator.disconnect();
            operator.connect();
            facade.refresh();
        }
        {
            Category[] categories = facade.getUserGroupsCategory().getCategories();
            Assert.assertEquals("users1", categories[5].getKey());
            Assert.assertEquals("users2", categories[6].getKey());
        }

    }

    @Test
    public void testDynamicTypeChange() throws Exception
    {
        ClientFacade facade = getFacade();
        CachableStorageOperator operator = getOperator();
        facade.login("homer", "duffs".toCharArray());
        DynamicType type = facade.edit(facade.getDynamicType("event"));
        String id = type.getId();
        Attribute att = facade.newAttribute(AttributeType.STRING);
        att.setKey("test-att");
        type.addAttribute(att);
        facade.store(type);
        facade.logout();
        printTypeIds();
        operator.disconnect();
        facade.login("homer", "duffs".toCharArray());
        DynamicType typeAfterEdit = facade.getDynamicType("event");
        String idAfterEdit = typeAfterEdit.getId();
        Assert.assertEquals(id, idAfterEdit);
    }

    private void printTypeIds() throws RaplaException, SQLException
    {
        CachableStorageOperator operator = getOperator();
        Connection connection = ((DBOperator) operator).createConnection();
        String sql = "SELECT * from DYNAMIC_TYPE";
        try
        {
            Statement statement = connection.createStatement();
            ResultSet set = statement.executeQuery(sql);
            while (!set.isLast())
            {
                set.next();
                String idString = set.getString("ID");
                String key = set.getString("TYPE_KEY");
                System.out.println("id " + idString + " key " + key);
            }
        }
        catch (SQLException ex)
        {
            throw new RaplaException(ex);
        }
        finally
        {
            connection.close();
        }
    }


    @Test
    public void testUpdateFromDB() throws Exception
    {
        final AtomicReference<ModificationEvent> updateResult = new AtomicReference<ModificationEvent>();
        final Semaphore waitFor = new Semaphore(0);
        ClientFacade readFacade = this.facade;
        readFacade.addModificationListener(new ModificationListener()
        {
            @Override public void dataChanged(ModificationEvent evt) throws RaplaException
            {
                updateResult.set(evt);
                waitFor.release();
            }
        });
        Thread.sleep(500);
        {// create second writeFacade
            String xmlFile = null;
            ClientFacade writeFacade = RaplaTestCase.createFacadeWithDatasource(logger, datasource, xmlFile);
            writeFacade.login("homer", "duffs".toCharArray());
            { // Reservation test with an attribute, appointment and permission
                final Reservation newReservation = writeFacade.newReservation();
                Date endDate = new Date();
                Date startDate = new Date(endDate.getTime() - 120000);
                newReservation.addAppointment(writeFacade.newAppointment(startDate, endDate));
                Permission permission = new PermissionImpl();
                permission.setAccessLevel(Permission.DENIED);
                permission.setUser(writeFacade.getUser());
                Category category = writeFacade.getUserGroupsCategory().getCategories()[0];
                permission.setGroup(category);
                newReservation.getPermissionList().clear();
                newReservation.addPermission(permission);
                final Classification classification = newReservation.getClassification();
                final Attribute attribute = classification.getAttributes()[0];
                final String value = "TestName";
                classification.setValue(attribute, value);
                writeFacade.store(newReservation);
                readFacade.refresh();
                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
                Assert.assertTrue(tryAcquire);
                final ModificationEvent modificationEvent = updateResult.get();
                Assert.assertNotNull(modificationEvent);
                final Set<Entity> addObjects = modificationEvent.getAddObjects();
                Assert.assertEquals(1, addObjects.size());
                final Entity first = addObjects.iterator().next();
                Assert.assertTrue(first instanceof Reservation);
                Reservation newReserv = (Reservation) first;
                final Classification classification2 = newReserv.getClassification();
                final Attribute attribute2 = classification2.getAttributes()[0];
                final Object value2 = classification2.getValue(attribute2);
                Assert.assertEquals(value, value2.toString());
                final Appointment appointment = newReserv.getAppointments()[0];
                Assert.assertEquals(startDate.getTime(), appointment.getStart().getTime());
                Assert.assertEquals(endDate.getTime(), appointment.getEnd().getTime());
                final Collection<Permission> permissionList = newReserv.getPermissionList();
                Assert.assertEquals(1, permissionList.size());
                final Permission loadedPermission = permissionList.iterator().next();
                Assert.assertEquals(writeFacade.getUser().getId(), loadedPermission.getUser().getId());
                Assert.assertEquals(category, loadedPermission.getGroup());
                Assert.assertEquals(Permission.DENIED, loadedPermission.getAccessLevel());
            }
            {// Next we will insert a Category
                final Category newCategory = writeFacade.newCategory();
                String key = "newCat";
                newCategory.setKey(key);
                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
                Assert.assertTrue(tryAcquire);
                writeFacade.store(newCategory);
                // check
                final ModificationEvent modificationEvent = updateResult.get();
                final Set<Entity> addObjects = modificationEvent.getAddObjects();
                Assert.assertEquals(1, addObjects.size());
                final Entity addedObj = addObjects
                .iterator().next();
                Assert.assertTrue(addedObj instanceof Category);
                Category newCat = (Category) addedObj;
                Assert.assertEquals(key, newCat.getKey());
                Assert.assertEquals(newCategory.getId(), newCat.getId());
            }
            {// check update of more entities
                Map<String, String> rename = new HashMap<String, String>();
                final Allocatable[] allocatables = writeFacade.getAllocatables();
                final Random random = new Random();
                for (Allocatable allocatable : allocatables)
                {
                    final String id = allocatable.getId();
                    final Classification classification = allocatable.getClassification();
                    final Attribute attribute = classification.getAttributes()[0];
                    String newValue = "generated " + random.nextInt(100);
                    classification.setValue(attribute, newValue);
                    rename.put(id, newValue);
                }
                writeFacade.storeObjects(allocatables);
                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
                Assert.assertTrue(tryAcquire);
                final ModificationEvent modificationEvent = updateResult.get();
                final Set<Entity> changed = modificationEvent.getChanged();
                Assert.assertEquals(allocatables.length, changed.size());
                final Iterator<Entity> iterator = changed.iterator();
                while(iterator.hasNext())
                {
                    final Entity next = iterator.next();
                    Assert.assertTrue(next instanceof Allocatable);
                    Allocatable alloc = (Allocatable) next;
                    final String renamedFirstAttribute = rename.get(alloc.getId());
                    final Classification classification = alloc.getClassification();
                    final Attribute attribute = classification.getAttributes()[0];
                    final Object value = classification.getValue(attribute);
                    Assert.assertEquals(renamedFirstAttribute, value.toString());
                }
            }
        }
    }

}





