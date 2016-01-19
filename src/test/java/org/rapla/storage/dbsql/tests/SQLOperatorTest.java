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
import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Add;
import org.rapla.storage.UpdateResult.Change;
import org.rapla.storage.UpdateResult.Remove;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.tests.AbstractOperatorTest;
import org.rapla.test.util.RaplaTestCase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class SQLOperatorTest extends AbstractOperatorTest
{

    RaplaFacade facade;
    Logger logger;
    org.hsqldb.jdbc.JDBCDataSource datasource;
    
    @Before
    public void setUp() throws SQLException
    {
        logger = RaplaTestCase.initLoger();
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

    @Override protected RaplaFacade getFacade()
    {
        return facade;
    }

    @Test
    /** exposes a bug in 1.1
     * @throws RaplaException */
    public void testPeriodInfitiveEnd() throws RaplaException
    {
        RaplaFacade facade = getFacade();
        final User user = facade.getUser("homer");
        CachableStorageOperator operator = getOperator();
        Reservation event = facade.newReservation(facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification(), user);
        Appointment appointment = facade.newAppointment(new Date(), new Date(), user);
        event.getClassification().setValue("name", "test");
        appointment.setRepeatingEnabled(true);
        appointment.getRepeating().setEnd(null);
        event.addAppointment(appointment);
        facade.storeAndRemove(new Entity[]{event}, Entity.ENTITY_ARRAY, user);
        operator.refresh();

        Set<Reservation> singleton = Collections.singleton(event);
        final Map<Entity, Entity> persistantMap = operator.getPersistant(singleton);
        Reservation event1 = (Reservation) persistantMap.get(event);
        final Appointment[] appointments = event1.getAppointments();
        final Appointment appointment1 = appointments[0];
        Repeating repeating = appointment1.getRepeating();
        Assert.assertNotNull(repeating);
        Assert.assertNull(repeating.getEnd());
        Assert.assertEquals(-1, repeating.getNumber());
    }

    @Test
    public void testPeriodStorage() throws RaplaException
    {
        CachableStorageOperator operator = getOperator();
        RaplaFacade facade = getFacade();
        Date start = DateTools.cutDate(new Date());
        Date end = new Date(start.getTime() + DateTools.MILLISECONDS_PER_WEEK);
        final User user = facade.getUser("homer");
        Allocatable period = facade.newPeriod(user);
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
        RaplaFacade facade = getFacade();
        CachableStorageOperator operator = getOperator();
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
        RaplaFacade facade = getFacade();
        CachableStorageOperator operator = getOperator();
        DynamicType type = facade.edit(facade.getDynamicType("event"));
        String id = type.getId();
        Attribute att = facade.newAttribute(AttributeType.STRING);
        att.setKey("test-att");
        type.addAttribute(att);
        facade.store(type);
        printTypeIds();
        operator.disconnect();
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
        RaplaFacade readFacade = this.facade;
        CachableStorageOperator readOperator = (CachableStorageOperator) readFacade.getOperator();
        Thread.sleep(500);
        Date lastUpdated = new Date();
        {// create second writeFacade
            String reservationId = null;
            String xmlFile = null;
            RaplaFacade writeFacade = RaplaTestCase.createFacadeWithDatasource(logger, datasource, xmlFile);
            final User user = writeFacade.getUsers()[0];
            { // Reservation test with an attribute, appointment and permission
                final Reservation newReservation = writeFacade.newReservation(writeFacade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification(), user);
                reservationId = newReservation.getId();
                Date endDate = new Date();
                Date startDate = new Date(endDate.getTime() - 120000);
                final Appointment newAppointment = writeFacade.newAppointment(startDate, endDate, user);
                newAppointment.setRepeatingEnabled(true);
                final Date exceptionDate = DateTools.cutDate(DateTools.addDays(startDate, 7));
                newAppointment.getRepeating().addException(exceptionDate);
                newAppointment.getRepeating().setType(RepeatingType.DAILY);
                final Date repeatingEnd = DateTools.addDays(startDate, 14);
                newAppointment.getRepeating().setEnd(repeatingEnd);
                newReservation.addAppointment(newAppointment);
                Permission permission = newReservation.newPermission();
                permission.setAccessLevel(Permission.DENIED);
                Category category = writeFacade.getUserGroupsCategory().getCategories()[0];
                permission.setGroup(category);
                newReservation.getPermissionList().clear();
                newReservation.addPermission(permission);
                final Classification classification = newReservation.getClassification();
                final Attribute attribute = classification.getAttributes()[0];
                final String value = "TestName";
                classification.setValue(attribute, value);
                writeFacade.storeAndRemove(new Entity[]{newReservation}, Entity.ENTITY_ARRAY, user);
                readFacade.refresh();
//                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                Assert.assertTrue(tryAcquire);
                final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                lastUpdated = updateResult.getUntil();
                Assert.assertNotNull(updateResult);
                Assert.assertEquals(0, updateResult.getOperations(Change.class).size());
                Assert.assertEquals(0, updateResult.getOperations(Remove.class).size());
                final Collection<Add> addObjects = updateResult.getOperations(Add.class);
                Assert.assertEquals(1, addObjects.size());
                final Entity first = updateResult.getLastKnown(addObjects.iterator().next().getCurrentId());
                Assert.assertTrue(first instanceof Reservation);
                Reservation newReserv = (Reservation) first;
                final Classification classification2 = newReserv.getClassification();
                final Attribute attribute2 = classification2.getAttributes()[0];
                final Object value2 = classification2.getValue(attribute2);
                Assert.assertEquals(value, value2.toString());
                final Appointment appointment = newReserv.getAppointments()[0];
                Assert.assertEquals(startDate.getTime(), appointment.getStart().getTime());
                Assert.assertEquals(endDate.getTime(), appointment.getEnd().getTime());
                Assert.assertTrue(appointment.isRepeatingEnabled());
                final Repeating repeating = appointment.getRepeating();
                Assert.assertEquals(RepeatingType.DAILY, repeating.getType());
                Assert.assertEquals(repeatingEnd, repeating.getEnd());
                Assert.assertEquals(exceptionDate, repeating.getExceptions()[0]);
                final Collection<Permission> permissionList = newReserv.getPermissionList();
                Assert.assertEquals(1, permissionList.size());
                final Permission loadedPermission = permissionList.iterator().next();
                Assert.assertEquals(Permission.DENIED, loadedPermission.getAccessLevel());
                Assert.assertEquals(category, loadedPermission.getGroup());
            }
            final String categoryId;
            {// Next we will insert a Category
                final Category newCategory = writeFacade.newCategory();
                categoryId = newCategory.getId();
                final Category editSuperCat = writeFacade.edit(writeFacade.getSuperCategory());
                editSuperCat.addCategory(newCategory);
                String key = "newCat";
                newCategory.setKey(key);
                writeFacade.storeAndRemove(new Entity[]{editSuperCat}, Entity.ENTITY_ARRAY, user);
                readFacade.refresh();
                // check
                final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                lastUpdated = updateResult.getUntil();
                final Collection<Add> addObjects = updateResult.getOperations(Add.class);
                Assert.assertEquals(1, addObjects.size());
                final Entity addedObj = updateResult.getLastKnown(addObjects.iterator().next().getCurrentId());
                Assert.assertTrue(addedObj instanceof Category);
                Category newCat = (Category) addedObj;
                Assert.assertEquals(key, newCat.getKey());
                Assert.assertEquals(newCategory.getId(), newCat.getId());
            }
            {// check update of more entities
                Map<String, String> rename = new HashMap<String, String>();
                final Collection<Allocatable> allocatables = writeFacade.edit(Arrays.asList(writeFacade.getAllocatables()));
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
                writeFacade.storeAndRemove(allocatables.toArray(Entity.ENTITY_ARRAY), Entity.ENTITY_ARRAY, user);
                readFacade.refresh();
//                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                Assert.assertTrue(tryAcquire);
                final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                lastUpdated = updateResult.getUntil();
                final Collection<Change> changed = updateResult.getOperations(Change.class);
                Assert.assertEquals(allocatables.size(), changed.size());
                final Iterator<Change> iterator = changed.iterator();
                while(iterator.hasNext())
                {
                    final Entity next = updateResult.getLastKnown(iterator.next().getCurrentId());
                    Assert.assertTrue(next instanceof Allocatable);
                    Allocatable alloc = (Allocatable) next;
                    final String renamedFirstAttribute = rename.get(alloc.getId());
                    final Classification classification = alloc.getClassification();
                    final Attribute attribute = classification.getAttributes()[0];
                    final Object value = classification.getValue(attribute);
                    Assert.assertEquals(renamedFirstAttribute, value.toString());
                }
            }
            {// AllocationStorage
                Reservation reservationChange = writeFacade.edit(writeFacade.getOperator().resolve(reservationId, Reservation.class));
                final Allocatable[] allocatables = writeFacade.getAllocatables();
                Assert.assertTrue(reservationChange.getAllocatables().length < allocatables.length);
                for (Allocatable allocatable : allocatables)
                {
                    reservationChange.addAllocatable(allocatable);
                }
                writeFacade.storeAndRemove(new Entity[]{reservationChange}, Entity.ENTITY_ARRAY, user);
                readFacade.refresh();
//                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                Assert.assertTrue(tryAcquire);
                final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                lastUpdated = updateResult.getUntil();
                final Collection<Change> changed = updateResult.getOperations(Change.class);
                Assert.assertEquals(1, changed.size());
                final Entity next = updateResult.getLastKnown(changed.iterator().next().getCurrentId());
                Assert.assertTrue(next instanceof Reservation);
                Reservation newReservation = (Reservation) next;
                Assert.assertEquals(allocatables.length, newReservation.getAllocatables().length);
            }
            final ReferenceInfo<User> userID;
            {// UserStorage and UserGroupStorage
                {
                    final User newUser = writeFacade.newUser();
                    userID = newUser.getReference();
                    for (Category cat:newUser.getGroupList()) {newUser.removeGroup( cat);}
                    //newUser.getGroupList().clear();
                    newUser.addGroup(writeFacade.getUserGroupsCategory().getCategories()[0]);
                    newUser.setAdmin(false);
                    String email = "123@456.de";
                    newUser.setEmail(email);
                    final String name = "example";
                    newUser.setName(name);
                    final String username = "userEx";
                    newUser.setUsername(username);
                    writeFacade.storeAndRemove(new Entity[]{newUser}, Entity.ENTITY_ARRAY, user);
                    readFacade.refresh();
//                    final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                    Assert.assertTrue(tryAcquire);
                    final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                    lastUpdated = updateResult.getUntil();
                    final Collection<Add> addObjects = updateResult.getOperations(Add.class);
                    Assert.assertEquals(1, addObjects.size());
                    final Entity next = updateResult.getLastKnown(addObjects.iterator().next().getCurrentId());
                    Assert.assertTrue(next instanceof User);
                    User addedUser = (User) next;
                    Assert.assertEquals(email, addedUser.getEmail());
                    Assert.assertEquals(name, addedUser.getName());
                    Assert.assertEquals(username, addedUser.getUsername());
                    final Category[] categories = writeFacade.getUserGroupsCategory().getCategories();
                    final Collection<Category> groupList = addedUser.getGroupList();
                    Assert.assertEquals(categories[0], groupList.iterator().next());
                }
                {// now change the group
                    final User obj = writeFacade.tryResolve(userID);
                    final User newUser = writeFacade.edit(obj);
                    newUser.removeGroup(writeFacade.getUserGroupsCategory().getCategories()[0]);
                    newUser.addGroup(writeFacade.getUserGroupsCategory().getCategories()[1]);
                    writeFacade.storeAndRemove(new Entity[]{newUser}, Entity.ENTITY_ARRAY, user);
                    readFacade.refresh();
//                    final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                    Assert.assertTrue(tryAcquire);
                    final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                    lastUpdated = updateResult.getUntil();
                    final Collection<Change> changed = updateResult.getOperations(Change.class);
                    Assert.assertEquals(1, changed.size());
                    final Entity next = updateResult.getLastKnown(changed.iterator().next().getCurrentId());
                    Assert.assertTrue(next instanceof User);
                    User changedUser = (User) next;
                    final Category[] categories = writeFacade.getUserGroupsCategory().getCategories();
                    final Collection<Category> groupList = changedUser.getGroupList();
                    Assert.assertEquals(categories[1], groupList.iterator().next());
                }
            }
            {// Delete of an reservation
                final Reservation existingReservaton = writeFacade.getOperator().resolve(reservationId, Reservation.class);
                writeFacade.storeAndRemove(Entity.ENTITY_ARRAY, new Entity[]{existingReservaton}, user);
                readFacade.refresh();
//                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                Assert.assertTrue(tryAcquire);
                final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                lastUpdated = updateResult.getUntil();
                final Collection<Remove> removed = updateResult.getOperations(Remove.class);
                Assert.assertEquals(1, removed.size());
                final ReferenceInfo next = removed.iterator().next().getReference();
                Assert.assertEquals(existingReservaton.getId(), next.getId());
            }
            {// Delete of an resource
                final Allocatable[] allocatables = writeFacade.getAllocatables();
                final Allocatable allocatable = allocatables[0];
                writeFacade.remove(allocatable);
                readFacade.refresh();
//                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                Assert.assertTrue(tryAcquire);
                final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                lastUpdated = updateResult.getUntil();
                final Collection<Remove> removed = updateResult.getOperations(Remove.class);
                Assert.assertEquals(1, removed.size());
                final ReferenceInfo next = removed.iterator().next().getReference();
                Assert.assertEquals(allocatable.getId(), next.getId());
            }
            {// Delete of an user
                final User newUser = writeFacade.edit(writeFacade.tryResolve(userID));
                writeFacade.remove(newUser);
                readFacade.refresh();
//                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                Assert.assertTrue(tryAcquire);
                final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                lastUpdated = updateResult.getUntil();
                final Collection<Remove> removed = updateResult.getOperations(Remove.class);
                Assert.assertEquals(1, removed.size());
                final ReferenceInfo next = removed.iterator().next().getReference();
                Assert.assertEquals(newUser.getId(), next.getId());
            }
            {// Delete of a category
                final Category newCategory = writeFacade.edit(writeFacade.getOperator().tryResolve(categoryId, Category.class));
                writeFacade.remove(newCategory);
                readFacade.refresh();
//                final boolean tryAcquire = waitFor.tryAcquire(3, TimeUnit.MINUTES);
//                Assert.assertTrue(tryAcquire);
                final UpdateResult updateResult = readOperator.getUpdateResult(lastUpdated);
                lastUpdated = updateResult.getUntil();
                final Collection<Remove> removed = updateResult.getOperations(Remove.class);
                Assert.assertEquals(1, removed.size());
                final ReferenceInfo next = removed.iterator().next().getReference();
                Assert.assertEquals(newCategory.getId(), next.getId());
            }
        }
    }

    /* TODO re-think the test and finish it
    @Test
    public void concurrentReadAndUpdate() throws Exception
    {
        RaplaFacade writeFacade = this.facade;
        final String xmlFile = null;
        // create init data
        final DynamicType dynamicType = writeFacade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0];
        List<Entity> storeObjects = new ArrayList<Entity>();
        for(int i = 0; i< 100000; i++)
        {
            final Classification classification = dynamicType.newClassification();
            final User user = writeFacade.getUser("homer");
            final Allocatable allocatable = writeFacade.newAllocatable(classification, user);
            final Attribute attribute = classification.getAttributes()[0];
            classification.setValue(attribute, "generated-alloc-"+i);
            storeObjects.add(allocatable);
        }
        final Allocatable[] storedAllocatables = storeObjects.toArray(new Allocatable[storeObjects.size()]);
        writeFacade.storeObjects(storedAllocatables);
        storeObjects.clear();
        for(int i = 0; i < 30000; i++)
        {
            final Reservation reservation = writeFacade.newReservation();
            final Classification classification = reservation.getClassification();
            final Attribute attribute = classification.getAttributes()[0];
            classification.setValue(attribute, "generated-reserv-" + i);
            Date endDate = new Date();
            Date startDate = new Date(endDate.getTime() - 120000);
            final Appointment newAppointment = writeFacade.newAppointment(startDate, endDate);
            reservation.addAppointment(newAppointment);
            newAppointment.setRepeatingEnabled(true);
            newAppointment.getRepeating().setType(RepeatingType.DAILY);
            newAppointment.getRepeating().setInterval(i);
            reservation.addAllocatable(storedAllocatables[i * 3]);
            reservation.addAllocatable(storedAllocatables[i * 3 + 1]);
            reservation.addAllocatable(storedAllocatables[i * 3 + 2]);
            storeObjects.add(reservation);
        }
        writeFacade.storeObjects(storeObjects.toArray(new Entity[storeObjects.size()]));
        final AtomicReference<RaplaFacade> initReference = new AtomicReference<RaplaFacade>(null);
        final Semaphore semaphore = new Semaphore(0);
        // now lets start init the other facade
        new Thread(new Runnable(){
            public void run() {
                RaplaFacade initFacade = RaplaTestCase.createFacadeWithDatasource(logger, datasource, xmlFile);
                initReference.set(initFacade);
                semaphore.release();
            }
        }).start();
        // and remove an new reservation and its allocations

        
        final boolean tryAcquire = semaphore.tryAcquire(1, TimeUnit.MINUTES);
        Assert.assertTrue(tryAcquire);
    }
    */
    
    /**
     * Test update of the changes table 
     */
    @Test
    public void updateChanges() throws Exception
    {
        // create a reading instance for the new table
        final Connection readConnection = datasource.getConnection();
        Calendar datetimeCal = Calendar.getInstance( TimeZone.getDefault());
        final String select = "SELECT ID, CHANGED_AT, ENTITY_CLASS, TYPE, XML_VALUE, ISDELETE FROM CHANGES WHERE id = ?";
        final User user = facade.getUser("homer");
        {// resources
            final Allocatable newResource = facade.newAllocatable(
                    facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0].newClassification(), user);
            final Classification classification = newResource.getClassification();
            final Attribute attribute = classification.getAttributes()[0];
            classification.setValue(attribute, "newValue");
            facade.storeAndRemove(new Entity[] { newResource }, Entity.ENTITY_ARRAY, user);
            try(final PreparedStatement stmt = readConnection.prepareStatement(select))
            {
                stmt.setString(1, newResource.getId());
                final ResultSet executeQuery = stmt.executeQuery();
                Assert.assertTrue(executeQuery.next());
                final Timestamp lastChangedAt = executeQuery.getTimestamp(2, datetimeCal);
                Assert.assertEquals(newResource.getLastChanged().getTime(), lastChangedAt.getTime());
                final String type = executeQuery.getString(4);
                Assert.assertEquals(RaplaType.getLocalName(newResource), type);
            }
        }
        {// events
            final Reservation newReservation = facade.newReservation(facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification(), user);
            Date startDate = new Date();
            Date endDate = new Date(startDate.getTime() + 120000);
            newReservation.addAppointment(facade.newAppointment(startDate, endDate, user));
            final Classification classification = newReservation.getClassification();
            final Attribute attribute = classification.getAttributes()[0];
            classification.setValue(attribute, "newReservation");
            facade.storeAndRemove(new Entity[] { newReservation }, Entity.ENTITY_ARRAY, user);
            try(final PreparedStatement stmt = readConnection.prepareStatement(select))
            {
                stmt.setString(1, newReservation.getId());
                final ResultSet executeQuery = stmt.executeQuery();
                Assert.assertTrue(executeQuery.next());
                final Timestamp lastChangedAt = executeQuery.getTimestamp(2, datetimeCal);
                Assert.assertEquals(newReservation.getLastChanged().getTime(), lastChangedAt.getTime());
                final String type = executeQuery.getString(4);
                Assert.assertEquals(RaplaType.getLocalName(newReservation), type);
            }
        }
        {// category
            final Category newCategory = facade.newCategory();
            newCategory.setKey("new");
            final Category superCategory = facade.edit(facade.getSuperCategory());
            superCategory.addCategory(newCategory);
            facade.storeAndRemove(new Entity[] { superCategory }, Entity.ENTITY_ARRAY, user);
            // TODO think about other categories...
            try(final PreparedStatement stmt = readConnection.prepareStatement(select))
            {
                stmt.setString(1, newCategory.getId());
                final ResultSet executeQuery = stmt.executeQuery();
                Assert.assertTrue(executeQuery.next());
                final Timestamp lastChangedAt = executeQuery.getTimestamp(2, datetimeCal);
                Assert.assertEquals(newCategory.getLastChanged().getTime(), lastChangedAt.getTime());
                final String type = executeQuery.getString(4);
                Assert.assertEquals(RaplaType.getLocalName(newCategory), type);
            }
        }
        {// user
            final User newUser = facade.newUser();
            newUser.setAdmin(false);
            newUser.setEmail("123@456.789");
            newUser.setName("newUser");
            newUser.setUsername("new");
            facade.storeAndRemove(new Entity[] { newUser }, Entity.ENTITY_ARRAY, user);
            try(final PreparedStatement stmt = readConnection.prepareStatement(select))
            {
                stmt.setString(1, newUser.getId());
                final ResultSet executeQuery = stmt.executeQuery();
                Assert.assertTrue(executeQuery.next());
                final Timestamp lastChangedAt = executeQuery.getTimestamp(2, datetimeCal);
                Assert.assertEquals(newUser.getLastChanged().getTime(), lastChangedAt.getTime());
                final String type = executeQuery.getString(4);
                Assert.assertEquals(RaplaType.getLocalName(newUser), type);
            }
        }
        {// preferences
            
        }
    }
}





