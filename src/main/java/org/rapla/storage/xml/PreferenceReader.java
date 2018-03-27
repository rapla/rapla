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

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

import java.util.Map;

public class PreferenceReader extends RaplaXMLReader {
    public static final TypedComponentRole<Map<String,Class<? extends  RaplaObject>>> LOCALNAMEMAPENTRY = new TypedComponentRole<>("org.rapla.storage.xml.localnameMap");
    public static final TypedComponentRole<Map<Class<? extends  RaplaObject>,RaplaXMLReader>> READERMAP = new TypedComponentRole<>("org.rapla.storage.xml.readerMap");
    
    PreferencesImpl preferences;
    String configRole;
    User owner;
    RaplaXMLReader childReader;
    String stringValue = null;

    public PreferenceReader(RaplaXMLContext sm) throws RaplaException {
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
        	TimestampDates ts = readTimestamps(atts);
            preferences = new PreferencesImpl(ts.createTime, ts.changeTime);
            setLastChangedBy(preferences, atts);
            preferences.setResolver( store);
            String ownerId = owner != null ? owner.getId() :null;
            ReferenceInfo<Preferences> ref = PreferencesImpl.getPreferenceIdFromUser( ownerId);
            if ( owner == null )
            {
            	preferences.setId( ref.getId());
            } 
            else
            {
                preferences.setOwner( owner );
                preferences.setId( ref.getId());
            }
            return;
        }

        if (localName.equals("entry")) {
            configRole = getString(atts,"key");
            stringValue = getString(atts,"value", null);
            // ignore old entry
            if ( stringValue != null) {
            	preferences.putEntryPrivate( configRole,stringValue );
            }
            return;
        }
        Class<? extends RaplaObject> raplaType = getTypeForLocalName(localName );
        childReader = getChildHandlerForType( raplaType );
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

