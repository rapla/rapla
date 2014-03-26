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

package org.rapla.components.util.xml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class RaplaContentHandler extends DefaultHandler
{
    Locator locator;
    RaplaSAXHandler handler;
    public RaplaContentHandler(RaplaSAXHandler handler) {
    	this.handler = handler;
    }
    
    public void setDocumentLocator( Locator locator )
    {
        this.locator = locator;
    }

    final public void startElement(
        String namespaceURI,
        String localName,
        String qName,
        Attributes atts ) throws SAXException
    {
    	try
        {
    		Map<String,String> attributeMap;
			if ( atts != null) {
				int length = atts.getLength();
				if ( length == 0)
				{
					attributeMap = Collections.emptyMap();
				}
				else if ( length == 1)
				{
					String key = atts.getLocalName( 0);
					String value = atts.getValue( 0);
					attributeMap = Collections.singletonMap(key, value);
				}
				else
				{
					attributeMap = new LinkedHashMap<String, String>();
					for ( int i=0;i<length;i++)
					{
					    String key = atts.getLocalName( i);
					    String value = atts.getValue( i);
					    attributeMap.put( key, value);
					}	
				}
			}
			else
			{
				attributeMap = Collections.emptyMap();
			}
    		handler.startElement( namespaceURI, localName,  new RaplaSAXAttributes(attributeMap)  );
        }
        catch (RaplaSAXParseException ex)
        {
        	throw new SAXParseException(ex.getMessage(), locator, ex);
        }
        catch (Exception ex)
        {
            throw new SAXException( ex );
        }
    	
    }

    final public void endElement(
        String namespaceURI,
        String localName,
        String qName ) throws SAXException
    {
    	try
    	{
    		handler.endElement( namespaceURI, localName);
    	}
    	catch ( RaplaSAXParseException ex)
    	{
    		throw new SAXParseException(ex.getMessage(), locator, ex);
    	}
    }

    final public void characters( char[] ch, int start, int length )
        throws SAXException
    {
    	handler.characters( ch, start, length );
    }


}
