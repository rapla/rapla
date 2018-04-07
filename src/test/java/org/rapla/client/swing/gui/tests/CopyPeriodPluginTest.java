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
package org.rapla.client.swing.gui.tests;

import org.junit.Assert;
import org.junit.Before;
import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.swing.DialogUI.DialogUiFactory;
import org.rapla.client.swing.SwingSchedulerImpl;
import org.rapla.client.swing.internal.RaplaDateRenderer;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.client.swing.SwingBundleManager;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.plugin.periodcopy.PeriodCopyResources;
import org.rapla.plugin.periodcopy.client.swing.CopyDialog;
import org.rapla.plugin.periodcopy.client.swing.CopyPluginMenu;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.test.util.RaplaTestCase;

import javax.inject.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;

/** listens for allocation changes */
public class CopyPeriodPluginTest {
    ClientFacade facade;
    Locale locale;
    Logger logger;
    RaplaLocale raplaLocale;


    @Before
    public void setUp() throws Exception {
        facade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        locale = Locale.getDefault();
    }

    RaplaLocale getRaplaLocale()
    {
        return  raplaLocale;
    }

    public ClientFacade getFacade()
    {
        return facade;
    }

    Logger getLogger()
    {
        return logger;
    }

    private Reservation findReservationWithName(Collection<Reservation> reservations, String name) {
        for (Iterator<Reservation> it = reservations.iterator(); it.hasNext();)
        {
            Reservation reservation = it.next();
            if (reservation.getName(locale).equals(name))
            {
                return reservation;
            }
        }
        return null;
    }
    @SuppressWarnings("null")
    public void test() throws Exception {
        final RaplaFacade raplaFacade = facade.getRaplaFacade();
        final CalendarSelectionModel model = raplaFacade.newCalendarModel( facade.getUser());
        final ClassificationFilter filter = raplaFacade.getDynamicType("room").newClassificationFilter();
        filter.addEqualsRule("name","erwin");
        Allocatable allocatable = raplaFacade.getAllocatablesWithFilter( new ClassificationFilter[] { filter})[0];
        model.setSelectedObjects( Collections.singletonList(allocatable ));

        Period[] periods = raplaFacade.getPeriods();
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
        Assert.assertNotNull("Period not found ", sourcePeriod);
        Assert.assertNotNull("Period not found ", destPeriod);
        BundleManager bundleManager= new SwingBundleManager(logger);
        final PeriodCopyResources i18n = new PeriodCopyResources(bundleManager);
        final Logger logger = getLogger();
        final RaplaLocaleImpl raplaLocale = new RaplaLocaleImpl(bundleManager);
        final RaplaResources rr = new RaplaResources(bundleManager);
        final DateRenderer dateRenderer = new RaplaDateRenderer(raplaFacade, getRaplaLocale());
        final RaplaResources raplaResources = rr;
        CommandScheduler scheduler = new SwingSchedulerImpl(logger);
        final DialogUiFactoryInterface dialogUiFactory = new DialogUiFactory(raplaResources, scheduler, bundleManager,  logger );
        final BooleanFieldFactory booleanFieldFactory = new BooleanFieldFactory(facade, raplaResources, raplaLocale, logger);
        final IOInterface t = new DefaultIO(logger);
        Provider<CopyDialog> copyDialogProvider = new Provider<CopyDialog>(){
            @Override
            public CopyDialog get()
            {
                return new CopyDialog(getFacade(), rr, getRaplaLocale(), getLogger(), i18n, model, dateRenderer, booleanFieldFactory, dialogUiFactory, t );
            }
        };
        CopyPluginMenu init = new CopyPluginMenu( getFacade(), rr, getRaplaLocale(), getLogger(), i18n, copyDialogProvider,  dialogUiFactory);
        Collection<Reservation>original = RaplaTestCase.waitForWithRaplaException(model.queryReservations( new TimeInterval(sourcePeriod.getStart(), sourcePeriod.getEnd())), 10000);
        Assert.assertNotNull(findReservationWithName(original, "power planting"));

        init.copy( original, destPeriod.getStart(),destPeriod.getEnd(), false);

        Collection<Reservation> copy = RaplaTestCase.waitForWithRaplaException(model.queryReservations( new TimeInterval(destPeriod.getStart(), destPeriod.getEnd())), 10000);
        Assert.assertNotNull(findReservationWithName(copy, "power planting"));

    }
}

