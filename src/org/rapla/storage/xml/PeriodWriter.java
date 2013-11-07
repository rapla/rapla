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

import java.io.IOException;

import org.rapla.entities.RaplaObject;
import org.rapla.entities.domain.Period;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class PeriodWriter extends RaplaXMLWriter {
    public PeriodWriter(RaplaContext sm) throws RaplaException {
        super(sm);
    }

    protected void printPeriod(Period p) throws IOException {
        String start = dateTimeFormat.formatDate( p.getStart());
        String end = dateTimeFormat.formatDate(p.getEnd(),true);

        openTag("rapla:period");
        printId(p);
        printVersion( p);
        att("name",p.getName());
        att("start", start);
        att("end", end);
        closeElementTag();
    }
    public void writeObject( RaplaObject object ) throws IOException, RaplaException
    {
        printPeriod( (Period) object);
    }

}


