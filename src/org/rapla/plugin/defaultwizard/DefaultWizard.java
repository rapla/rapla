
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
package org.rapla.plugin.defaultwizard;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.MenuElement;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.PermissionContainer;
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
		List<DynamicType> eventTypes = new ArrayList<DynamicType>();
		try {
			DynamicType[] types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            User user = getUser();
			for ( DynamicType type: types)
			{
                if (PermissionContainer.Util.canCreate( type, user))
			    {
			        eventTypes.add( type );
			    }
			}
		} catch (RaplaException e) {
			return null;
		}
		boolean canCreateReservation = eventTypes.size() > 0;
		MenuElement element;
		String newEventText = getString("new_reservation");
        if ( eventTypes.size() == 1)
		{
		    RaplaMenuItem item = new RaplaMenuItem( getId());
            item.setEnabled( canAllocate() && canCreateReservation);
            DynamicType type = eventTypes.get(0);
            String name = type.getName( getLocale());
            if ( newEventText.endsWith( name))
            {
                item.setText(newEventText );
            }
            else
            {
                item.setText(newEventText + " " + name);
            }
            item.setIcon( getIcon("icon.new"));
			item.addActionListener( this);
			
            typeMap.put( item, type);
			element = item;
		}
		else
		{
			RaplaMenu item = new RaplaMenu( getId());
			item.setEnabled( canAllocate() && canCreateReservation);
			item.setText(newEventText);
			item.setIcon( getIcon("icon.new"));
			for ( DynamicType type:eventTypes)
			{
				RaplaMenuItem newItem = new RaplaMenuItem(type.getKey());
				String name = type.getName( getLocale());
                newItem.setText( name);
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
	    	if ( type == null)
	    	{
	    		getLogger().warn("Type not found for " + source + " in map " + typeMap);
	    		return;
	    	}
	        Classification newClassification = type.newClassification();
			Reservation r = getModification().newReservation( newClassification );
	    	Appointment appointment = createAppointment(model);
	        r.addAppointment(appointment);
	        final Collection<Reservation> singletonList = Collections.singletonList( r);
            Collection<Reservation> list = addAllocatables(model, singletonList, getUser());
            Reservation[] array = list.toArray(Reservation.RESERVATION_ARRAY);
            getEditController().edit(array,getMainComponent());
		}
		catch (RaplaException ex)
		{
			showException( ex, getMainComponent());
		}
    }

    public static Collection<Reservation> addAllocatables(CalendarModel model, Collection<Reservation> newReservations, User user) throws RaplaException
    {
        Collection<Allocatable> markedAllocatables = model.getMarkedAllocatables();
        if (markedAllocatables == null || markedAllocatables.size() == 0)
        {
            Collection<Allocatable> allocatables = Arrays.asList(model.getSelectedAllocatables());
            if (allocatables.size() == 1)
            {
                addAlloctables(newReservations, allocatables);
            }
        }
        else
        {
            Collection<Allocatable> allocatables = markedAllocatables;
            addAlloctables(newReservations, allocatables);
        }
        Collection<Reservation> list = new ArrayList<Reservation>();
        for (Reservation reservation : newReservations)
        {
            Reservation cast = reservation;
            User lastChangedBy = cast.getLastChangedBy();
            if (lastChangedBy != null && !lastChangedBy.equals(user))
            {
                throw new RaplaException("Reservation " + cast + " has wrong user " + lastChangedBy);
            }
            list.add(cast);
        }
        return list;
    }
	
    static private void addAlloctables(Collection<Reservation> events, Collection<Allocatable> allocatables)
    {
        for (Reservation event : events)
        {
            for (Allocatable alloc : allocatables)
            {
                event.addAllocatable(alloc);
            }
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




