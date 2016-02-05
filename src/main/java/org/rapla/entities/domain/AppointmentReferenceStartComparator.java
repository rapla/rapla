/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.entities.domain;

import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.Comparator;

public class AppointmentReferenceStartComparator implements Comparator<ReferenceInfo<Appointment>> {
    EntityResolver resolver;
    AppointmentStartComparator comparator;
    public AppointmentReferenceStartComparator(EntityResolver resolver)
    {
        this.resolver = resolver;
        comparator = new AppointmentStartComparator();
    }

    public int compare(ReferenceInfo<Appointment> a1Ref,ReferenceInfo<Appointment> a2Ref) {
        Appointment a1 = resolver.tryResolve( a1Ref);
        Appointment a2 = resolver.tryResolve( a2Ref);
        return comparator.compare( a1, a2);
    }

}

    