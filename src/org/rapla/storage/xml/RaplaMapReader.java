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

import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.RefEntity;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class RaplaMapReader<T> extends RaplaXMLReader  {

    String key;
    RaplaMapImpl<T> entityMap;
    RaplaXMLReader childReader;

    public RaplaMapReader(RaplaContext sm) throws RaplaException {
        super(sm);
    }

    @SuppressWarnings("unchecked")
	public void processElement(String namespaceURI,String localName,String qName,Attributes atts)
        throws SAXException
    {
        if ( !RAPLA_NS.equals(namespaceURI))
            return;
        if (localName.equals(RaplaMap.TYPE.getLocalName())) {
            entityMap = new RaplaMapImpl<T>();
            return;
        }
        if (localName.equals("mapentry")) {
            key= getString(atts, "key");
            String value = getString( atts, "value", null);
            if ( value != null)
            {
            	try
            	{
            		entityMap.putPrivate( key,(T) value );
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
            // TODO We ignore the old references
            if ( raplaType.equals( Appointment.TYPE) || raplaType.equals( Reservation.TYPE)) {
                return;
            }
            Object id = getId( raplaType, refid);
            entityMap.getReferenceHandler().putId( key,  id);
        }  else if ( keyref != null) {
            childReader = null;
            DynamicType type = getDynamicType( keyref );
            if ( type != null) {
                Object id = ((RefEntity<?>) type).getId();
                entityMap.getReferenceHandler().putId( key,  id);
            }
        } else {
            childReader = getChildHandlerForType( raplaType );
            delegateElement( childReader, namespaceURI, localName, qName, atts);
        }

    }

    @SuppressWarnings("unchecked")
	public void processEnd(String namespaceURI,String localName,String qName)
        throws SAXException
    {
        if ( !RAPLA_NS.equals(namespaceURI) )
            return;

        if ( childReader != null ) {
            RaplaObject type = childReader.getType();
            try
        	{
            	entityMap.putPrivate( key, (T)type);
        	}
        	catch (ClassCastException ex)
        	{
        		getLogger().error("Mixed maps are currently not supported.", ex);
        	}
        }
        childReader = null;
    }

    public RaplaMap<T> getEntityMap() {
        return entityMap;
    }
    public RaplaObject getType() {
        //reservation.getReferenceHandler().put()
        return getEntityMap();
    }


}

