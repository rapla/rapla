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
package org.rapla;

import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationModule;
import org.rapla.facade.QueryModule;
import org.rapla.facade.UserModule;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.weekview.WeekviewPlugin;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.rapla.test.util.DefaultPermissionControllerSupport;
import org.rapla.test.util.RaplaTestCase;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

@RunWith(JUnit4.class)
public class ServerTest
{

    protected ClientFacade facade1;
    protected ClientFacade facade2;
    Locale locale;

    private Server server;
    Logger logger;
    Provider<ClientFacade> clientFacadeProvider;
    protected RaplaLocale raplaLocale;
    protected ServerServiceImpl serverService;


    @Before public void setUp() throws Exception
    {
        logger = RaplaTestCase.initLoger();
        int port = 8052;
        ServerContainerContext container = createContext();
        serverService = (ServerServiceImpl)RaplaTestCase.createServer( logger, container);
        raplaLocale = serverService.getRaplaLocale();
        server = ServletTestBase.createServer( serverService, port);
        clientFacadeProvider = RaplaTestCase.createFacadeWithRemote(logger, port);
        facade1 = clientFacadeProvider.get();
        facade2 = clientFacadeProvider.get();
        facade1.login("homer", "duffs".toCharArray());

        facade2.login("homer", "duffs".toCharArray());
        locale = Locale.getDefault();
    }

    protected ClientFacade getServerFacade()
    {
        return serverService.getFacade();
    }

    protected StorageOperator getServerOperator()
    {
        return serverService.getOperator();
    }

    public RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }

    protected ServerContainerContext createContext() throws Exception
    {
        ServerContainerContext container = new ServerContainerContext();
        String xmlFile = "testdefault.xml";
        container.setFileDatasource(RaplaTestCase.getTestDataFile(xmlFile));
        return container;
    }

    @After public void tearDown() throws Exception
    {
        facade1.logout();
        facade2.logout();
        server.stop();
    }


    @Test
    public void testLoad() throws Exception
    {
        facade1.getAllocatables();
    }

    @Test
    public void testChangeReservation() throws Exception
    {
        Reservation r1 = facade1.newReservation();
        String typeKey = r1.getClassification().getType().getKey();
        r1.getClassification().setValue("name", "test-reservation");
        r1.addAppointment(facade1.newAppointment(facade1.today(), new Date()));
        facade1.store(r1);
        // Wait for the update
        facade2.refresh();

        Reservation r2 = findReservation(facade2, typeKey, "test-reservation");
        Assert.assertEquals(1, r2.getAppointments().length);
        Assert.assertEquals(0, r2.getAllocatables().length);

        // Modify Reservation in first facade
        Reservation r1clone = facade1.edit(r2);
        r1clone.addAllocatable(facade1.getAllocatables()[0]);
        facade1.store(r1clone);
        // Wait for the update
        facade2.refresh();

        // test for modify in second facade
        Reservation persistant = facade1.getPersistant(r2);
        Assert.assertEquals(1, persistant.getAllocatables().length);
        facade2.logout();
    }

    @Test
    public void testChangeDynamicType() throws Exception
    {
        {
            Allocatable allocatable = facade1.getAllocatables()[0];
            Assert.assertEquals(3, allocatable.getClassification().getAttributes().length);
        }
        DynamicType type = facade1.getDynamicType("room");
        Attribute newAttribute;
        {
            newAttribute = facade1.newAttribute(AttributeType.CATEGORY);
            DynamicType typeEdit1 = facade1.edit(type);
            newAttribute.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, facade1.getUserGroupsCategory());
            newAttribute.setKey("test");
            newAttribute.getName().setName("en", "test");
            typeEdit1.addAttribute(newAttribute);
            facade1.store(typeEdit1);
        }

        {
            Allocatable newResource = facade1.newResource();
            newResource.setClassification(type.newClassification());
            newResource.getClassification().setValue("name", "test-resource");
            newResource.getClassification().setValue("test", facade1.getUserGroupsCategory().getCategories()[0]);
            facade1.store(newResource);
        }

        {
            facade2.refresh();
            // Dyn
            DynamicType typeInSecondFacade = facade2.getDynamicType("room");
            Attribute att = typeInSecondFacade.getAttribute("test");
            Assert.assertEquals("test", att.getKey());
            Assert.assertEquals(AttributeType.CATEGORY, att.getType());
            Assert.assertEquals(facade2.getUserGroupsCategory(), att.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY));

            ClassificationFilter filter = typeInSecondFacade.newClassificationFilter();
            filter.addEqualsRule("name", "test-resource");
            Allocatable newResource = facade2.getAllocatables(filter.toArray())[0];
            Classification classification = newResource.getClassification();
            Category userGroup = (Category) classification.getValue("test");
            final Category[] usergroups = facade2.getUserGroupsCategory().getCategories();
            Assert.assertEquals("Category attribute value is not stored", usergroups[0].getKey(), userGroup.getKey());
            facade2.logout();
        }
        {
            Allocatable allocatable = facade1.getAllocatables()[0];
            Assert.assertEquals(4, allocatable.getClassification().getAttributes().length);
        }
        DynamicType typeEdit2 = facade1.edit(type);
        Attribute attributeLater = typeEdit2.getAttribute("test");
        Assert.assertTrue("Attributes identy changed after storing ", attributeLater.equals(newAttribute));
        typeEdit2.removeAttribute(attributeLater);
        facade1.store(typeEdit2);
        {
            Allocatable allocatable = facade1.getAllocatables()[0];
            Assert.assertEquals(facade1.getAllocatables().length, 5);
            Assert.assertEquals(3, allocatable.getClassification().getAttributes().length);
        }
        User user = facade1.newUser();
        user.setUsername("test-user");
        facade1.store(user);

        facade2.login("homer", "duffs".toCharArray());
        removeAnAttribute();
        // Wait for the update
        {
            facade2.login("homer", "duffs".toCharArray());
            facade2.getUser("test-user");
            facade2.logout();
        }
    }

    public void removeAnAttribute() throws Exception
    {
        DynamicType typeEdit3 = facade1.edit(facade1.getDynamicType("room"));
        typeEdit3.removeAttribute(typeEdit3.getAttribute("belongsto"));
        Allocatable allocatable = facade1.getAllocatables()[0];
        Assert.assertEquals("erwin", allocatable.getName(locale));

        Allocatable allocatableClone = facade1.edit(allocatable);
        Assert.assertEquals(3, allocatable.getClassification().getAttributes().length);
        facade1.storeObjects(new Entity[] { allocatableClone, typeEdit3 });
        Assert.assertEquals(5, facade1.getAllocatables().length);
        final Attribute[] attributes = allocatable.getClassification().getAttributes();
        Assert.assertEquals(2, attributes.length);

        // we check if the store affectes the second client.
        facade2.refresh();
        Assert.assertEquals(5, facade2.getAllocatables().length);

        ClassificationFilter filter = facade2.getDynamicType("room").newClassificationFilter();
        filter.addIsRule("name", "erwin");
        {
            Allocatable rAfter = facade2.getAllocatables(filter.toArray())[0];
            final Attribute[] attributes1 = rAfter.getClassification().getAttributes();
            Assert.assertEquals(2, attributes1.length);
        }
        // facade2.getUserFromRequest("test-user");
        // Wait for the update
        facade2.logout();
    }

    private Reservation findReservation(ClientFacade facade, String typeKey, String name) throws RaplaException
    {
        DynamicType reservationType = facade.getDynamicType(typeKey);
        ClassificationFilter filter = reservationType.newClassificationFilter();
        filter.addRule("name", new Object[][] { { "contains", name } });
        Reservation[] reservations = facade.getReservationsForAllocatable(null, null, null, new ClassificationFilter[] { filter });
        if (reservations.length > 0)
            return reservations[0];
        else
            return null;
    }

    @Test
    public void testChangeDynamicType2() throws Exception
    {
        {
            DynamicType type = facade1.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0];
            DynamicType typeEdit3 = facade1.edit(type);
            typeEdit3.removeAttribute(typeEdit3.getAttribute("belongsto"));
            Allocatable[] allocatables = facade1.getAllocatables();
            Allocatable resource1 = allocatables[0];
            Assert.assertEquals("erwin", resource1.getName(locale));
            facade1.store(typeEdit3);
        }
        {
            Allocatable[] allocatables = facade1.getAllocatables();
            Allocatable resource1 = allocatables[0];
            Assert.assertEquals("erwin", resource1.getName(locale));
            Assert.assertEquals(2, resource1.getClassification().getAttributes().length);
        }
    }

    @Test
    public void testRemoveCategory() throws Exception
    {
        Category superCategoryClone = facade1.edit(facade1.getSuperCategory());
        Category department = superCategoryClone.getCategory("department");
        Category powerplant = department.getCategory("springfield-powerplant");
        powerplant.getParent().removeCategory(powerplant);
        try
        {
            facade1.store(superCategoryClone);
            Assert.fail("Dependency Exception should have been thrown");
        }
        catch (DependencyException ex)
        {
        }
    }

    @Test
    public void testChangeLogin() throws RaplaException
    {
        facade2.logout();
        facade2.login("monty", "burns".toCharArray());

        // boolean canChangePassword = facade2.canChangePassword();
        User user = facade2.getUser();
        facade2.changePassword(user, "burns".toCharArray(), "newPassword".toCharArray());
        facade2.logout();
    }

    @Test
    public void testRemoveCategoryBug5() throws Exception
    {
        DynamicType type = facade1.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        String testTypeName = "TestType";
        type.getName().setName("en", testTypeName);
        Attribute att = facade1.newAttribute(AttributeType.CATEGORY);
        att.setKey("testdep");
        {
            Category superCategoryClone = facade1.getSuperCategory();
            Category department = superCategoryClone.getCategory("department");
            att.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, department);
        }
        type.addAttribute(att);
        facade1.store(type);
        Category superCategoryClone = facade1.edit(facade1.getSuperCategory());
        Category department = superCategoryClone.getCategory("department");
        superCategoryClone.removeCategory(department);
        try
        {
            facade1.store(superCategoryClone);
            Assert.fail("Dependency Exception should have been thrown");
        }
        catch (DependencyException ex)
        {
            Collection<String> dependencies = ex.getDependencies();
            Assert.assertTrue("Dependencies doesnt contain " + testTypeName, contains(dependencies, testTypeName));
        }

    }

    private boolean contains(Collection<String> dependencies, String testTypeName)
    {
        for (String dep : dependencies)
        {
            if (dep.contains(testTypeName))
            {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testStoreFilter() throws Exception
    {
        // select from event where name contains 'planting' or name contains
        // 'test';
        DynamicType dynamicType = facade1.getDynamicType("room");
        ClassificationFilter classificationFilter = dynamicType.newClassificationFilter();
        Category channel6 = facade1.getSuperCategory().getCategory("department").getCategory("channel-6");
        Category testdepartment = facade1.getSuperCategory().getCategory("department").getCategory("testdepartment");
        classificationFilter.setRule(0, dynamicType.getAttribute("belongsto"), new Object[][] { { "is", channel6 }, { "is", testdepartment } });
        boolean thrown = false;
        ClassificationFilter[] filter = new ClassificationFilter[] { classificationFilter };
        User user1 = facade1.getUser();
        CalendarSelectionModel calendar = facade1.newCalendarModel(user1);
        calendar.setViewId(WeekviewPlugin.WEEK_VIEW);
        calendar.setAllocatableFilter(filter);
        calendar.setSelectedObjects(Collections.emptyList());
        calendar.setSelectedDate(facade1.today());
        CalendarModelConfiguration conf = ((CalendarModelImpl) calendar).createConfiguration();
        Preferences prefs = facade1.edit(facade1.getPreferences());
        TypedComponentRole<CalendarModelConfiguration> TEST_CONF = new TypedComponentRole<CalendarModelConfiguration>("org.rapla.TestEntry");
        prefs.putEntry(TEST_CONF, conf);
        facade1.store(prefs);

        ClientFacade facade = getServerFacade();
        User user = facade.getUser("homer");
        Preferences storedPrefs = facade.getPreferences(user);
        Assert.assertNotNull(storedPrefs);
        CalendarModelConfiguration storedConf = storedPrefs.getEntry(TEST_CONF);
        Assert.assertNotNull(storedConf);

        ClassificationFilter[] storedFilter = storedConf.getFilter();
        Assert.assertEquals(1, storedFilter.length);
        ClassificationFilter storedClassFilter = storedFilter[0];

        Assert.assertEquals(1, storedClassFilter.ruleSize());

        try
        {
            Category parent = facade1.edit(testdepartment.getParent());
            parent.removeCategory(testdepartment);
            facade1.store(parent);
        }
        catch (DependencyException ex)
        {
            Assert.assertTrue(contains(ex.getDependencies(), prefs.getName(locale)));
            thrown = true;
        }
        Assert.assertTrue("Dependency Exception should have been thrown!", thrown);
    }

    @Test
    public void testCalendarStore() throws Exception
    {
        Date futureDate = new Date(facade1.today().getTime() + DateTools.MILLISECONDS_PER_WEEK * 10);
        Reservation r = facade1.newReservation();
        r.addAppointment(facade1.newAppointment(futureDate, futureDate));
        r.getClassification().setValue("name", "Test");

        facade1.store(r);

        CalendarSelectionModel calendar = facade1.newCalendarModel(facade1.getUser());
        calendar.setViewId(WeekviewPlugin.WEEK_VIEW);
        calendar.setSelectedObjects(Collections.singletonList(r));
        calendar.setSelectedDate(facade1.today());
        calendar.setTitle("test");
        CalendarModelConfiguration conf = ((CalendarModelImpl) calendar).createConfiguration();
        {
            Preferences prefs = facade1.edit(facade1.getPreferences());
            TypedComponentRole<CalendarModelConfiguration> TEST_ENTRY = new TypedComponentRole<CalendarModelConfiguration>("org.rapla.test");
            prefs.putEntry(TEST_ENTRY, conf);
            facade1.store(prefs);
        }
    }

    @Test
    public void testReservationWithExceptionDoesntShow() throws Exception
    {
        {
            facade1.removeObjects(facade1.getReservationsForAllocatable(null, null, null, null));
        }
        Date start = new Date();
        Date end = new Date(start.getTime() + DateTools.MILLISECONDS_PER_HOUR * 2);
        {
            Reservation r = facade1.newReservation();
            r.getClassification().setValue("name", "test-reservation");
            Appointment a = facade1.newAppointment(start, end);
            a.setRepeatingEnabled(true);
            a.getRepeating().setType(Repeating.WEEKLY);
            a.getRepeating().setInterval(2);
            a.getRepeating().setNumber(10);
            r.addAllocatable(facade1.getAllocatables()[0]);
            r.addAppointment(a);
            a.getRepeating().addException(start);
            a.getRepeating().addException(new Date(start.getTime() + DateTools.MILLISECONDS_PER_WEEK));
            facade1.store(r);
            facade1.logout();
        }
        {
            Reservation[] res = facade2.getReservationsForAllocatable(null, start, new Date(start.getTime() + 8 * DateTools.MILLISECONDS_PER_WEEK), null);
            Assert.assertEquals(1, res.length);
            Thread.sleep(100);
            facade2.logout();
        }

    }

    @Test
    public void testChangeGroup() throws Exception
    {
        final PermissionController permissionController = DefaultPermissionControllerSupport.getController(facade1.getOperator());
        User user = facade1.edit(facade1.getUser("monty"));
        Category[] groups = user.getGroupList().toArray(new Category[] {});
        Assert.assertTrue("No groups found!", groups.length > 0);
        Category myGroup = facade1.getUserGroupsCategory().getCategory("my-group");
        Assert.assertTrue(Arrays.asList(groups).contains(myGroup));
        user.removeGroup(myGroup);
        Allocatable testResource = facade2.edit(facade2.getAllocatables()[0]);
        Assert.assertTrue(permissionController.canAllocate(testResource, facade2.getUser("monty"), null, null, null));
        testResource.removePermission(testResource.getPermissionList().iterator().next());
        Permission newPermission = testResource.newPermission();
        newPermission.setGroup(facade1.getUserGroupsCategory().getCategory("my-group"));
        newPermission.setAccessLevel(Permission.READ);
        testResource.addPermission(newPermission);
        Assert.assertFalse(permissionController.canAllocate(testResource, facade2.getUser("monty"), null, null, null));
        Assert.assertTrue(permissionController.canRead(testResource, facade2.getUser("monty")));
        facade1.store(user);
        facade2.refresh();
        Assert.assertFalse(permissionController.canAllocate(testResource, facade2.getUser("monty"), null, null, null));
    }

    @Test
    public void testRemoveAppointment() throws Exception
    {
        Allocatable[] allocatables = facade1.getAllocatables();
        Date start = getRaplaLocale().toRaplaDate(2005, 11, 10);
        Date end = getRaplaLocale().toRaplaDate(2005, 11, 15);
        Reservation r = facade1.newReservation();
        r.getClassification().setValue("name", "newReservation");
        r.addAppointment(facade1.newAppointment(start, end));
        r.addAllocatable(allocatables[0]);
        ClassificationFilter f = r.getClassification().getType().newClassificationFilter();
        f.addEqualsRule("name", "newReservation");
        facade1.store(r);
        r = facade1.getPersistant(r);
        facade1.remove(r);
        Reservation[] allRes = facade1.getReservationsForAllocatable(null, null, null, new ClassificationFilter[] { f });
        Assert.assertEquals(0, allRes.length);
    }

    @Test
    public void testRestrictionsBug7() throws Exception
    {
        Reservation r = facade1.newReservation();
        r.getClassification().setValue("name", "newReservation");
        Appointment app1;
        {
            Date start = getRaplaLocale().toRaplaDate(2005, 11, 10);
            Date end = getRaplaLocale().toRaplaDate(2005, 10, 15);
            app1 = facade1.newAppointment(start, end);
            r.addAppointment(app1);
        }
        Appointment app2;
        {
            Date start = getRaplaLocale().toRaplaDate(2008, 11, 10);
            Date end = getRaplaLocale().toRaplaDate(2008, 11, 15);
            app2 = facade1.newAppointment(start, end);
            r.addAppointment(app2);
        }
        Allocatable allocatable = facade1.getAllocatables()[0];
        r.addAllocatable(allocatable);
        r.setRestriction(allocatable, new Appointment[] { app1, app2 });
        facade1.store(r);
        facade1.logout();
        facade1.login("homer", "duffs".toCharArray());
        ClassificationFilter f = r.getClassification().getType().newClassificationFilter();
        f.addEqualsRule("name", "newReservation");
        Reservation[] allRes = facade1.getReservationsForAllocatable(null, null, null, new ClassificationFilter[] { f });
        Reservation test = allRes[0];
        allocatable = facade1.getAllocatables()[0];
        Appointment[] restrictions = test.getRestriction(allocatable);
        Assert.assertEquals("Restrictions needs to be saved!", 2, restrictions.length);

    }

    @Test
    public void testMultilineTextField() throws Exception
    {

        String reservationName = "bowling";
        {
            ClientFacade facade = getServerFacade();
            String description = getDescriptionOfReservation(facade, reservationName);
            Assert.assertTrue(description.contains("\n"));
        }
        {
            ClientFacade facade = facade1;
            String description = getDescriptionOfReservation(facade, reservationName);
            Assert.assertTrue(description.contains("\n"));
        }
    }

    public String getDescriptionOfReservation(ClientFacade facade, String reservationName) throws RaplaException
    {
        User user = null;
        Date start = null;
        Date end = null;
        ClassificationFilter filter = facade.getDynamicType("event").newClassificationFilter();
        filter.addEqualsRule("name", reservationName);
        Reservation[] reservations = facade.getReservations(user, start, end, filter.toArray());
        Reservation bowling = reservations[0];
        Classification classification = bowling.getClassification();
        Object descriptionValue = classification.getValue("description");
        String description = descriptionValue.toString();
        return description;
    }

    @Test
    public void testRefresh() throws Exception {
        changeInSecondFacade(facade2,"bowling");
        facade1.refresh();
        Reservation resAfter = findReservation(facade1,"bowling");
        Appointment appointment = resAfter.getAppointments()[0];
        Calendar cal = Calendar.getInstance(DateTools.getTimeZone());
        cal.setTime(appointment.getStart());
        Assert.assertEquals(17, cal.get(Calendar.HOUR_OF_DAY));
        Assert.assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK));
        cal.setTime(appointment.getEnd());
        Assert.assertEquals(19, cal.get(Calendar.HOUR_OF_DAY));
        Assert.assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK));
    }

    @Test
    public void testSavePreferences() throws Exception {
        facade2.logout();
         Assert.assertTrue(facade2.login("monty", "burns".toCharArray()));
        Preferences prefs = facade2.edit( facade2.getPreferences() );
        facade2.store( prefs );
        facade2.logout();
    }
    // Make some Changes to the Reservation in another client
    private void changeInSecondFacade(ClientFacade facade2,String name) throws Exception {
        UserModule userMod2 =  facade2;
        QueryModule queryMod2 =  facade2;
        ModificationModule modificationMod2 =  facade2;
        Reservation reservation = findReservation(queryMod2,name);
        Reservation mutableReseravation = modificationMod2.edit(reservation);
        Appointment appointment =  mutableReseravation.getAppointments()[0];

        RaplaLocale loc = getRaplaLocale();
        Calendar cal = loc.createCalendar();
        cal.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);
        Date startTime = loc.toTime( 17,0,0);
        Date startTime1 = loc.toDate(cal.getTime(), startTime);
        Date endTime = loc.toTime( 19,0,0);
        Date endTime1 = loc.toDate(cal.getTime(), endTime);
        appointment.move(startTime1,endTime1);

        modificationMod2.store( mutableReseravation );
        //userMod2.logout();
    }

    private Reservation findReservation(QueryModule queryMod,String name) throws RaplaException {
        Reservation[] reservations = queryMod.getReservationsForAllocatable(null,null,null,null);
        for (int i=0;i<reservations.length;i++) {
            if (reservations[i].getName(locale).equals(name))
                return reservations[i];
        }
        return null;
    }
}
