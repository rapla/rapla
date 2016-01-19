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
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;

import java.util.Date;

public class ConflictReader extends RaplaXMLReader
{
    public ConflictReader( RaplaXMLContext context ) throws RaplaException
    {
        super( context );
    }

    @Override
    public void processElement(
        String namespaceURI,
        String localName,
        RaplaSAXAttributes atts ) throws RaplaSAXParseException
    {
        if (!namespaceURI.equals( RAPLA_NS ))
            return;

        ReferenceInfo<Allocatable> allocId = getRef(atts, "resource", Allocatable.class);
        ReferenceInfo<Appointment> appId1 = getRef(atts, "appointment1", Appointment.class);
        ReferenceInfo<Appointment> appId2 = getRef(atts, "appointment2", Appointment.class);
        String id = ConflictImpl.createId(allocId, appId1, appId2);
        Date today = getReadTimestamp();
        ConflictImpl conflict;
        try {
            final Date lastChanged = readTimestamps(atts).changeTime;
            conflict = new ConflictImpl(id, today, lastChanged);

        } catch (RaplaException e) {
            throw new RaplaSAXParseException(e.getMessage(), e);
        }
        boolean enabledAppointment1 = getString(atts,"appointment1enabled", "true").equalsIgnoreCase("true");
        conflict.setAppointment1Enabled(enabledAppointment1);
        boolean enabledAppointment2 = getString(atts,"appointment2enabled", "true").equalsIgnoreCase("true");
        conflict.setAppointment2Enabled(enabledAppointment2);
        add( conflict );

    }
    
}
