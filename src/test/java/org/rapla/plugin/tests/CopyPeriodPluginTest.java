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

import javax.inject.Provider;

import org.rapla.RaplaResources;
import org.rapla.RaplaTestCase;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.RaplaDateRenderer;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.periodcopy.PeriodCopyResources;
import org.rapla.plugin.periodcopy.client.swing.CopyDialog;
import org.rapla.plugin.periodcopy.client.swing.CopyPluginMenu;

/** listens for allocation changes */
public class CopyPeriodPluginTest extends RaplaTestCase {
    ClientFacade facade;
    Locale locale;

    public CopyPeriodPluginTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        facade = null;//FIXME raplaContainer.lookupDeprecated(ClientFacade.class, "local-facade");
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
        BundleManager bundleManager= new DefaultBundleManager();
        PeriodCopyResources i18n = new PeriodCopyResources(bundleManager);
        final Logger logger = getLogger();
        final RaplaLocaleImpl raplaLocale = new RaplaLocaleImpl(bundleManager);
        final RaplaResources rr = new RaplaResources(bundleManager);
        DateRenderer dateRenderer = new RaplaDateRenderer(getFacade(), rr, getRaplaLocale(), getLogger());
        RaplaResources raplaResources = rr;
        final RaplaImages raplaImages = new RaplaImages(logger);
        FrameControllerList frameList = new FrameControllerList(logger);
        DialogUiFactoryInterface dialogUiFactory = new DialogUiFactory(raplaResources, raplaImages, bundleManager, frameList, logger );
        BooleanFieldFactory booleanFieldFactory = new BooleanFieldFactory(facade, raplaResources, raplaLocale, logger);
        IOInterface t = new DefaultIO(logger);
        Provider<CopyDialog> copyDialogProvider = new Provider<CopyDialog>(){
            @Override
            public CopyDialog get()
            {
                return new CopyDialog(getFacade(), rr, getRaplaLocale(), getLogger(), i18n, model, dateRenderer, booleanFieldFactory, dialogUiFactory, t );
            }
        };
        CopyPluginMenu init = new CopyPluginMenu( getFacade(), rr, getRaplaLocale(), getLogger(), i18n, copyDialogProvider, raplaImages, dialogUiFactory);
        Reservation[] original = model.getReservations( sourcePeriod.getStart(), sourcePeriod.getEnd());
        assertNotNull(findReservationWithName(original, "power planting"));

        init.copy( Arrays.asList(original), destPeriod.getStart(),destPeriod.getEnd(), false);

        Reservation[] copy = model.getReservations( destPeriod.getStart(), destPeriod.getEnd());
        assertNotNull(findReservationWithName(copy,"power planting"));

    }
}

