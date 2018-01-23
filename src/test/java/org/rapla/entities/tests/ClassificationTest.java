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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.RaplaTestCase;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@RunWith(JUnit4.class)
public class ClassificationTest  {
    Reservation reserv1;
    Reservation reserv2;
    Allocatable allocatable1;
    Allocatable allocatable2;
    Calendar cal;

    RaplaFacade facade;
    User user;

	@Before
    public void setUp() throws Exception {
		final ClientFacade clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        facade = clientFacade.getRaplaFacade();
        user = clientFacade.getUser();
    }

	@Test
    public void testChangeType() throws RaplaException {
    	Category c1 = facade.newCategory();
    	c1.setKey("c1");
    	Category c1a = facade.newCategory();
    	c1a.setKey("c1a");
    	c1.addCategory( c1a );
    	Category c1b = facade.newCategory();
    	c1b.setKey("c1b");
    	c1.addCategory( c1b );
    	Category c2 = facade.newCategory();
    	c2.setKey("c2");
    	Category c2a = facade.newCategory();
    	c2a.setKey("c2a");
    	c2.addCategory( c2a );
    	Category rootC = facade.edit( facade.getSuperCategory() );
    	rootC.addCategory( c1 );
    	rootC.addCategory( c2 );

    	DynamicType type =  facade.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
    	type.setKey("test-type");
    	Attribute a1 = facade.newAttribute(AttributeType.CATEGORY);
    	a1.setKey("test-attribute");
    	a1.setConstraint( ConstraintIds.KEY_ROOT_CATEGORY, c1 );
    	a1.setConstraint( ConstraintIds.KEY_MULTI_SELECT, true);
    	type.addAttribute( a1 );
    	type.getName().setName("en", "test-type");

    	try {
    		facade.store(  type  );
			Assert.fail("Should throw an EntityNotFoundException");
    	} catch (EntityNotFoundException ex) {
    	}
		facade.storeObjects( new Entity[] { rootC, type } );
    	type =  facade.getPersistant( type );

    	Classification classification = type.newClassification();
        classification.setValue("name", "test-resource");
        List<?> asList = Arrays.asList(c1a, c1b);
		classification.setValues(classification.getAttribute("test-attribute"), asList);
		Allocatable resource = facade.newAllocatable(classification, user);
    	facade.storeObjects( new Entity[] {  resource } );
    	{
	        Allocatable persistantResource = facade.getPersistant(resource);
	        Collection<Object> values = persistantResource.getClassification().getValues( classification.getAttribute("test-attribute"));
	        Assert.assertEquals(2, values.size());
	        Iterator<Object> iterator = values.iterator();
			Assert.assertEquals(c1a, iterator.next());
			Assert.assertEquals(c1b, iterator.next());
    	}
    	
    	type = facade.getDynamicType("test-type");
    	type =  facade.edit( type );

    	a1 = type.getAttribute("test-attribute");
    	a1.setConstraint( ConstraintIds.KEY_ROOT_CATEGORY, c2 );
    	facade.store( type );
    	{
	    	Allocatable persistantResource = facade.getPersistant(resource);
	        Classification classification2 = persistantResource.getClassification();
	        Collection<Object> values = classification2.getValues( classification.getAttribute("test-attribute"));
			Assert.assertEquals(0, values.size());
			Object value = classification2.getValue("test-attribute");
			Assert.assertNull(value);
    	}
    }

}





