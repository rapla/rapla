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
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.SAXConfigurationHandler;

public class RaplaConfigurationReader extends RaplaXMLReader  {
    boolean delegating = false;
    
    public RaplaConfigurationReader(RaplaXMLContext context) throws RaplaException {
        super(context);
    }
    
    SAXConfigurationHandler configurationHandler = new SAXConfigurationHandler();
    
    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
        throws RaplaSAXParseException
    {
        if ( RAPLA_NS.equals(namespaceURI) && localName.equals(RaplaConfigurationWriter.TAGNAME))
            return;
        delegating = true;
        configurationHandler.startElement(namespaceURI,localName, atts);
    }

    @Override
    public void processEnd(String namespaceURI,String localName)
        throws RaplaSAXParseException
    {
        if ( RAPLA_NS.equals(namespaceURI) && localName.equals(RaplaConfigurationWriter.TAGNAME))
        {
            return;
        }
        
        configurationHandler.endElement(namespaceURI, localName);
        delegating = false;
    }

    @Override
    public void processCharacters(char[] ch,int start,int length)
     {

        if ( delegating ){
            configurationHandler.characters(ch,start,length);
        }
    }


    public RaplaObject getType() {
        return new RaplaConfiguration(getConfiguration());
    }
    
    private Configuration getConfiguration() {
        Configuration conf = configurationHandler.getConfiguration();
        return conf;
    }
}

