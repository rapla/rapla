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

import org.rapla.entities.RaplaObject;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

import java.io.IOException;
import java.util.Map;

    
public class PreferenceWriter extends RaplaXMLWriter {
    public static final TypedComponentRole<Map<Class<? extends  RaplaObject>,RaplaXMLWriter>> WRITERMAP = new TypedComponentRole<>("org.rapla.storage.xml.writerMap");

    public PreferenceWriter(RaplaXMLContext sm) throws RaplaException {
        super(sm);
    }
    
    protected void printPreferences(Preferences preferences) throws IOException,RaplaException {
        if ( preferences != null && !preferences.isEmpty()) {
            openTag("rapla:preferences");
            //printTimestamp( preferences);            
            closeTag();
            PreferencesImpl impl = (PreferencesImpl)preferences;
            for (String role:impl.getPreferenceEntries())
            {
                Object entry = impl.getEntry(role);
                if ( entry instanceof String) {
                	openTag("rapla:entry");
                    att("key", role );
                    att("value", (String)entry);
                    closeElementTag();
                } if ( entry instanceof RaplaObject) {
                	openTag("rapla:entry");
                    att("key", role );
                    closeTag();
                    RaplaObject raplaObject = (RaplaObject)entry;
                	RaplaXMLWriter writer = getWriterFor( raplaObject.getTypeClass());
                	writer.writeObject( raplaObject );
                	closeElement("rapla:entry");
                }
            }
            closeElement("rapla:preferences");
        }
    }
    
    public void writeObject(RaplaObject object) throws IOException, RaplaException {
        printPreferences( (Preferences) object);
    }

 
 }
 


