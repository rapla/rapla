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
package org.rapla.client.swing.gui.tests;

import org.junit.Assert;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;

import java.util.Collection;
import java.util.Collections;

public class DataUpdateTest  {
    RaplaFacade facade;
    Exception error;
    CalendarSelectionModel model;

    protected void setUp() throws Exception {
    }

    protected void tearDown() throws Exception {
    }

    public void testReload() throws Exception{
        error = null;
        User user = facade.getUsers()[0];
        refreshDelayed();
        // So we have to wait for the listener-thread
        Thread.sleep(1500);
        if (error != null)
            throw error;
        Assert.assertTrue("User-list varied during refresh! ", facade.getUsers()[0].equals(user));
    }
    boolean fail;
    
    public void testCalenderModel() throws Exception{
       	DynamicType dynamicType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0];
    	{
			DynamicType mutableType = facade.edit(dynamicType);
    		Attribute newAttribute = facade.newAttribute(AttributeType.STRING);
    		newAttribute.setKey("newkey");
    		mutableType.addAttribute( newAttribute);
    		facade.store( mutableType );
    	}

    	Allocatable newResource = facade.newResource();
    	newResource.setClassification( dynamicType.newClassification());
    	newResource.getClassification().setValue("name", "testdelete");
    	newResource.getClassification().setValue("newkey", "filter");
    	
    	facade.store( newResource );
     	

    	ClassificationFilter filter = dynamicType.newClassificationFilter();
    	filter.addIsRule("newkey", "filter");
    	model.setAllocatableFilter( new ClassificationFilter[] {filter});
    	model.setSelectedObjects( Collections.singletonList( newResource));
        Assert.assertFalse(model.isDefaultResourceTypes());
        Assert.assertTrue(filter.matches(newResource.getClassification()));
    	{
			ClassificationFilter[] allocatableFilter = model.getAllocatableFilter();
            Assert.assertEquals(1, allocatableFilter.length);
            Assert.assertEquals(1, allocatableFilter[0].ruleSize());
    	}
    	{
			DynamicType mutableType = facade.edit(dynamicType);
    		mutableType.removeAttribute( mutableType.getAttribute( "newkey"));
    		facade.storeAndRemove( new Entity[] {mutableType}, new Entity[]{ newResource});
    	}

        Assert.assertFalse(model.isDefaultResourceTypes());
    	Collection<RaplaObject> selectedObjects = model.getSelectedObjects( );
    	int size = selectedObjects.size();
        Assert.assertEquals(0,size);
    	
    	ClassificationFilter[] allocatableFilter = model.getAllocatableFilter();
        Assert.assertEquals(1, allocatableFilter.length);
    	ClassificationFilter  filter1 = allocatableFilter[0];
        Assert.assertEquals(0, filter1.ruleSize());
    }

    private void refreshDelayed() {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    try {
                        facade.refresh();
                    } catch (Exception ex) {
                        error = ex;
                    }
                }
            });
    }

}





