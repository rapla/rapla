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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;

public class RaplaCalendarSettingsReader extends RaplaXMLReader  {

    CalendarModelConfiguration settings;
    String title;
    String view;
    Date selectedDate;
    Date startDate;
    Date endDate;
    boolean resourceRootSelected;
    ClassificationFilter[] filter;
    RaplaMapReader optionMapReader;
    ClassificationFilterReader classificationFilterHandler;

    List<String> idList;
    List<Class<? extends Entity>> idTypeList;
    Map<String,String> optionMap;
  
    
    public RaplaCalendarSettingsReader(RaplaXMLContext context) throws RaplaException {
        super( context );
        optionMapReader= new RaplaMapReader(context);
        classificationFilterHandler = new ClassificationFilterReader(context);
        addChildHandler( optionMapReader );
        addChildHandler( classificationFilterHandler );
    }

    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
        throws RaplaSAXParseException
    {
        if (localName.equals("calendar")) {
            filter = null;
            classificationFilterHandler.clear();
            title = getString( atts,"title");
            view = getString( atts,"view");

            selectedDate = getDate( atts, "date");
            startDate = getDate( atts, "startdate");
            endDate = getDate( atts, "enddate");
            resourceRootSelected = getString(atts, "rootSelected", "false").equalsIgnoreCase("true");
            idList = Collections.emptyList();
            idTypeList = Collections.emptyList();
            optionMap = Collections.emptyMap();
        }

        if (localName.equals("selected")) {
        	idList = new ArrayList<String>();
        	idTypeList = new ArrayList<Class<? extends Entity>>();
        }

        if (localName.equals("options")) {
            delegateElement( optionMapReader, namespaceURI, localName, atts);
        }
        
        if (localName.equals("filter"))
        {
            classificationFilterHandler.clear();
            
        	delegateElement( classificationFilterHandler, namespaceURI, localName, atts);
        }

        String refid = getString( atts, "idref", null);
        String keyref = getString( atts, "keyref", null);
        if ( refid != null)
        {
            final Class<? extends RaplaObject> typeClass = getTypeForLocalName( localName );
            // Test if raplaType can be referenced by the model (categories and periods cannot, but old versions allowed to store them 
            if ( CalendarModelConfigurationImpl.canReference(typeClass))
            {
                final Class<? extends Entity> entityClass = (Class<? extends Entity>) typeClass;
                ReferenceInfo id = getId( entityClass, refid);
                idList.add( id.getId());
                idTypeList.add(entityClass);
            }
        } 
        else if ( keyref != null)
        {
            final Class<? extends RaplaObject> typeClass = getTypeForLocalName(localName);
            // Test if raplaType can be referenced by the model (categories and periods cannot, but old versions allowed to store them
            if ( CalendarModelConfigurationImpl.canReference(typeClass))
            {
                ReferenceInfo id = null;
                if (typeClass == Category.class)
                {
                    id = getKeyAndPathResolver().getIdForCategory(keyref);
                }
                else if (typeClass == DynamicType.class)
                {
                    id = getKeyAndPathResolver().getIdForDynamicType(keyref);
                }
                if ( id != null)
                {
                    idList.add(id.getId());
                    idTypeList.add( (Class<? extends Entity>)typeClass);
                }
                else
                {
                    getLogger().error("Can't find type with key " + keyref + " while loading calendar selections " + ( title != null ? "for " + title : ""));
                }
            }
        }
    }

    private Date getDate(RaplaSAXAttributes atts, String key ) throws RaplaSAXParseException {
        String dateString = getString( atts,key, null);

        if ( dateString != null) {
            return parseDate( dateString, false );
        } else {
            return null;
        }
    }

    @Override
    public void processEnd(String namespaceURI,String localName)
    {
        if (localName.equals("calendar")) {
            boolean defaultResourceTypes =  classificationFilterHandler.isDefaultResourceTypes();
            boolean defaultEventTypes = classificationFilterHandler.isDefaultEventTypes();
            settings = new CalendarModelConfigurationImpl( idList,idTypeList, resourceRootSelected,filter, defaultResourceTypes, defaultEventTypes,title,startDate, endDate, selectedDate, view, optionMap);
        }

        if (localName.equals("selected")) {
        	
        }

        if (localName.equals("options")) {
            @SuppressWarnings("unchecked")
			Map<String,String> entityMap = optionMapReader.getEntityMap();
			optionMap = entityMap;
        }

        if (localName.equals("filter")) {
            filter = classificationFilterHandler.getFilters();
        }


    }

    public RaplaObject getType() {
        //reservation.getReferenceHandler().put()
        return settings;
    }


}

