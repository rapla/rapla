/*---------------------------------------------------------------------------*
  | (C) 2014 Christopher Kohlhaas                                            |
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

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;

final public class XMLReaderAdapter {
    
	static SAXParserFactory spfvalidating;
	static SAXParserFactory spfnonvalidating;
	
	static private SAXParserFactory getFactory( boolean validating)
	{
		if ( validating && spfvalidating != null)
		{
			return spfvalidating;
		}
		if ( !validating && spfnonvalidating != null)
		{
			return spfnonvalidating;
		}
		SAXParserFactory spf = SAXParserFactory.newInstance();
    	spf.setNamespaceAware(true);
    	spf.setValidating(validating);
    	if ( validating)
    	{
    		spfvalidating = spf;
    	}
    	else
    	{
    		spfnonvalidating = spf;
    	}
    	return spf;
	}
	
	public static XMLReader createXMLReader(boolean validating) throws SAXException {
      try {
            SAXParserFactory spf = getFactory(validating);
			return spf.newSAXParser().getXMLReader();
        } catch (Exception ex2) {
        	throw new SAXException("Couldn't createInfoDialog XMLReader " + ex2.getMessage(), ex2);
        }
    }
}
