
/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
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
package org.rapla.plugin.tempatewizard;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.MenuElement;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.Template;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.SaveUndo;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.gui.toolkit.MenuScroller;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaMenuItem;

/** This ReservationWizard displays no wizard and directly opens a ReservationEdit Window
*/
public class TemplateWizard extends RaplaGUIComponent implements IdentifiableMenuEntry, ActionListener
{
	Map<Component,Template> componentMap = new HashMap<Component, Template>();
    public TemplateWizard(RaplaContext sm){
        super(sm);
    }
    
    public String getId() {
		return "020_templateWizard";
	}


    public MenuElement getMenuElement() {
    	Map<String, Template> templateMap;
		try {
			templateMap = getQuery().getTemplateMap();
		} catch (RaplaException e) {
			getLogger().error(e.getMessage(), e);
			return null;
		}
    	componentMap.clear();

		boolean canCreateReservation = canCreateReservation();
		MenuElement element;
		if (templateMap.size() == 0)
		{
			return null;
		}
		if ( templateMap.size() == 1)
		{
			RaplaMenuItem item = new RaplaMenuItem( getId());
			item.setEnabled( canAllocate() && canCreateReservation);
			item.setText(getString("new_reservations_from_template"));
			item.setIcon( getIcon("icon.new"));
			item.addActionListener( this);
			Template template = templateMap.values().iterator().next();
			componentMap.put( item, template);
			element = item;
		}
		else
		{
			RaplaMenu item = new RaplaMenu( getId());
			item.setEnabled( canAllocate() && canCreateReservation);
			item.setText(getString("new_reservations_from_template"));
			item.setIcon( getIcon("icon.new"));
			 
			Set<String> templateSet = new TreeSet<String>(templateMap.keySet());
			SortedMap<String, Set<String>> keyGroup = new TreeMap<String, Set<String>>();
			if ( templateSet.size() >  10)
			{
				for ( String string:templateSet)
				{
					if (string.length() == 0)
					{
						continue;
					}
					String firstChar = string.substring( 0,1);
					Set<String> group = keyGroup.get( firstChar);
					if ( group == null)
					{
						group = new TreeSet<String>();
						keyGroup.put( firstChar, group);
					}
					group.add(string);
				}
				SortedMap<String, Set<String>> merged = merge( keyGroup);
				for ( String subMenuName: merged.keySet())
				{
					RaplaMenu subMenu = new RaplaMenu( getId());
					item.setIcon( getIcon("icon.new"));
					subMenu.setText( subMenuName);
					Set<String> set = merged.get( subMenuName);
					int maxItems = 20;
					if ( set.size() >= maxItems)
					{
						int millisToScroll = 40;
						MenuScroller.setScrollerFor( subMenu, maxItems , millisToScroll);
					}
					addTemplates(templateMap, subMenu, set);
					item.add( subMenu);
				}
			}
			else
			{
				addTemplates(templateMap, item, templateSet);
			}
			element = item;
		}
		return element;
	}

	public void addTemplates(Map<String, Template> templateMap, RaplaMenu item,
			Set<String> templateSet) {
		for ( String templateName:templateSet)
		{
			final Template template = templateMap.get( templateName);
			RaplaMenuItem newItem = new RaplaMenuItem(templateName);
			componentMap.put( newItem, template);
			newItem.setText( templateName );
			item.add( newItem);
			newItem.addActionListener( this);
		}
	}
    
    private SortedMap<String, Set<String>> merge(
			SortedMap<String, Set<String>> keyGroup) 
	{
    	SortedMap<String,Set<String>> result = new TreeMap<String, Set<String>>();
    	String beginnChar = null;
    	String currentChar = null;
    	Set<String> currentSet = null;
    	for ( String key: keyGroup.keySet() )
    	{
    		Set<String> set = keyGroup.get( key);
    		if ( currentSet == null)
    		{
    			currentSet = new TreeSet<String>();
    			beginnChar = key;
    			currentChar = key;
    		}
    		if ( !key.equals( currentChar))
    		{
    			if ( set.size() + currentSet.size() > 10)
    			{
    				String storeKey;
    				if ( beginnChar != null && !beginnChar.equals(currentChar))
    				{
    					storeKey = beginnChar + "-" + currentChar;
    				}
    				else
    				{
    					storeKey = currentChar;
    				}
    				result.put( storeKey, currentSet);
    				currentSet = new TreeSet<String>();
        			beginnChar = key;
        			currentChar = key;    				
    			}
    			else
    			{
    				currentChar = key;
    			}
    		}
			currentSet.addAll( set);
    	}
		String storeKey;
		if ( beginnChar != null)
		{
			if ( !beginnChar.equals(currentChar))
			{
				storeKey = beginnChar + "-" + currentChar;
			}
			else
			{
				storeKey = currentChar;
			}
			result.put( storeKey, currentSet);
		}
    	return result;
	}

    
	public void actionPerformed(ActionEvent e) {
		try
		{
			CalendarModel model = getService(CalendarModel.class);
	    	Date beginn = getStartDate( model);
	    	Object source = e.getSource();
	    	Template template = componentMap.get( source);
			Collection<Reservation> reservations = template.getReservations();
			List<Entity<Reservation>> newReservations = copy( reservations, beginn);
			Collection<Allocatable> markedAllocatables = model.getMarkedAllocatables();
			if (markedAllocatables != null )
			{
				for (Entity<Reservation> reservation: newReservations)
				{
					Reservation event = reservation.cast();
					if ( event.getAllocatables().length == 0)
					{
						for ( Allocatable alloc:markedAllocatables)
						{
							if (!event.hasAllocated(alloc))
							{
								event.addAllocatable( alloc);
							}
						}	
					}
				}
			}
			
			if ( newReservations.size() == 1)
			{
				Reservation next = newReservations.iterator().next().cast();
				getReservationController().edit( next);
			}
			else
			{
				Collection<Reservation> list = new ArrayList<Reservation>();
				for ( Entity<Reservation> reservation:newReservations)
				{
					Reservation cast = reservation.cast();
					User lastChangedBy = cast.getLastChangedBy();
					if ( lastChangedBy != null && !lastChangedBy.equals(getUser()))
					{
						throw new RaplaException("Reservation " + cast + " has wrong user " + lastChangedBy);
					}
					list.add( cast);
				}
				
				SaveUndo<Reservation> saveUndo = new SaveUndo<Reservation>(getContext(), list, null);
				getModification().getCommandHistory().storeAndExecute( saveUndo);
			}
			
		}
		catch (RaplaException ex)
		{
			showException( ex, getMainComponent());
		}
    }
    
	
}




