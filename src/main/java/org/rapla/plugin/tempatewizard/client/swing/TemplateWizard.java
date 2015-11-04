
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
package org.rapla.plugin.tempatewizard.client.swing;

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

import javax.inject.Inject;
import javax.swing.MenuElement;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.ReservationWizardExtension;
import org.rapla.client.swing.EditController;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.MenuScroller;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.defaultwizard.client.swing.DefaultWizard;
import org.rapla.plugin.tempatewizard.TemplatePlugin;

/** This ReservationWizard displays no wizard and directly opens a ReservationEdit Window
*/
@Extension(provides = ReservationWizardExtension.class, id = TemplatePlugin.PLUGIN_ID)
public class TemplateWizard extends RaplaGUIComponent implements ReservationWizardExtension, ActionListener, ModificationListener
{
	Collection<Allocatable> templateNames;
    private final CalendarSelectionModel model;
    private final EditController editController;
    private final RaplaImages raplaImages;
    private final DialogUiFactory dialogUiFactory;
    private final PermissionController permissionController;
	@Inject
    public TemplateWizard(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model, EditController editController, RaplaImages raplaImages, DialogUiFactory dialogUiFactory, PermissionController permissionController) throws RaplaException{
        super(facade, i18n, raplaLocale, logger);
        this.model = model;
        this.editController = editController;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = permissionController;
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
        private static final long serialVersionUID = 1L;
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
		final ClientFacade clientFacade = getClientFacade();
        boolean canCreateReservation = permissionController.canCreateReservation(clientFacade);
		MenuElement element;
		if (templateNames.size() == 0)
		{
			return null;
		}
		final RaplaLocale raplaLocale = getRaplaLocale();
        if ( templateNames.size() == 1)
		{
			Allocatable template = templateNames.iterator().next();
			RaplaMenuItem item = new TemplateMenuItem( getId(), template);
            item.setEnabled( permissionController.canAllocate(model, clientFacade, raplaLocale) && canCreateReservation);
			final String templateName = template.getName(getLocale());
			item.setText( getI18n().format("new_reservation.format", templateName));
            item.setIcon(raplaImages.getIconFromKey("icon.new"));
            item.addActionListener( this);
			element = item;
		}
		else
		{
			RaplaMenu item = new RaplaMenu( getId());
			item.setEnabled( permissionController.canAllocate(model, clientFacade, raplaLocale) && canCreateReservation);
			item.setText(getString("new_reservations_from_template"));
			item.setIcon( raplaImages.getIconFromKey("icon.new"));
			@SuppressWarnings("unchecked")
            Comparator<String> collator = (Comparator<String>) (Comparator)Collator.getInstance(raplaLocale.getLocale());
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
					item.setIcon( raplaImages.getIconFromKey("icon.new"));
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
		    Collection<Reservation> reservations;
		    TemplateMenuItem source = (TemplateMenuItem) e.getSource();
            Allocatable template = source.getTemplate();
            reservations = getQuery().getTemplateReservations(template);
            if (reservations.size() == 0)
            {
                showException(new EntityNotFoundException("Template " + template + " is empty. Please create events in template first."), getMainComponent(), dialogUiFactory);
                return;
            }
            Boolean keepOrig = (Boolean) template.getClassification().getValue("fixedtimeandduration");
		    Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
		    boolean markedIntervalTimeEnabled = model.isMarkedIntervalTimeEnabled();
            boolean keepTime = !markedIntervalTimeEnabled || (keepOrig == null || keepOrig); 
	    	Date beginn = getStartDate(model);
	    	Collection<Reservation> newReservations = getModification().copy(reservations, beginn, keepTime);
       		if ( markedIntervals.size() >0 && reservations.size() == 1 && reservations.iterator().next().getAppointments().length == 1 && keepOrig == Boolean.FALSE)
       		{
       		    Appointment app = newReservations.iterator().next().getAppointments()[0];
       		    TimeInterval first = markedIntervals.iterator().next();
       		    Date end = first.getEnd();
       		    if (!markedIntervalTimeEnabled)
       		    {
       		        end = getRaplaLocale().toDate( end,app.getEnd() );
       		    }
       		    if (!beginn.before(end))
       		    {
       		        end = new Date( app.getStart().getTime() + DateTools.MILLISECONDS_PER_HOUR );
       		    }
       		    app.move(app.getStart(), end);
       		}
            List<Reservation> list = DefaultWizard.addAllocatables(model, newReservations, getUser());
            Reservation[] array = list.toArray(Reservation.RESERVATION_ARRAY);
			final SwingPopupContext popupContext = new SwingPopupContext(getMainComponent(), null);
			String title = null;
			EditController.EditCallback<List<Reservation>> callback = null;
			editController.edit(list, title, popupContext, callback);
		}
		catch (RaplaException ex)
		{
			showException( ex, getMainComponent(), dialogUiFactory);
		}
    }
	
}




