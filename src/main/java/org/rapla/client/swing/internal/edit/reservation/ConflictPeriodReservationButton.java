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
package org.rapla.client.swing.internal.edit.reservation;

import org.rapla.RaplaResources;
import org.rapla.client.AppointmentListener;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.check.HolidayExceptionCheck;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.ReservationToolbarExtension;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import java.awt.Component;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Extension(provides = ReservationToolbarExtension.class, id = "holidayexception")
public class ConflictPeriodReservationButton extends RaplaGUIComponent implements ReservationToolbarExtension
{
    private final DialogUiFactoryInterface dialogUiFactory;
    private Reservation reservation;
    private RaplaButton button;
    private final HolidayExceptionCheck check;

    @Inject
    public ConflictPeriodReservationButton(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            DialogUiFactoryInterface dialogUiFactory, HolidayExceptionCheck check)
    {
        super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
        this.check = check;
    }

    @Override
    public Collection<RaplaWidget> createExtensionButtons(ReservationEdit edit)
    {
        final Category periodCategory = check.getPeriodsCategory();
        if (periodCategory == null)
        {
            return Collections.emptyList();
        }
        if ( periodCategory.getCategories().length == 0)
        {
        	return Collections.emptyList();
        }
        final PopupContext popupContext = new SwingPopupContext((Component) edit.getComponent(), null);
        button = new RaplaButton();
        button.setText(i18n.getString("holidays"));
        button.addActionListener((evt) ->
        {
            try
            {
                final Map<Appointment, Set<Period>> periodConflicts = HolidayExceptionCheck.getPeriodConflicts(getFacade(),Collections.singletonList(reservation));
                check.showPeriodConflicts( popupContext, periodConflicts, true).thenAccept((result) ->
                {
                    if (result == null || result.isEmpty())
                    {
                        return;
                    }
                    edit.addExceptionsToCurrentAppointment( result);
                    boolean modified = false;

                    //            final Date[] exceptionsBefore = repeating.getExceptions();
                    //            repeating.addExceptions(period.getInterval());
                    //            final Date[] exceptionsAfter = repeating.getExceptions();
                    //            if (!Arrays.equals(exceptionsAfter, exceptionsBefore)) {
                    //                modified = true;
                    //modified ? DialogResult.OK_MODIFIED : DialogResult.OK;

                    if ( modified)
                    {
                        updateButton(reservation);
                        edit.fireChange();
                    }
                });
            } catch (Exception ex)
            {
                dialogUiFactory.showException( ex, popupContext);
            }
        });
        edit.addAppointmentListener(new AppointmentListener()
        {
            @Override
            public void appointmentAdded(Collection<Appointment> appointment)
            {
                updateButton( reservation);
            }

            @Override
            public void appointmentRemoved(Collection<Appointment> appointment)
            {
                updateButton( reservation);
            }

            @Override
            public void appointmentChanged(Collection<Appointment> appointment)
            {
                updateButton( reservation);
            }

            @Override
            public void appointmentSelected(Collection<Appointment> appointment)
            {

            }
        });
        return Collections.singletonList(() -> button);
    }

    @Override
    public void setReservation(Reservation newReservation, Appointment mutableAppointment) throws RaplaException
    {
        updateButton(newReservation);
        this.reservation = newReservation;
    }

    private void updateButton(Reservation newReservation)
    {
    	if ( button == null)
    	{
    		return;
    	}
    	final Map<Appointment, Set<Period>> periodConflicts;
        try
        {
            periodConflicts = HolidayExceptionCheck.getPeriodConflicts(getFacade(),Collections.singletonList(newReservation));
        }
        catch (RaplaException e)
        {
            getLogger().error( e.getMessage(),e);
            return;
        }
        int count = 0;
        for (Set<Period> periods : periodConflicts.values())
        {
            count += periods.size();
        }
        button.setText(i18n.getString("holidays") + " (" + count + ")");
    }
}



