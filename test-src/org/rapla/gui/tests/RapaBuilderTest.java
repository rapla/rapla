/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.gui.tests;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.RaplaTestCase;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;


public final class RapaBuilderTest extends RaplaTestCase
{
    public RapaBuilderTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(RapaBuilderTest.class);
    }

    public void testSplitAppointments() throws Exception {
        Date start = formater().parseDate("2004-01-01",false);
        Date end = formater().parseDate("2004-01-10",true);

        // test 2 Blocks
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        Reservation reservation = getFacade().newReservation();
        Appointment appointment = getFacade().newAppointment(
                    formater().parseDateTime("2004-01-01","18:30:00")
                    ,formater().parseDateTime("2004-01-02","12:00:00")
                                );
        reservation.addAppointment( appointment );
        appointment.createBlocks(start,end, blocks );
        blocks = RaplaBuilder.splitBlocks( blocks
                    ,start
                    ,end
        );

        assertEquals("Blocks are not split in two", 2, blocks.size());
        assertEquals( formater().parseDateTime("2004-01-01","23:59:59").getTime()/1000, blocks.get(0).getEnd()/1000);
        assertEquals( formater().parseDateTime("2004-01-02","00:00:00").getTime(), blocks.get(1).getStart());
        assertEquals( formater().parseDateTime("2004-01-02","12:00:00").getTime(), blocks.get(1).getEnd());

        //      test 3 Blocks
        blocks.clear();
        reservation = getFacade().newReservation();
        appointment = getFacade().newAppointment(
                    formater().parseDateTime("2004-01-01","18:30:00")
                    ,formater().parseDateTime("2004-01-04","00:00:00")
                                );
        reservation.addAppointment( appointment );
        appointment.createBlocks(start,end, blocks );
        blocks = RaplaBuilder.splitBlocks( blocks
                                           ,start
                                           ,end
                                         );
        assertEquals("Blocks are not split in three", 3, blocks.size());
        assertEquals(formater().parseDateTime("2004-01-03","23:59:59").getTime()/1000, blocks.get(2).getEnd()/1000);

        //      test 3 Blocks, but only the first two should show
        blocks.clear();
        reservation = getFacade().newReservation();
        appointment = getFacade().newAppointment(
                formater().parseDateTime("2004-01-01","18:30:00")
                ,formater().parseDateTime("2004-01-04","00:00:00")
        );
        reservation.addAppointment( appointment );
        appointment.createBlocks(start,end, blocks );
        blocks = RaplaBuilder.splitBlocks( blocks
                ,start
                ,formater().parseDateTime("2004-01-03","00:00:00")

        );
        assertEquals("Blocks are not split in two", 2, blocks.size());
        assertEquals(formater().parseDateTime("2004-01-02","23:59:59").getTime()/1000, blocks.get(1).getEnd()/1000);
    }
}

