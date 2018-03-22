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
package org.rapla.entities.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.test.util.RaplaTestCase;

import java.util.Collections;
import java.util.Map;

@RunWith(JUnit4.class) public class CategoryTest
{
    CategoryImpl areas;
    Category area51;
    RaplaFacade raplaFacade;
    ClientFacade clientFacade;
    Category buildingA;
    Category floor1;

    @Before public void setUp() throws Exception
    {
        clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        raplaFacade = clientFacade.getRaplaFacade();

        areas = (CategoryImpl) raplaFacade.newCategory();
        areas.setKey("areas");
        areas.getName().setName("en", "areas");
        area51 = raplaFacade.newCategory();
        area51.setKey("51");
        area51.getName().setName("en", "area 51");
        buildingA = raplaFacade.newCategory();
        buildingA.setKey("A");
        buildingA.getName().setName("en", "building A");
        floor1 = raplaFacade.newCategory();
        floor1.setKey("1");
        floor1.getName().setName("en", "floor 1");

        buildingA.addCategory(floor1);
        area51.addCategory(buildingA);
        areas.addCategory(area51);
    }

    @Ignore
    @Test public void testStore2() throws Exception
    {
        Category superCategory = raplaFacade.edit(raplaFacade.getSuperCategory());

        superCategory.addCategory(areas);
        raplaFacade.store(superCategory);
        Assert.assertTrue(areas.getId() != null);
        Category editObject = raplaFacade.edit(superCategory);
        raplaFacade.store(editObject);
        Assert.assertTrue("reference to subcategory has changed", areas == superCategory.getCategory("areas"));
    }

    @Ignore
    @Test public void testStore() throws Exception
    {
        Category superCategory = raplaFacade.edit(raplaFacade.getSuperCategory());
        superCategory.addCategory(areas);
        User user = clientFacade.getUser();
        raplaFacade.storeAndRemove(new Entity[]{superCategory}, new Entity[] {}, user);
        Assert.assertTrue(areas.getId() != null);
        raplaFacade.refresh();
        Category[] categories = raplaFacade.getSuperCategory().getCategories();
        for (int i = 0; i < categories.length; i++)
            if (categories[i].equals(areas))
                return;
        Assert.assertTrue("category not stored!", false);
    }

    @Test public void testStore3() throws Exception
    {
        Category superCategory = raplaFacade.getSuperCategory();
        Category department = raplaFacade.edit(superCategory.getCategory("department"));
        Category school = department.getCategory("elementary-springfield");
        try
        {
            department.removeCategory(raplaFacade.edit(school));
            raplaFacade.store(department);
            Assert.fail("No dependency exception thrown");
        }
        catch (DependencyException ex)
        {
        }
        school = raplaFacade.edit(superCategory.getCategory("department").getCategory("channel-6"));
        raplaFacade.store(school);
    }

    @Test public void testEditDeleted() throws Exception
    {
        Category superCategory = raplaFacade.getSuperCategory();
        Category department = raplaFacade.edit(superCategory.getCategory("department"));
        Category subDepartment = department.getCategory("testdepartment");
        final Category edit2Remove = raplaFacade.edit(subDepartment);
        department.removeCategory(edit2Remove);
        raplaFacade.store(department);
        final Map<Category, Category> persistant = raplaFacade.getPersistantForList(Collections.singleton(edit2Remove));
        Assert.assertEquals(0, persistant.size());
    }

    @Test public void testGetParent()
    {
        Category area51 = areas.getCategory("51");
        Category buildingA = area51.getCategory("A");
        Category floor1 = buildingA.getCategories()[0];
        Assert.assertEquals(areas, area51.getParent());
        Assert.assertEquals(area51, buildingA.getParent());
        Assert.assertEquals(buildingA, floor1.getParent());
    }

    @Test @SuppressWarnings("null") public void testPath() throws Exception
    {
        String path = "category[key='51']/category[key='A']/category[key='1']";
        Category sub = areas.getCategoryFromPath(path);
        Assert.assertTrue(sub != null);
        Assert.assertTrue(sub.getName().getName("en").equals("floor 1"));
        String path2 = areas.getPathForCategory(sub);
        //      System.out.println(path2);
        Assert.assertEquals(path, path2);
    }

    @Test public void testAncestorOf() throws Exception
    {
        String path = "category[key='51']/category[key='A']/category[key='1']";
        Category sub = areas.getCategoryFromPath(path);
        Assert.assertTrue(areas.isAncestorOf(sub));
        Assert.assertTrue(!sub.isAncestorOf(areas));
    }
}





