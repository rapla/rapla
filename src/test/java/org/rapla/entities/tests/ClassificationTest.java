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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.rapla.RaplaTestCase;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationModule;
import org.rapla.facade.QueryModule;
import org.rapla.framework.RaplaException;

public class ClassificationTest extends RaplaTestCase {
    Reservation reserv1;
    Reservation reserv2;
    Allocatable allocatable1;
    Allocatable allocatable2;
    Calendar cal;

    ModificationModule modificationMod;
    QueryModule queryMod;

    public ClassificationTest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();
        ClientFacade facade = getFacade();
        queryMod = facade;
        modificationMod = facade;
    }

    public void testChangeType() throws RaplaException {
    	Category c1 = modificationMod.newCategory();
    	c1.setKey("c1");
    	Category c1a = modificationMod.newCategory();
    	c1a.setKey("c1a");
    	c1.addCategory( c1a );
    	Category c1b = modificationMod.newCategory();
    	c1a.setKey("c1b");
    	c1.addCategory( c1b );
    	Category c2 = modificationMod.newCategory();
    	c2.setKey("c2");
    	Category c2a = modificationMod.newCategory();
    	c2a.setKey("c2a");
    	c2.addCategory( c2a );
    	Category rootC = modificationMod.edit( queryMod.getSuperCategory() );
    	rootC.addCategory( c1 );
    	rootC.addCategory( c2 );

    	DynamicType type =  modificationMod.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
    	type.setKey("test-type");
    	Attribute a1 = modificationMod.newAttribute(AttributeType.CATEGORY);
    	a1.setKey("test-attribute");
    	a1.setConstraint( ConstraintIds.KEY_ROOT_CATEGORY, c1 );
    	a1.setConstraint( ConstraintIds.KEY_MULTI_SELECT, true);
    	type.addAttribute( a1 );
    	type.getName().setName("en", "test-type");

    	try {
    		modificationMod.store(  type  );
    		fail("Should throw an EntityNotFoundException");
    	} catch (EntityNotFoundException ex) {
    	}
		modificationMod.storeObjects( new Entity[] { rootC, type } );
    	type =  modificationMod.getPersistant( type );

    	Classification classification = type.newClassification();
        classification.setValue("name", "test-resource");
        List<?> asList = Arrays.asList(new Category[] {c1a, c1b});
		classification.setValues(classification.getAttribute("test-attribute"), asList);
		Allocatable resource = modificationMod.newAllocatable(classification);
    	modificationMod.storeObjects( new Entity[] {  resource } );
    	{
	        Allocatable persistantResource = modificationMod.getPersistant(resource);
	        Collection<Object> values = persistantResource.getClassification().getValues( classification.getAttribute("test-attribute"));
	        assertEquals( 2, values.size());
	        Iterator<Object> iterator = values.iterator();
			assertEquals( c1a,iterator.next());
	        assertEquals( c1b,iterator.next());
    	}
    	
    	type = queryMod.getDynamicType("test-type");
    	type =  modificationMod.edit( type );

    	a1 = type.getAttribute("test-attribute");
    	a1.setConstraint( ConstraintIds.KEY_ROOT_CATEGORY, c2 );
    	modificationMod.store( type );
    	{
	    	Allocatable persistantResource = modificationMod.getPersistant(resource);
	        Classification classification2 = persistantResource.getClassification();
	        Collection<Object> values = classification2.getValues( classification.getAttribute("test-attribute"));
			assertEquals(0,  values.size());
			Object value = classification2.getValue("test-attribute");
			assertNull( value);
    	}
    }

}





