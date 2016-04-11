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

package org.rapla.client.swing.gui.tests;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.test.util.RaplaTestCase;


@RunWith(JUnit4.class)
public final class RapaBuilderTest
{
    ClientFacade facade;
    @Before
    public void setUp() throws Exception
    {
        facade = RaplaTestCase.createSimpleSimpsonsWithHomer();
    }

    protected SerializableDateTimeFormat formater()
    {
        return new SerializableDateTimeFormat();
    }

    @Test
    public void testSplitAppointments() throws Exception {
        Date start = formater().parseDate("2004-01-01",false);
        Date end = formater().parseDate("2004-01-10",true);

        // test 2 Blocks
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        final RaplaFacade raplaFacade = facade.getRaplaFacade();
        Reservation reservation = raplaFacade.newReservation();
        Appointment appointment = raplaFacade.newAppointment(formater().parseDateTime("2004-01-01", "18:30:00"), formater().parseDateTime("2004-01-02", "12:00:00"));
        reservation.addAppointment( appointment );
        appointment.createBlocks(start,end, blocks );
        blocks = RaplaBuilder.splitBlocks( blocks
                    ,start
                    ,end
        );

        Assert.assertEquals("Blocks are not split in two", 2, blocks.size());
        Assert.assertEquals(formater().parseDateTime("2004-01-01", "23:59:59").getTime() / 1000, blocks.get(0).getEnd() / 1000);
        Assert.assertEquals(formater().parseDateTime("2004-01-02", "00:00:00").getTime(), blocks.get(1).getStart());
        Assert.assertEquals(formater().parseDateTime("2004-01-02", "12:00:00").getTime(), blocks.get(1).getEnd());

        //      test 3 Blocks
        blocks.clear();
        reservation = raplaFacade.newReservation();
        appointment = raplaFacade.newAppointment(formater().parseDateTime("2004-01-01", "18:30:00"), formater().parseDateTime("2004-01-04", "00:00:00"));
        reservation.addAppointment( appointment );
        appointment.createBlocks(start,end, blocks );
        blocks = RaplaBuilder.splitBlocks( blocks
                                           ,start
                                           ,end
                                         );
        Assert.assertEquals("Blocks are not split in three", 3, blocks.size());
        Assert.assertEquals(formater().parseDateTime("2004-01-03", "23:59:59").getTime() / 1000, blocks.get(2).getEnd() / 1000);

        //      test 3 Blocks, but only the first two should show
        blocks.clear();
        reservation = raplaFacade.newReservation();
        appointment = raplaFacade.newAppointment(formater().parseDateTime("2004-01-01", "18:30:00"), formater().parseDateTime("2004-01-04", "00:00:00"));
        reservation.addAppointment( appointment );
        appointment.createBlocks(start,end, blocks );
        blocks = RaplaBuilder.splitBlocks( blocks
                ,start
                ,formater().parseDateTime("2004-01-03","00:00:00")

        );
        Assert.assertEquals("Blocks are not split in two", 2, blocks.size());
        Assert.assertEquals(formater().parseDateTime("2004-01-02", "23:59:59").getTime() / 1000, blocks.get(1).getEnd() / 1000);
    }
}

