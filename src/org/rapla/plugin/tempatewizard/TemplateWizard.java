
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.MenuElement;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.gui.toolkit.MenuScroller;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaMenuItem;

/** This ReservationWizard displays no wizard and directly opens a ReservationEdit Window
*/
public class TemplateWizard extends RaplaGUIComponent implements IdentifiableMenuEntry, ActionListener, ModificationListener
{
	Collection<Allocatable> templateNames;
    public TemplateWizard(RaplaContext context) throws RaplaException{
        super(context);
        getUpdateModule().addModificationListener( this);
        templateNames = updateTemplateNames();
    }
    
    public String getId() {
		return "020_templateWizard";
	}

    @Override
    public void dataChanged(ModificationEvent evt) throws RaplaException {
        if ( evt.getInvalidateInterval() != null)
        {
            templateNames = updateTemplateNames();
        }
    }

    private Collection<Allocatable> updateTemplateNames() throws RaplaException {
       
        List<Allocatable> templates = new ArrayList<Allocatable>();
        User user = getUser();
        for (Allocatable template:getQuery().getTemplates())
        {
            if ( user.isAdmin())
            {
                template.getPermissionList();
            }
            templates.add( template);
        }
        return templates;
    }

    class TemplateMenuItem extends RaplaMenuItem
    {
        Allocatable template;

        public TemplateMenuItem(String id, Allocatable template) {
            super(id);
            this.template = template;
        }

        public Allocatable getTemplate() {
            return template;
        }
    }
    public MenuElement getMenuElement() {
		boolean canCreateReservation = canCreateReservation();
		MenuElement element;
		if (templateNames.size() == 0)
		{
			return null;
		}
		if ( templateNames.size() == 1)
		{
			Allocatable template = templateNames.iterator().next();
			RaplaMenuItem item = new TemplateMenuItem( getId(), template);
            item.setEnabled( canAllocate() && canCreateReservation);
            item.setText(getString("new_reservations_from_template"));
            item.setIcon( getIcon("icon.new"));
            item.addActionListener( this);
			element = item;
		}
		else
		{
			RaplaMenu item = new RaplaMenu( getId());
			item.setEnabled( canAllocate() && canCreateReservation);
			item.setText(getString("new_reservations_from_template"));
			item.setIcon( getIcon("icon.new"));
			@SuppressWarnings("unchecked")
            Comparator<String> collator = (Comparator<String>) (Comparator)Collator.getInstance(getRaplaLocale().getLocale());
			Map<String,Collection<Allocatable>> templateMap = new HashMap<String,Collection<Allocatable>>();
			
			Set<String> templateSet = new TreeSet<String>(collator);
			Locale locale = getLocale();
			for ( Allocatable template:templateNames)
			{
			    String name = template.getName( locale);
                templateSet.add( name );
			    Collection<Allocatable> collection = templateMap.get( name);
			    if ( collection == null)
			    {
			        collection = new ArrayList<Allocatable>();
			        templateMap.put( name, collection);
			    }
			    collection.add( template);
			}
			
			SortedMap<String, Set<String>> keyGroup = new TreeMap<String, Set<String>>(collator);
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
						group = new TreeSet<String>(collator);
						keyGroup.put( firstChar, group);
					}
					group.add(string);
				}
				
				SortedMap<String, Set<String>> merged = merge( keyGroup, collator);
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
					addTemplates(subMenu, set, templateMap);
					item.add( subMenu);
				}
			}
			else
			{
				addTemplates( item, templateSet, templateMap);
			}
			element = item;
		}
		return element;
	}

	public void addTemplates(RaplaMenu item,
			Set<String> templateSet, Map<String,Collection<Allocatable>> templateMap) {
	    Locale locale = getLocale();
        
		for ( String templateName:templateSet)
		{
		    Collection<Allocatable> collection = templateMap.get( templateName);
		    // there could be multiple templates with the same name
		    for ( Allocatable template:collection)
		    {
    			RaplaMenuItem newItem = new TemplateMenuItem(template.getName( locale), template);
    			newItem.setText( templateName );
    			item.add( newItem);
    			newItem.addActionListener( this);
		    }
		}
	}
    
    private SortedMap<String, Set<String>> merge(
			SortedMap<String, Set<String>> keyGroup, Comparator<String> comparator) 
	{
    	SortedMap<String,Set<String>> result = new TreeMap<String, Set<String>>( comparator);
    	String beginnChar = null;
    	String currentChar = null;
    	Set<String> currentSet = null;
    	for ( String key: keyGroup.keySet() )
    	{
    		Set<String> set = keyGroup.get( key);
    		if ( currentSet == null)
    		{
    			currentSet = new TreeSet<String>(comparator);
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
    				currentSet = new TreeSet<String>(comparator);
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
	    	TemplateMenuItem source = (TemplateMenuItem) e.getSource();
	    	Allocatable templateName = source.getTemplate();
	    	List<Reservation> newReservations;
       		Collection<Reservation> reservations = getQuery().getTemplateReservations(templateName);
       		if (reservations.size() > 0)
       		{
	       		newReservations = copy( reservations, beginn);
				Collection<Allocatable> markedAllocatables = model.getMarkedAllocatables();
				if (markedAllocatables != null )
				{
					for (Reservation event: newReservations)
					{
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
       		}
	    	else
	    	{
	    		showException(new EntityNotFoundException("Template " + templateName + " is empty. Please create events in template first."), getMainComponent());
	    		return;
	    	}
			
//			if ( newReservations.size() == 1)
//			{
//				Reservation next = newReservations.iterator().next();
//				getReservationController().edit( next);
//			}
//			else
			{
				Collection<Reservation> list = new ArrayList<Reservation>();
				for ( Reservation reservation:newReservations)
				{
					Reservation cast = reservation;
					User lastChangedBy = cast.getLastChangedBy();
					if ( lastChangedBy != null && !lastChangedBy.equals(getUser()))
					{
						throw new RaplaException("Reservation " + cast + " has wrong user " + lastChangedBy);
					}
					list.add( cast);
				}
				Reservation[] array = list.toArray(Reservation.RESERVATION_ARRAY);
                getEditController().edit(array, getMainComponent());
				//SaveUndo<Reservation> saveUndo = new SaveUndo<Reservation>(getContext(), list, null);
				//getModification().getCommandHistory().storeAndExecute( saveUndo);
			}
			
		}
		catch (RaplaException ex)
		{
			showException( ex, getMainComponent());
		}
    }

    
	
}




