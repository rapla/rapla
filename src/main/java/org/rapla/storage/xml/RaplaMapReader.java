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
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class RaplaMapReader extends RaplaXMLReader  {

    String key;
    RaplaMapImpl entityMap;
    RaplaXMLReader childReader;

    public RaplaMapReader(RaplaContext sm) throws RaplaException {
        super(sm);
    }

    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
	        throws RaplaSAXParseException
    {
        if ( !RAPLA_NS.equals(namespaceURI))
            return;
        if (localName.equals(RaplaMap.TYPE.getLocalName())) {
            entityMap = new RaplaMapImpl();
            return;
        }
        if (localName.equals("mapentry")) {
            key= getString(atts, "key");
            String value = getString( atts, "value", null);
            if ( value != null)
            {
            	try
            	{
            		entityMap.putPrivate( key, value );
            	}
            	catch (ClassCastException ex)
            	{
            		getLogger().error("Mixed maps are currently not supported.", ex);
            	}
            }
            return;
        }

        String refid = getString( atts, "idref", null);
        String keyref = getString( atts, "keyref", null);
        RaplaType raplaType = getTypeForLocalName( localName );
        if ( refid != null) {
            childReader = null;
            // We ignore the old references from 1.7 that are not compatible
            if ( !entityMap.isTypeSupportedAsLink(raplaType)) {
                return;
            }
            String id = getId( raplaType, refid);
            entityMap.putIdPrivate( key,  id, raplaType);
        }  else if ( keyref != null) {
            childReader = null;
            DynamicType type = getDynamicType( keyref );
            if ( type != null) {
            	String id = ((Entity) type).getId();
                entityMap.putIdPrivate( key,  id, DynamicType.TYPE);
            }
        } else {
            childReader = getChildHandlerForType( raplaType );
            delegateElement( childReader, namespaceURI, localName, atts);
        }

    }

    @Override
    public void processEnd(String namespaceURI,String localName)
		throws RaplaSAXParseException
    {
        if ( !RAPLA_NS.equals(namespaceURI) )
            return;

        if ( childReader != null ) {
            RaplaObject type = childReader.getType();
            try
        	{
            	entityMap.putPrivate( key, type);
        	}
        	catch (ClassCastException ex)
        	{
        		getLogger().error("Mixed maps are currently not supported.", ex);
        	}
        }
        childReader = null;
    }

    public RaplaMap getEntityMap() {
        return entityMap;
    }
    
    public RaplaObject getType() {
        //reservation.getReferenceHandler().put()
        return getEntityMap();
    }


}

