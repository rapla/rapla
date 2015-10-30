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
package org.rapla.plugin.dayresource.client.swing;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.Icon;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;

@Extension(provides = SwingViewFactory.class,id = DayResourceViewFactory.DAY_RESOURCE_VIEW)
public class DayResourceViewFactory  implements SwingViewFactory
{
    private final RaplaResources i18n;
    private final Set<ObjectMenuFactory> objectMenuFactories;
    private final MenuFactory menuFactory;
    private final Provider<DateRenderer> dateRendererProvider;
    @Inject
    public DayResourceViewFactory(RaplaResources i18n, Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, Provider<DateRenderer> dateRendererProvider )
    {
        this.i18n = i18n;
        this.objectMenuFactories = objectMenuFactories;
        this.menuFactory = menuFactory;
        this.dateRendererProvider = dateRendererProvider;
    }

    public final static String DAY_RESOURCE_VIEW = "day_resource";

    public SwingCalendarView createSwingView(RaplaContext context, CalendarModel model, boolean editable) throws RaplaException
    {
        return new SwingDayResourceCalendar( context, model, editable, objectMenuFactories, menuFactory, i18n, dateRendererProvider);
    }

    public String getViewId()
    {
        return DAY_RESOURCE_VIEW;
    }

    public String getName()
    {
        return i18n.getString(DAY_RESOURCE_VIEW);
    }

    Icon icon;
    public Icon getIcon()
    {
        if ( icon == null) {
            icon = RaplaImages.getIcon("/org/rapla/plugin/dayresource/images/day_resource.png");
        }
        return icon;
    }

    public String getMenuSortKey() {
        return "A";
    }


}

