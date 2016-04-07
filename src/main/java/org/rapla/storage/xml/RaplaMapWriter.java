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
import java.util.Iterator;
import java.util.Map;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaException;

    
public class RaplaMapWriter extends RaplaXMLWriter {

    final static String TAGNAME =  "map";
    public RaplaMapWriter(RaplaXMLContext sm) throws RaplaException {
        super(sm);
    }

    public void writeObject(RaplaObject type) throws IOException, RaplaException {
        writeMap_((RaplaMapImpl) type );
    }

    private void writeMap_(RaplaMapImpl map ) throws IOException, RaplaException {
        openElement("rapla:" +TAGNAME);
        for (Iterator<String> it = map.keySet().iterator();it.hasNext();) {
            Object key = it.next();
            Object obj =  map.get( key);
            printRaplaObject( key, obj);
        }
        closeElement("rapla:" +TAGNAME);
    }

    public void writeMap(Map<String,String> map ) throws IOException {
        openElement("rapla:" +TAGNAME);
        for (Map.Entry<String,String> entry:map.entrySet()) {
            String key = entry.getKey();
            String obj =  entry.getValue();
            openTag("rapla:mapentry");
            att("key", key.toString());
            if ( obj instanceof String)
            {
                String value = obj;
                att("value", value);
                closeElementTag();
            }
        }
        closeElement("rapla:" +TAGNAME);
    }

    
    private void printRaplaObject(Object key,Object obj) throws RaplaException, IOException {
        if (obj == null)
        {
            getLogger().warn( "Map contains empty value under key " + key );
            return;
        }
        int start = getIndentLevel();
        openTag("rapla:mapentry");
        att("key", key.toString());
        if ( obj instanceof String)
        {
            String value = (String) obj;
            att("value", value);
            closeElementTag();
            return;
        }
        closeTag();
        if ( obj instanceof Entity ) {
            printReference( (Entity) obj);
        } else {
            RaplaObject raplaObj = (RaplaObject) obj;
            getWriterFor( raplaObj.getTypeClass()).writeObject( raplaObj );
        }
        setIndentLevel( start+1 );
        closeElement("rapla:mapentry");
        setIndentLevel( start );
    }
    
 }
 


