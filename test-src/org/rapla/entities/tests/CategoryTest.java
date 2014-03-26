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
import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.RaplaTestCase;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationModule;
import org.rapla.facade.QueryModule;
import org.rapla.facade.UpdateModule;
import org.rapla.framework.RaplaException;

public class CategoryTest extends RaplaTestCase {
    CategoryImpl areas;
    ModificationModule modificationMod;
    QueryModule queryMod;
    UpdateModule updateMod;

    public CategoryTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(CategoryTest.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        ClientFacade facade = getFacade();
        queryMod = facade;
        modificationMod = facade;
        updateMod = facade;

        areas = (CategoryImpl) modificationMod.newCategory();
        areas.setKey("areas");
        areas.getName().setName("en","areas");
        Category area51 =  modificationMod.newCategory();
        area51.setKey("51");
        area51.getName().setName("en","area 51");
        Category buildingA =  modificationMod.newCategory();
        buildingA.setKey("A");
        buildingA.getName().setName("en","building A");
        Category floor1 =  modificationMod.newCategory();
        floor1.setKey("1");
        floor1.getName().setName("en","floor 1");

        buildingA.addCategory(floor1);
        area51.addCategory(buildingA);
        areas.addCategory(area51);

    }

    public void testStore2() throws Exception {
        Category superCategory = modificationMod.edit(queryMod.getSuperCategory());
        
        superCategory.addCategory(areas);
        modificationMod.store(superCategory);
        assertTrue(areas.getId() != null);
        Category editObject =  modificationMod.edit(superCategory);
        modificationMod.store(editObject);
        assertTrue("reference to subcategory has changed"
                   ,areas == (CategoryImpl) superCategory.getCategory("areas")
                   );
    }

    
    public void testStore() throws Exception {
        Category superCategory =modificationMod.edit(queryMod.getSuperCategory());
        superCategory.addCategory(areas);
        modificationMod.store(superCategory);
        assertTrue(areas.getId() != null);
        updateMod.refresh();
        Category[] categories = queryMod.getSuperCategory().getCategories();
        for (int i=0;i<categories.length;i++)
            if (categories[i].equals(areas))
                return;
        assertTrue("category not stored!",false);
    }

    public void testStore3() throws Exception {
        Category superCategory = queryMod.getSuperCategory();
        Category department = modificationMod.edit( superCategory.getCategory("department") );
        Category school = department.getCategory("elementary-springfield");
        try {
            department.removeCategory( school);
            modificationMod.store( department );
            fail("No dependency exception thrown");
        } catch (DependencyException ex) {
        }
        school = modificationMod.edit( superCategory.getCategory("department").getCategory("channel-6") );
        modificationMod.store( school );
    }
    
    public void testEditDeleted() throws Exception {
        Category superCategory = queryMod.getSuperCategory();
        Category department = modificationMod.edit( superCategory.getCategory("department") );
        Category subDepartment = department.getCategory("testdepartment");
        department.removeCategory( subDepartment);
        modificationMod.store( department );
        try {
           Category subDepartmentEdit = modificationMod.edit( subDepartment );
           modificationMod.store( subDepartmentEdit );
           fail( "store should throw an exception, when trying to edit a removed entity ");
        } catch ( RaplaException ex) {
        }
    }


    public void testGetParent() {
        Category area51 = areas.getCategory("51");
        Category buildingA = area51.getCategory("A");
        Category floor1 = buildingA.getCategories()[0];
        assertEquals(areas, area51.getParent());
        assertEquals(area51, buildingA.getParent());
        assertEquals(buildingA, floor1.getParent());
    }

    @SuppressWarnings("null")
    public void testPath() throws Exception {
        String path = "category[key='51']/category[key='A']/category[key='1']";
        Category sub =areas.getCategoryFromPath(path);
        assertTrue(sub != null);
        assertTrue(sub.getName().getName("en").equals("floor 1"));
        String path2 = areas.getPathForCategory(sub);
        //      System.out.println(path2);
        assertEquals(path, path2);
    }

    public void testAncestorOf() throws Exception {
        String path = "category[key='51']/category[key='A']/category[key='1']";
        Category sub =areas.getCategoryFromPath(path);
        assertTrue(areas.isAncestorOf(sub));
        assertTrue(!sub.isAncestorOf(areas));
    }
}





