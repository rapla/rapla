/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import java.util.Comparator;

import org.rapla.entities.storage.internal.SimpleEntity;


public class AppointmentStartComparator implements Comparator<Appointment> {
    public int compare(Appointment a1,Appointment a2) {

        if ( a1.equals(a2)) return 0;
        if (a1.getStart().before(a2.getStart()))
            return -1;
        if (a1.getStart().after(a2.getStart()))
            return 1;

        return ((SimpleEntity)a1).compareTo( (SimpleEntity)a2 );
    }

}

    