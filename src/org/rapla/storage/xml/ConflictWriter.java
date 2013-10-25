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
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class ConflictWriter extends RaplaXMLWriter {
    public ConflictWriter(RaplaContext sm) throws RaplaException {
        super(sm);
    }

    protected void printConflict(Conflict conflict) throws IOException 
    {
        openTag("rapla:conflict");
        att("allocatable",getId( conflict.getAllocatable()));
        att("appointment1", getId( conflict.getAppointment1()));
        att("appointment2", getId( conflict.getAppointment2()));
        closeElementTag();
    }
    
    public void writeObject( RaplaObject object ) throws IOException, RaplaException
    {
        printConflict( (Conflict) object);
    }

}


