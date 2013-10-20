
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.plugin.defaultwizard;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.swing.MenuElement;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaMenuItem;

/** This ReservationWizard displays no wizard and directly opens a ReservationEdit Window
*/
public class DefaultWizard extends RaplaGUIComponent implements IdentifiableMenuEntry, ActionListener 
{
	Map<Component,DynamicType> typeMap = new HashMap<Component, DynamicType>();
	    
	public DefaultWizard(RaplaContext sm){
        super(sm);
    }
    
    public String getId() {
		return "000_defaultWizard";
	}

 	public MenuElement getMenuElement() {
 		typeMap.clear();
		DynamicType[] eventTypes;
		try {
			eventTypes = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
		} catch (RaplaException e) {
			return null;
		}
		boolean canCreateReservation = canCreateReservation();
		MenuElement element;
		if ( eventTypes.length == 1)
		{
			RaplaMenuItem item = new RaplaMenuItem( getId());
			item.setEnabled( canAllocate() && canCreateReservation);
			item.setText(getString("new_reservation"));
			item.setIcon( getIcon("icon.new"));item.addActionListener( this);
			typeMap.put( item, eventTypes[0]);
			element = item;
		}
		else
		{
			RaplaMenu item = new RaplaMenu( getId());
			item.setEnabled( canAllocate() && canCreateReservation);
			item.setText(getString("new_reservation"));
			item.setIcon( getIcon("icon.new"));
			for ( DynamicType type:eventTypes)
			{
				RaplaMenuItem newItem = new RaplaMenuItem(type.getElementKey());
				newItem.setText( type.getName( getLocale()));
				item.add( newItem);
				newItem.addActionListener( this);
				typeMap.put( newItem, type);
			}
			element = item;
		}
		return element;
	}
    

	public void actionPerformed(ActionEvent e) {
		try
		{
			CalendarModel model = getService(CalendarModel.class);
	    	Object source = e.getSource();
			DynamicType type = typeMap.get( source);
	    	
	        Classification newClassification = type.newClassification();
			Reservation r = getModification().newReservation( newClassification );
			Collection<Allocatable> markedAllocatables = model.getMarkedAllocatables();
	    	Appointment appointment = createAppointment(model);
	        r.addAppointment(appointment);
	        if ( markedAllocatables == null || markedAllocatables.size() == 0)
	        {
		        Allocatable[] allocatables = model.getSelectedAllocatables();
		        if ( allocatables.length == 1)
		        {
		            r.addAllocatable( allocatables[0]);
		        }
	        }
	        else
	        {
	        	for ( Allocatable alloc: markedAllocatables)
	        	{
	        		r.addAllocatable( alloc);
	        	}
	        }
	        getReservationController().edit( r );
		}
		catch (RaplaException ex)
		{
			showException( ex, getMainComponent());
		}
    }

	protected Appointment createAppointment(CalendarModel model)
			throws RaplaException {
		
		Date startDate = getStartDate(model);
        Date endDate = getEndDate( model, startDate);
        Appointment appointment =  getModification().newAppointment(startDate, endDate);
		return appointment;
	}

//	/**
//	 * @param model
//	 * @param startDate
//	 * @return
//	 */
//	protected Date getEndDate( CalendarModel model,Date startDate) {
//		Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
//		Date endDate = null;
//    	if ( markedIntervals.size() > 0)
//    	{
//    		TimeInterval first = markedIntervals.iterator().next();
//    		endDate = first.getEnd();
//    	}
//    	if ( endDate != null)
//    	{
//    		return endDate;
//    	}
//		return new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
//	}
//
//	protected Date getStartDate(CalendarModel model) {
//		Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
//		Date startDate = null;
//    	if ( markedIntervals.size() > 0)
//    	{
//    		TimeInterval first = markedIntervals.iterator().next();
//    		startDate = first.getStart();
//    	}
//    	if ( startDate != null)
//    	{
//    		return startDate;
//    	}
//    	
//		
//		Date selectedDate = model.getSelectedDate();
//		if ( selectedDate == null)
//		{
//			selectedDate = getQuery().today();
//		}
//		Date time = new Date (DateTools.MILLISECONDS_PER_MINUTE * getCalendarOptions().getWorktimeStartMinutes());
//		startDate = getRaplaLocale().toDate(selectedDate,time);
//		return startDate;
//	}

	
    
	



}




