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

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
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
    	harden(spf);
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
	
	/**
	 * A9: harden the parser against XXE. rapla's XML never uses a DOCTYPE, so we
	 * forbid them outright (the strongest mitigation — kills external entities,
	 * parameter entities and entity-expansion bombs in one feature) plus disable
	 * external entity / DTD resolution as defence in depth.
	 */
	private static void harden(SAXParserFactory spf)
	{
		try
		{
			spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
			spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		}
		catch (ParserConfigurationException | SAXException e)
		{
			// Fail loud: refusing to run an un-hardened XML parser is the safe choice.
			throw new IllegalStateException("Could not harden SAX parser against XXE", e);
		}
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
