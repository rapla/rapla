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
import org.rapla.entities.storage.internal.ImportExportEntityImpl;
import org.rapla.framework.RaplaException;

public class ImportExportReader extends RaplaXMLReader
{
    private static class AttReader extends DelegationHandler
    {
        public AttReader(RaplaXMLContext context)
        {
        }
    }

    private AttReader dynAttHandler;
    private ImportExportEntityImpl importExport;

    public ImportExportReader(RaplaXMLContext context) throws RaplaException
    {
        super(context);
        dynAttHandler = new AttReader(context);
        addChildHandler(dynAttHandler);
    }

    @Override
    public void processElement(String namespaceURI, String localName, RaplaSAXAttributes atts) throws RaplaSAXParseException
    {
        if (localName.equals("importExport"))
        {
            importExport = new ImportExportEntityImpl();
            final String id = atts.getValue("id");
            importExport.setId(id);
            final String raplaId = atts.getValue("raplaId");
            importExport.setRaplaId(raplaId);
            final String externalSystem = atts.getValue("externalSystem");
            importExport.setExternalSystem(externalSystem);
            final String directionString = atts.getValue("direction");
            final int direction = Integer.parseInt(directionString);
            importExport.setDirection(direction);
        }
        else if (localName.equals("data"))
        {
            startContent();
        }
        else if (localName.equals("context"))
        {
            startContent();
        }

    }

    @Override
    public void processEnd(String namespaceURI, String localName) throws RaplaSAXParseException
    {
        if (localName.equals("data"))
        {
            final String data = readContent();
            importExport.setData(data);
        }
        else if (localName.equals("context"))
        {
            final String context = readContent();
            importExport.setContext(context);
        }
        else if(localName.equals("importExport"))
        {
            add(importExport);
            importExport = null;
        }
            
    }
}
