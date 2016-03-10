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

    private ImportExportEntityImpl importExport;
    public ImportExportReader(RaplaXMLContext context) throws RaplaException
    {
        super(context);
    }

    @Override
    public void processElement(String namespaceURI, String localName, RaplaSAXAttributes atts) throws RaplaSAXParseException
    {
        if (localName.equals("importexport"))
        {
            importExport = new ImportExportEntityImpl();
            final String id = getString(atts, "id");
            importExport.setId(id);
            final String raplaId = getString(atts, "raplaid", null);
            importExport.setRaplaId(raplaId);
            final String externalSystem = getString(atts, "externalsystem");
            importExport.setExternalSystem(externalSystem);
            final String directionString = getString(atts, "direction");
            final int direction = parseLong(directionString).intValue();
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
        else if(localName.equals("importexport"))
        {
            add(importExport);
            importExport = null;
        }
            
    }
}
