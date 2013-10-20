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

import java.util.Date;

import org.rapla.entities.RaplaObject;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class RaplaCalendarSettingsReader extends RaplaXMLReader  {

    CalendarModelConfiguration settings;
    String title;
    String view;
    Date selectedDate;
    Date startDate;
    Date endDate;
    ClassificationFilter[] filter;
    RaplaMapReader<RaplaObject> selectedEntitiesMapReader;
    RaplaMapReader<String> optionMapReader;
    ClassificationFilterReader classificationFilterHandler;

    RaplaMap<RaplaObject> entityMap;
    RaplaMap<String> optionMap;

  
    
    public RaplaCalendarSettingsReader(RaplaContext context) throws RaplaException {
        super( context );
        selectedEntitiesMapReader= new RaplaMapReader<RaplaObject>(context);
        optionMapReader= new RaplaMapReader<String>(context);
        classificationFilterHandler = new ClassificationFilterReader(context);
        addChildHandler( selectedEntitiesMapReader );
        addChildHandler( optionMapReader );
        addChildHandler( classificationFilterHandler );
    }

    public void processElement(String namespaceURI,String localName,String qName,Attributes atts)
        throws SAXException
    {
        if (localName.equals("calendar")) {
            filter = null;
            classificationFilterHandler.clear();
            title = getString( atts,"title");
            view = getString( atts,"view");

            selectedDate = getDate( atts, "date");
            startDate = getDate( atts, "startdate");
            endDate = getDate( atts, "enddate");

            entityMap = new RaplaMapImpl<RaplaObject>();
            optionMap = new RaplaMapImpl<String>();
        }

        if (localName.equals("selected")) {
            delegateElement( selectedEntitiesMapReader, namespaceURI, localName, qName, atts);
        }

        if (localName.equals("options")) {
            delegateElement( optionMapReader, namespaceURI, localName, qName, atts);
        }

        if (localName.equals("filter"))
        {
            classificationFilterHandler.clear();
            
        	delegateElement( classificationFilterHandler, namespaceURI, localName, qName, atts);
        }

    }

    private Date getDate(Attributes atts, String key ) throws SAXException {
        String dateString = getString( atts,key, null);

        if ( dateString != null) {
            return parseDate( dateString, false );
        } else {
            return null;
        }
    }

    public void processEnd(String namespaceURI,String localName,String qName)
        throws SAXException
    {
        if (localName.equals("calendar")) {
            boolean defaultResourceTypes =  classificationFilterHandler.isDefaultResourceTypes();
            boolean defaultEventTypes = classificationFilterHandler.isDefaultEventTypes();
            settings = new CalendarModelConfigurationImpl( entityMap, filter, defaultResourceTypes, defaultEventTypes,title,startDate, endDate, selectedDate, view, optionMap);
        }

        if (localName.equals("selected")) {
            entityMap = selectedEntitiesMapReader.getEntityMap();
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

