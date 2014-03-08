/*--------------------------------------------------------------------------*
  | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class RaplaCalendarSettingsReader extends RaplaXMLReader  {

    CalendarModelConfiguration settings;
    String title;
    String view;
    Date selectedDate;
    Date startDate;
    Date endDate;
    ClassificationFilter[] filter;
    RaplaMapReader optionMapReader;
    ClassificationFilterReader classificationFilterHandler;

    List<String> idList;
    Map<String,String> optionMap;
  
    
    public RaplaCalendarSettingsReader(RaplaContext context) throws RaplaException {
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

            idList = Collections.emptyList();
            optionMap = Collections.emptyMap();
        }

        if (localName.equals("selected")) {
        	idList = new ArrayList<String>();
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
        RaplaType raplaType = getTypeForLocalName( localName );
        if ( refid != null)
        {
        	String id = getId( raplaType, refid);
            idList.add( id);
        } 
        else if ( keyref != null)
        {
            DynamicType type = getDynamicType( keyref );
        	idList.add( type.getId());
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
            settings = new CalendarModelConfigurationImpl( idList, filter, defaultResourceTypes, defaultEventTypes,title,startDate, endDate, selectedDate, view, optionMap);
        }

        if (localName.equals("selected")) {
        }

        if (localName.equals("options")) {
            optionMap = optionMapReader.getEntityMap();
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

