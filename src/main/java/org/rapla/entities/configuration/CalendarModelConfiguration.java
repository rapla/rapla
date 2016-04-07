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
package org.rapla.entities.configuration;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.framework.TypedComponentRole;

/**
 *
 * @author ckohlhaas
 * @version 1.00.00
 * @since 2.03.00
 */
public interface CalendarModelConfiguration extends RaplaObject<CalendarModelConfiguration>
{
    TypedComponentRole<CalendarModelConfiguration> CONFIG_ENTRY = new TypedComponentRole<CalendarModelConfiguration>("org.rapla.DefaultSelection");
    TypedComponentRole<RaplaMap<CalendarModelConfiguration>> EXPORT_ENTRY = new TypedComponentRole<RaplaMap<CalendarModelConfiguration>>("org.rapla.plugin.autoexport");
    Date getStartDate();
    Date getEndDate();
    Date getSelectedDate();
    String getTitle();
    String getView();
    Collection<Entity> getSelected();
    Map<String,String> getOptionMap();
    //public Configuration get
    ClassificationFilter[] getFilter();
    
    boolean isDefaultEventTypes();
    boolean isDefaultResourceTypes();

    boolean isResourceRootSelected();
    
    CalendarModelConfiguration cloneWithNewOptions(Map<String, String> newMap);
}
