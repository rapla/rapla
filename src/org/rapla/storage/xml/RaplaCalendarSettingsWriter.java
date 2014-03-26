/*--------------------------------------------------------------------------*
  | Copyright (C) 2014 Christopher Kohlhaas                                  |
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU General Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org .       |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/

package org.rapla.storage.xml;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;


public class RaplaCalendarSettingsWriter extends ClassificationFilterWriter {

    /**
     * @param sm
     * @throws RaplaException
     */
    public RaplaCalendarSettingsWriter(RaplaContext sm) throws RaplaException {
        super(sm);
    }

    public void writeObject(RaplaObject type) throws IOException, RaplaException {
        CalendarModelConfigurationImpl calendar = (CalendarModelConfigurationImpl) type ;
        openTag("rapla:"  + CalendarModelConfiguration.TYPE.getLocalName());
        att("title", calendar.getTitle());
        att("view", calendar.getView());
        Map<String,String> extensionMap = calendar.getOptionMap();
        final String saveDate = extensionMap != null ? extensionMap.get( CalendarModel.SAVE_SELECTED_DATE) : "false" ;
        boolean saveDateActive = saveDate != null && saveDate.equals("true");
		if ( calendar.getSelectedDate() != null && saveDateActive) {
            att("date", dateTimeFormat.formatDate( calendar.getSelectedDate()));
        }
        if ( calendar.getStartDate() != null && saveDateActive) {
            att("startdate", dateTimeFormat.formatDate( calendar.getStartDate()));
        }
        if ( calendar.getEndDate() != null && saveDateActive) {
            att("enddate", dateTimeFormat.formatDate( calendar.getEndDate()));
        }
        if ( calendar.isResourceRootSelected())
        {
        	att("rootSelected","true");
        }
        closeTag();
        Collection<Entity> selectedObjects = calendar.getSelected();
        if (selectedObjects != null && selectedObjects.size() > 0)
        {
            openElement("selected");
            for ( Entity entity:selectedObjects)
            {
            	printReference( entity);
            }
            closeElement("selected");
        }
        if (extensionMap != null && extensionMap.size() > 0)
        {
            openElement("options");
            RaplaMapWriter writer = (RaplaMapWriter)getWriterFor( RaplaMap.TYPE);
            writer.writeMap( extensionMap);
            closeElement("options");
        }
        ClassificationFilter[] filter =calendar.getFilter() ;
        if ( filter.length> 0)
        {
            openElement("filter");
            for (ClassificationFilter f:filter) 
            {
                final DynamicType dynamicType = f.getType();
                final String annotation = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                boolean eventType = annotation != null && annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
                if (( eventType && !calendar.isDefaultEventTypes())
                    || (!eventType && !calendar.isDefaultResourceTypes())
                )
                {
                    printClassificationFilter( f );
                }
            }
            closeElement("filter");
        }
        closeElement("rapla:" + CalendarModelConfiguration.TYPE.getLocalName());
    }



 }



