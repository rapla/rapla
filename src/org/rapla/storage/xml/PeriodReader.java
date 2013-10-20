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

import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class PeriodReader extends DynAttReader {
    public PeriodReader(RaplaContext context) throws RaplaException {
        super(context);
    }

    public void processElement(String namespaceURI,String localName,String qName,Attributes atts)
        throws SAXException
    {
        if (namespaceURI.equals(RAPLA_NS) && localName.equals("period")) {
            PeriodImpl period = new PeriodImpl();
            setId(period, atts);
            setVersionIfThere( period, atts);
            period.setName(getString(atts,"name"));
            period.setStart(parseDate(getString(atts,"start"),false));
            period.setEnd(parseDate(getString(atts,"end"),true));
            add(period);
        }

    }
}




