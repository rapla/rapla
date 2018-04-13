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
package org.rapla.plugin.defaultwizard.client;

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.extensionpoints.ReservationWizardExtension;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/** This ReservationWizard displays no wizard and directly opens a ReservationEdit Window
 */
@Extension(provides = ReservationWizardExtension.class, id = "defaultWizard") public class DefaultWizard
        implements ReservationWizardExtension
{
    final public static TypedComponentRole<Boolean> ENABLED = new TypedComponentRole<>("org.rapla.plugin.defaultwizard.enabled");
    private final PermissionController permissionController;
    private final CalendarModel model;
    private final ApplicationEventBus eventBus;
    private final MenuItemFactory menuItemFactory;
    private final RaplaResources i18n;
    RaplaFacade raplaFacade;
    private ClientFacade clientFacade;

    @Inject public DefaultWizard(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model,
                                  ApplicationEventBus eventBus, MenuItemFactory menuFactory)
    {
        this.clientFacade = clientFacade;
        this.i18n = i18n;
        raplaFacade = clientFacade.getRaplaFacade();
        this.menuItemFactory = menuFactory;
        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        this.permissionController = raplaFacade.getPermissionController();
        this.model = model;
        this.eventBus = eventBus;
    }

    @Override public boolean isEnabled()
    {
        try
        {
            return raplaFacade.getSystemPreferences().getEntryAsBoolean(ENABLED, true);
        }
        catch (RaplaException e)
        {
            return false;
        }
    }

    @Override
    public String getId()
    {
        return "000_defaultWizard";
    }

    @Override
    public Object getComponent()
    {
        List<DynamicType> eventTypes = new ArrayList<>();
        User user;
        try
        {
            user = clientFacade.getUser();
            DynamicType[] types = raplaFacade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            for (DynamicType type : types)
            {
                if (permissionController.canCreate(type, user))
                {
                    eventTypes.add(type);
                }
            }
        }
        catch (RaplaException e)
        {
            return null;
        }
        boolean canCreateReservation = eventTypes.size() > 0;
        IdentifiableMenuEntry element;
        String newEventText = i18n.getString("new_reservation");
        final boolean enabled = raplaFacade.canAllocate(model, user) && canCreateReservation;

        if (eventTypes.size() == 1)
        {
            final DynamicType type = eventTypes.get(0);
            String name = type.getName(i18n.getLocale());
            String text;
            if (newEventText.endsWith(name))
            {
                text = newEventText;
            }
            else
            {
                text = newEventText + " " + name;
            }
            Consumer<PopupContext> action = enabled ?  (popupContext) -> actionPerformed(popupContext, type): null;
            element = menuItemFactory.createMenuItem(text,i18n.getIcon("icon.new"),action);
        }
        else
        {
            MenuInterface container = menuItemFactory.createMenu(newEventText, i18n.getIcon("icon.new"),"new");
            if ( enabled) {
                for (DynamicType type : eventTypes) {
                    String name = type.getName(i18n.getLocale());
                    Consumer<PopupContext> action =(popupContext) -> actionPerformed(popupContext, type);
                    IdentifiableMenuEntry newItem = menuItemFactory.createMenuItem(name, null, action);
                    container.addMenuItem(newItem);
                }
            }
            element = container;
        }
        return element.getComponent();
    }

    public void actionPerformed(final PopupContext popupContext, DynamicType type)
    {
        ApplicationEventContext context = null;
        eventBus.publish(new ApplicationEvent(EditTaskPresenter.CREATE_RESERVATION_FOR_DYNAMIC_TYPE, type.getId(), popupContext, context));
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




