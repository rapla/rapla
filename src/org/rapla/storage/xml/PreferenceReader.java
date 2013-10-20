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

import java.util.Map;

import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class PreferenceReader extends RaplaXMLReader {
    public static final TypedComponentRole<Map<String,RaplaType>> LOCALNAMEMAPENTRY = new TypedComponentRole<Map<String,RaplaType>>(PreferenceReader.class.getPackage().getName() + ".localnameMap");
    public static final TypedComponentRole<Map<Object,RaplaXMLReader>> READERMAP = new TypedComponentRole<Map<Object,RaplaXMLReader>>(PreferenceReader.class.getPackage().getName() + ".readerMap");
    
    PreferencesImpl preferences;
    String configRole;
    User owner;
    RaplaXMLReader childReader;
    String stringValue = null;

    public PreferenceReader(RaplaContext sm) throws RaplaException {
        super(sm);
    }
    
    public void setUser( User owner) {
        this.owner = owner;
    }
    
    public void processElement(String namespaceURI,String localName,String qName,Attributes atts)
        throws SAXException
    {
        if ( RAPLA_NS.equals(namespaceURI) && localName.equals("data")) {
            return;
        }
            
        if (localName.equals("preferences")) {
            preferences = new PreferencesImpl();
            String id = atts.getValue("id");
            if ( id!=null) {
                setId( preferences, atts );
            } else {
                setNewId(preferences);
            }
            setVersionIfThere( preferences, atts);

            if ( owner == null )
            {
                String ownerid = atts.getValue("ownerid");
                if ( ownerid != null)
                {
                    preferences.setOwner(resolve(User.TYPE, ownerid));
                }
            } 
            else
            {
                preferences.setOwner( owner );
            }
            return;
        }

        if (localName.equals("entry")) {
            configRole = getString(atts,"key");
            stringValue = getString(atts,"value", null);
            if ( stringValue != null) {
            	preferences.putEntry( configRole,stringValue );
            }
            return;
        }

        RaplaType raplaTypeName = getTypeForLocalName(localName );
        childReader = getChildHandlerForType( raplaTypeName );
        delegateElement(childReader,namespaceURI,localName,qName,atts);
    }
    
    public Preferences getPreferences() {
        return preferences;
    }
    
    public RaplaObject getChildType() throws SAXException {
    	return childReader.getType();
    }
    
    public void processEnd(String namespaceURI,String localName,String qName)
        throws SAXException
    {
        if (!namespaceURI.equals(RAPLA_NS))
            return;

        
        if (localName.equals("preferences")) {
            add(preferences);
        } 
        if (localName.equals("entry") && stringValue == null) {
            RaplaObject type = childReader.getType();
            preferences.putEntry(configRole, type);
        }
    }
    
}

