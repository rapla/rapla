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
import java.util.LinkedHashMap;

import org.rapla.entities.RaplaObject;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;


public class RaplaConfigurationWriter extends RaplaXMLWriter {

    public RaplaConfigurationWriter(RaplaContext sm) throws RaplaException {
        super(sm);
    }

    public void writeObject(RaplaObject type) throws IOException, RaplaException {
        RaplaConfiguration raplaConfig = (RaplaConfiguration) type ;
        openElement("rapla:" + RaplaConfiguration.TYPE.getLocalName());
        try {
            printConfiguration(raplaConfig  );
        } catch (ConfigurationException ex) {
            throw new RaplaException( ex );
        }
        closeElement("rapla:" + RaplaConfiguration.TYPE.getLocalName());
    }

    /**
     * Serialize each Configuration element.  This method is called recursively.
     * Original code for this method is taken from  the org.apache.framework.configuration.DefaultConfigurationSerializer class
     * @throws ConfigurationException if an error occurs
     * @throws IOException if an error occurs
     */
    
    private void printConfiguration(final Configuration element ) throws ConfigurationException, RaplaException, IOException {

        LinkedHashMap<String, String> attr = new LinkedHashMap<String, String>();
        String[] attrNames = element.getAttributeNames();

        if( null != attrNames )
        {
            for( int i = 0; i < attrNames.length; i++ )
            {
                String key = attrNames[ i ];
                String value = element.getAttribute( attrNames[ i ], "" );
                attr.put( key, value);
            }
        }

        String qName = element.getName();
        openTag(qName);
        att(attr);
        Configuration[] children = element.getChildren();
        if (children.length > 0)
        {
            closeTag();
            for( int i = 0; i < children.length; i++ )
            {
                printConfiguration( children[ i ] );
            }
            closeElement(qName);
        }
        else
        {
            String value = element.getValue( null );
            if (null == value)
            {
                closeElementTag();
            }
            else
            {
                closeTagOnLine();
                print(value);
                closeElementOnLine(qName);
                println();
            }
        }
    }


 }



