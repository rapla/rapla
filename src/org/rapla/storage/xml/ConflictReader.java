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

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class ConflictReader extends DynAttReader {
    public ConflictReader(RaplaContext context) throws RaplaException {
        super(context);
    }

    public void processElement(String namespaceURI,String localName,String qName,Attributes atts)
        throws SAXException
    {
        if (namespaceURI.equals(RAPLA_NS) && localName.equals("conflict")) {
        	ConflictImpl conflict = new ConflictImpl();
        	ReferenceHandler referenceHandler = conflict.getReferenceHandler();
        	referenceHandler.putId("allocatable", getId(Allocatable.TYPE,getString(atts,"allocatable")));
        	referenceHandler.putId("appointment1", getId(Appointment.TYPE,getString(atts,"appointment1")));
        	referenceHandler.putId("appointment2", getId(Appointment.TYPE,getString(atts,"appointment2")));
        	conflict.setId( conflict.createId());
            add(conflict);
        }

    }
}




