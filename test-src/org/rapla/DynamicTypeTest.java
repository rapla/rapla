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
import java.util.Date;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.internal.edit.ClassifiableFilterEdit;


public class DynamicTypeTest extends RaplaTestCase {

    public DynamicTypeTest(String name) {
        super(name);
    }

    public void testAttributeChangeSimple() throws Exception
    {
  	  ClientFacade facade = getFacade();
    	String key = "booleantest";
    	{
    		DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
	    	DynamicType type = facade.edit( eventType );
	    	Attribute att = facade.newAttribute(AttributeType.BOOLEAN);
	    	att.getName().setName("en", "test");
			att.setKey(key);
	    	type.addAttribute( att);
	    	facade.store( type);
	    	{
	    		eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
	    		AttributeImpl attributeImpl = (AttributeImpl) eventType.getAttribute(key);
	    		assertNotNull( attributeImpl);
	    	}
    	}
    	
    }
   public void testAttributeChange() throws Exception
    {
	  ClientFacade facade = getFacade();
  	
	  Reservation event = facade.newReservation();
    	event.getClassification().setValue("name", "test");
    	Appointment app = facade.newAppointment( new Date() , DateTools.addDay(new Date()));
    	event.addAppointment( app );
    	
    	DynamicType eventType = event.getClassification().getType();
    	
    	facade.store( event);
    	String key = "booleantest";
    	{
	    	DynamicType type = facade.edit( eventType );
	    	Attribute att = facade.newAttribute(AttributeType.BOOLEAN);
	    	att.getName().setName("en", "test");
			att.setKey(key);
	    	type.addAttribute( att);
	    	facade.store( type);
    	}
    	{
    		Reservation modified = facade.edit( event );
    		modified.getClassification().setValue(key, Boolean.TRUE);
    		facade.store( modified);
    	}
    	{
	    	CalendarSelectionModel model = getClientService().getContext().lookup(CalendarSelectionModel.class);
	    	ClassificationFilter firstFilter = eventType.newClassificationFilter();
	    	assertNotNull( firstFilter);
	    	firstFilter.addRule(key, new Object[][] {{"=",Boolean.TRUE}});
	    	model.setReservationFilter( new ClassificationFilter[] { firstFilter});
	    	model.save("test");
			Thread.sleep(100);
    	}   	
    	{
	    	DynamicType type = facade.edit( eventType );
	    	Attribute att = type.getAttribute(key);
	    	att.setType(AttributeType.CATEGORY);
	    	facade.store( type);
    	}
    	{
    		Thread.sleep(100);
	    	CalendarSelectionModel model =  getClientService().getContext().lookup(CalendarSelectionModel.class);
	    	model.getReservations();
    	}
//    	List<String> errorMessages = RaplaTestLogManager.getErrorMessages();
//    	assertTrue(errorMessages.toString(),errorMessages.size() == 0);       		
    }
  
   
   public void testAttributeRemove() throws Exception
   {
   	ClientFacade facade = getFacade();
   	Allocatable alloc = facade.getAllocatables()[0];
   	DynamicType allocType = alloc.getClassification().getType();
   	
   	String key = "stringtest";
   	{
	    	DynamicType type = facade.edit( allocType );
	    	Attribute att = facade.newAttribute(AttributeType.STRING);
	    	att.getName().setName("en", "test");
			att.setKey(key);
	    	type.addAttribute( att);
	    	facade.store( type);
   	}
   	{
   		Allocatable modified = facade.edit( alloc );
   		modified.getClassification().setValue(key, "t");
   		facade.store( modified);
   	}
   	{
	    	CalendarSelectionModel model = getClientService().getContext().lookup(CalendarSelectionModel.class);
	    	ClassificationFilter firstFilter = allocType.newClassificationFilter();
	    	assertNotNull( firstFilter);
	    	firstFilter.addRule(key, new Object[][] {{"=","t"}});
	    	model.setReservationFilter( new ClassificationFilter[] { firstFilter});
	    	model.save("test");
   	}   	
   	{
	    	DynamicType type = facade.edit( allocType );
	    	Attribute att = type.getAttribute(key);
	    	type.removeAttribute( att);
	    	facade.store( type);
   	}
   	{
	    	CalendarSelectionModel model = getClientService().getContext().lookup(CalendarSelectionModel.class);
	    	model.getReservations();
	    	Thread.sleep(100);
	    	RaplaContext context = getClientService().getContext();
			boolean isResourceOnly = true;
			ClassifiableFilterEdit ui = new ClassifiableFilterEdit( context, isResourceOnly);
			ui.setFilter( model);
   	}
  // 	List<String> errorMessages = RaplaTestLogManager.getErrorMessages();
	//assertTrue(errorMessages.toString(),errorMessages.size() == 0);
   	
   }

}





