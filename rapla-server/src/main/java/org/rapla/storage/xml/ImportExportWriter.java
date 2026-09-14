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

import org.rapla.entities.RaplaObject;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.framework.RaplaException;

import java.io.IOException;


public class ImportExportWriter extends RaplaXMLWriter {
    public ImportExportWriter(RaplaXMLContext sm) throws RaplaException {
        super(sm);
    }

    public void printImportExportEntity(ExternalSyncEntity externalSyncEntity) throws IOException,RaplaException {
        final String tagName = "rapla:importexport";
        openTag(tagName);
        printId(externalSyncEntity);
        att("raplaid", externalSyncEntity.getRaplaId());
        att("direction", externalSyncEntity.getDirection()+"");
        att("context", externalSyncEntity.getContext());
        att("externalsystem", externalSyncEntity.getExternalSystem());
        closeTag();
        {
            final String elementName = "data";
            openElementOnLine(elementName);
            printEncode(externalSyncEntity.getData());
            closeElementOnLine(elementName);
            println();
        }
        if(externalSyncEntity.getContext() != null)
        {
            final String elementName = "context";
            openElementOnLine(elementName);
            printEncode(externalSyncEntity.getContext());
            closeElementOnLine(elementName);
            println();
        }
        closeElement(tagName);
    }

    
    public void writeObject(RaplaObject object) throws IOException, RaplaException {
        printImportExportEntity( (ExternalSyncEntity) object);
    }

  



}



