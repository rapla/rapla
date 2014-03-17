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

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

public class PreferenceReader extends RaplaXMLReader {
    public static final TypedComponentRole<Map<String,RaplaType>> LOCALNAMEMAPENTRY = new TypedComponentRole<Map<String,RaplaType>>("org.rapla.storage.xml.localnameMap");
    public static final TypedComponentRole<Map<RaplaType,RaplaXMLReader>> READERMAP = new TypedComponentRole<Map<RaplaType,RaplaXMLReader>>("org.rapla.storage.xml.readerMap");
    
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
    
    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
        throws RaplaSAXParseException
    {
        if ( RAPLA_NS.equals(namespaceURI) && localName.equals("data")) {
            return;
        }
            
        if (localName.equals("preferences")) {
            preferences = new PreferencesImpl();
            preferences.setResolver( store);
            setVersionIfThere( preferences, atts);

            if ( owner == null )
            {
            	preferences.setId( Preferences.TYPE.getId( 0));
            } 
            else
            {
                preferences.setOwner( owner );
                int key = User.TYPE.getKey(owner.getId());
                preferences.setId( Preferences.TYPE.getId( key));
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
        delegateElement(childReader,namespaceURI,localName,atts);
    }
    
    public Preferences getPreferences() {
        return preferences;
    }
    
    public RaplaObject getChildType() throws RaplaSAXParseException {
    	return childReader.getType();
    }
    
    @Override
    public void processEnd(String namespaceURI,String localName)
        throws RaplaSAXParseException
    {
        if (!namespaceURI.equals(RAPLA_NS))
            return;

        
        if (localName.equals("preferences")) {
            add(preferences);
        } 
        if (localName.equals("entry") && stringValue == null) {
            RaplaObject type = childReader.getType();
            preferences.putEntryPrivate(configRole, type);
        }
    }
    
}

