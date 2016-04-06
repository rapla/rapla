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

import java.util.Comparator;


public class AppointmentStartComparator implements Comparator<Appointment> {

    public int compare(Appointment a1,Appointment a2) {
        if ( a1.equals(a2)) return 0;
        if (a1.getStart().before(a2.getStart()))
            return -1;
        if (a1.getStart().after(a2.getStart()))
            return 1;

        Reservation r1 = a1.getReservation();
        Reservation r2 = a2.getReservation();
        if ( r1 == null && r2 == null )
        {
            @SuppressWarnings("unchecked")
            int compareTo = a1.compareTo(a2);
            return compareTo;
        }
        if ( r1 == null)
        {
            return 1;
        }
        if ( r2 == null)
        {
            return -1;
        }
        if ( r1 == r2)
        {
            int i1 = r1.indexOf( a1);
            int i2 = r1.indexOf( a2);
            if ( i1 < i2)
            {
                return -1;
            }
            if ( i1 > i2)
            {
                return 1;
            }
            if ( i1 == i2)
            {
                throw new IllegalStateException(" appointment should have passed equal before ");
            }
        }
        // compare the reservation ids
        {
            @SuppressWarnings("unchecked")
            int compareTo = r1.compareTo(r2);
            return compareTo;
        }
    }

}

    