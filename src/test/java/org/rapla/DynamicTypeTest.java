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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.RaplaDateRenderer;
import org.rapla.client.swing.internal.edit.ClassifiableFilterEdit;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.client.swing.internal.view.InfoFactoryImpl;
import org.rapla.client.swing.internal.view.TreeFactoryImpl;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.test.util.RaplaTestCase;

import java.util.Date;

@RunWith(JUnit4.class)
public class DynamicTypeTest  {


	RaplaFacade facade;
	ClientFacade clientFacade;
	Logger logger;
	CalendarSelectionModel model;
	@Before
	public void setUp() throws RaplaException
	{
		logger = RaplaTestCase.initLoger();
		clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
		facade= clientFacade.getRaplaFacade();
		model  = facade.newCalendarModel( clientFacade.getUser());
	}

	@After
	public void tearDown()
	{
	}

	@Test
    public void testAttributeChangeSimple() throws Exception
    {
    	String key = "booleantest";
    	{
    		DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
	    	DynamicType type = facade.edit( eventType );
	    	Attribute att = facade.newAttribute(AttributeType.BOOLEAN);
	    	att.getName().setName("en", "test");
			att.setKey(key);
	    	type.addAttribute( att);
	    	facade.store(type);
	    	{
	    		eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
	    		AttributeImpl attributeImpl = (AttributeImpl) eventType.getAttribute(key);
	    		Assert.assertNotNull(attributeImpl);
	    	}
    	}
    	
    }

	@Test
	public void testAttributeChange() throws Exception
    {
	  Reservation event = facade.newReservation();
    	event.getClassification().setValue("name", "test");
    	Appointment app = facade.newAppointment( new Date() , DateTools.addDay(new Date()));
    	event.addAppointment( app );
    	
    	DynamicType eventType = event.getClassification().getType();
    	
    	facade.store(event);
    	String key = "booleantest";
    	{
	    	DynamicType type = facade.edit( eventType );
	    	Attribute att = facade.newAttribute(AttributeType.BOOLEAN);
	    	att.getName().setName("en", "test");
			att.setKey(key);
	    	type.addAttribute( att);
	    	facade.store(type);
    	}
    	{
    		Reservation modified = facade.edit( event );
    		modified.getClassification().setValue(key, Boolean.TRUE);
    		facade.store(modified);
    	}
    	{
	    	ClassificationFilter firstFilter = eventType.newClassificationFilter();
	    	Assert.assertNotNull(firstFilter);
	    	firstFilter.addRule(key, new Object[][] {{"=",Boolean.TRUE}});
	    	model.setReservationFilter( new ClassificationFilter[] { firstFilter});
	    	model.save("test");
			Thread.sleep(100);
    	}   	
    	{
	    	DynamicType type = facade.edit( eventType );
	    	Attribute att = type.getAttribute(key);
	    	att.setType(AttributeType.CATEGORY);
	    	facade.store(type);
    	}
    	{
    		Thread.sleep(100);
	    	model.queryReservations(model.getTimeIntervall());
    	}
//    	List<String> errorMessages = RaplaTestLogManager.getErrorMessages();
//    	assertTrue(errorMessages.toString(),errorMessages.size() == 0);       		
    }

	@Test
	public void testAttributeRemove() throws Exception
   {
   	Allocatable alloc = facade.getAllocatables()[0];
   	DynamicType allocType = alloc.getClassification().getType();
   	
   	String key = "stringtest";
   	{
	    	DynamicType type = facade.edit( allocType );
	    	Attribute att = facade.newAttribute(AttributeType.STRING);
	    	att.getName().setName("en", "test");
			att.setKey(key);
	    	type.addAttribute( att);
	    	facade.store(type);
   	}
   	{
   		Allocatable modified = facade.edit( alloc );
   		modified.getClassification().setValue(key, "t");
   		facade.store(modified);
   	}
   	{
	    	ClassificationFilter firstFilter = allocType.newClassificationFilter();
	    	Assert.assertNotNull(firstFilter);
	    	firstFilter.addRule(key, new Object[][] {{"=","t"}});
	    	model.setReservationFilter( new ClassificationFilter[] { firstFilter});
	    	model.save("test");
   	}   	
   	{
	    	DynamicType type = facade.edit( allocType );
	    	Attribute att = type.getAttribute(key);
	    	type.removeAttribute( att);
	    	facade.store(type);
   	}
   	{
            final DefaultBundleManager bundleManager = new DefaultBundleManager();
            RaplaResources i18n = new RaplaResources(bundleManager);
            RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
            AppointmentFormater appointmentFormater = new AppointmentFormaterImpl(i18n, raplaLocale);
            IOInterface ioInterface = new DefaultIO(logger);
            final RaplaImages raplaImages = new RaplaImages(logger);
            FrameControllerList frameList = new FrameControllerList(logger);
            DialogUiFactoryInterface dialogUiFactory = new DialogUiFactory(i18n, raplaImages, bundleManager, frameList, logger);
            InfoFactory infoFactory = new InfoFactoryImpl(clientFacade, i18n, raplaLocale, logger, appointmentFormater, ioInterface, raplaImages, dialogUiFactory);
   	        TreeFactory treeFactory = new TreeFactoryImpl(clientFacade, i18n, raplaLocale, logger, infoFactory, raplaImages);
	    	model.queryReservations(model.getTimeIntervall());
	    	Thread.sleep(100);
			boolean isResourceOnly = true;
	        DateRenderer dateRenderer = new RaplaDateRenderer(facade, i18n, raplaLocale, logger);
            DateFieldFactory dateFieldFactory = new DateFieldFactory(clientFacade, i18n, raplaLocale, logger, dateRenderer, ioInterface);
            BooleanFieldFactory booleanFieldFactory = new BooleanFieldFactory(clientFacade, i18n, raplaLocale, logger);
            TextFieldFactory textFieldFactory = new TextFieldFactory(clientFacade, i18n, raplaLocale, logger, ioInterface);
            LongFieldFactory longFieldFactory = new LongFieldFactory(clientFacade, i18n, raplaLocale, logger, ioInterface);
            ClassifiableFilterEdit ui = new ClassifiableFilterEdit( clientFacade, i18n, raplaLocale, logger, treeFactory, isResourceOnly, raplaImages, dateFieldFactory, dialogUiFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
			ui.setFilter( model);
   	}
  // 	List<String> errorMessages = RaplaTestLogManager.getErrorMessages();
	//assertTrue(errorMessages.toString(),errorMessages.size() == 0);
   	
   }

}





