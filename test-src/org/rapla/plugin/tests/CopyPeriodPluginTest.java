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
package org.rapla.plugin.tests;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.rapla.RaplaTestCase;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.plugin.periodcopy.CopyPluginMenu;

/** listens for allocation changes */
public class CopyPeriodPluginTest extends RaplaTestCase {
    ClientFacade facade;
    Locale locale;

    public CopyPeriodPluginTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        facade = raplaContainer.lookup(ClientFacade.class , "local-facade");
        facade.login("homer","duffs".toCharArray());
        locale = Locale.getDefault();
    }

    private Reservation findReservationWithName(Reservation[] reservations, String name) {
        for (int i=0;i<reservations.length;i++) {
            if ( reservations[i].getName( locale).equals( name )) {
                return reservations[i];
            }
        }
        return null;
    }
    @SuppressWarnings("null")
    public void test() throws Exception {
        CalendarSelectionModel model = facade.newCalendarModel( facade.getUser());
        ClassificationFilter filter = facade.getDynamicType("room").newClassificationFilter();
        filter.addEqualsRule("name","erwin");
        Allocatable allocatable = facade.getAllocatables( new ClassificationFilter[] { filter})[0];
        model.setSelectedObjects( Collections.singletonList(allocatable ));

        Period[] periods = facade.getPeriods();
        Period sourcePeriod = null;
        Period destPeriod = null;
        for ( int i=0;i<periods.length;i++) {
            if ( periods[i].getName().equals("SS 2002")) {
                sourcePeriod = periods[i];
            }
            if ( periods[i].getName().equals("SS 2001")) {
                destPeriod = periods[i];
            }
        }
        assertNotNull( "Period not found ", sourcePeriod );
        assertNotNull( "Period not found ", destPeriod );
        CopyPluginMenu init = new CopyPluginMenu( getClientService().getContext() );
        Reservation[] original = model.getReservations( sourcePeriod.getStart(), sourcePeriod.getEnd());
        assertNotNull(findReservationWithName(original, "power planting"));

        init.copy( Arrays.asList(original), destPeriod.getStart(),destPeriod.getEnd(), false);

        Reservation[] copy = model.getReservations( destPeriod.getStart(), destPeriod.getEnd());
        assertNotNull(findReservationWithName(copy,"power planting"));

    }
}

