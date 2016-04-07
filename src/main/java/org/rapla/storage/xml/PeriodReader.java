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

import java.util.Date;

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;

public class PeriodReader extends DynAttReader {
    public PeriodReader(RaplaXMLContext context) throws RaplaException {
        super(context);
    }

    static int idCount = 0 ;
    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
        throws RaplaSAXParseException
    {
        if (namespaceURI.equals(RAPLA_NS) && localName.equals("period")) {	 
        	AllocatableImpl period = new AllocatableImpl(new Date(), new Date());
        	Classification classification = ((DynamicTypeImpl)store.getDynamicType(StorageOperator.PERIOD_TYPE)).newClassificationWithoutCheck(true);
            classification.setValue("name", getString(atts,"name"));
            classification.setValue("start",parseDate(getString(atts,"start"),false));
            classification.setValue("end",parseDate(getString(atts,"end"),true));
            period.setClassification( classification);
            Permission newPermission = period.newPermission();
            newPermission.setAccessLevel( Permission.READ);
            period.addPermission( newPermission);
            String id = atts.getValue("id");
            if ( id != null)
            {
            	period.setId(id);
            }
            else
            {
                period.setId("period_"+idCount);
                idCount++;
            }
            add(period);
        }

    }
}




