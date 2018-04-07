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

import org.rapla.entities.NamedComparator;

import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class ReservationStartComparator implements Comparator<Reservation> {
    NamedComparator<Reservation> namedComp;
    public ReservationStartComparator(Locale locale) {
        namedComp = new NamedComparator<>(locale);
    }
    public int compare(Reservation o1,Reservation o2) {

        if ( o1.equals(o2)) return 0;
        Reservation r1 =  o1;
        Reservation r2 =  o2;
        if (getStart(r1).before(getStart(r2)))
            return -1;
        if (getStart(r1).after(getStart(r2)))
            return 1;

        return namedComp.compare(o1,o2);
    }

    public static Date getStart(Reservation r) {
        Date maxDate = null;
        Appointment[] apps =r.getAppointments();
        for ( int i=0;i< apps.length;i++) {
            Appointment app = apps[i];
            if (maxDate == null || app.getStart().before( maxDate)) {
                maxDate = app.getStart() ;
            }
        }
        if ( maxDate == null) {
            maxDate = new Date();
        }
        return maxDate;
    }

    public int compare(Date d1,Object o2) {
        if (o2 instanceof Date)
            return d1.compareTo((Date) o2);

        Reservation r2 = (Reservation) o2;
        if (d1.before(getStart(r2))) {
            //System.out.println(a2 + ">" + d1);
            return -1;
        }
        if (d1.after(getStart(r2))) {
            //                System.out.println(a2 + "<" + d1);
            return 1;
        }

        // If appointment.getStart().equals(date)
        // set the appointment before the date
        return 1;
    }

}

