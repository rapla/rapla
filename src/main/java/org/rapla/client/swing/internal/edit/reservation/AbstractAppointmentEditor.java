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
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.entities.domain.Appointment;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import java.util.ArrayList;
import java.util.Collection;

/** Provides AppointmentListener handling.*/
public class AbstractAppointmentEditor extends RaplaGUIComponent
{
    ArrayList<AppointmentListener> listenerList = new ArrayList<>();
    private boolean hasChanged;

    public AbstractAppointmentEditor(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
    {
        super(facade, i18n, raplaLocale, logger);
    }

    public void addAppointmentListener(AppointmentListener listener)
    {
        listenerList.add(listener);
    }

    public void removeAppointmentListener(AppointmentListener listener)
    {
        listenerList.remove(listener);
    }

    public AppointmentListener[] getAppointmentListeners()
    {
        return listenerList.toArray(new AppointmentListener[] {});
    }

    protected void fireAppointmentAdded(Collection<Appointment> appointment)
    {
        setHasChanged( true);
        AppointmentListener[] listeners = getAppointmentListeners();
        for (int i = 0; i < listeners.length; i++)
        {
            listeners[i].appointmentAdded(appointment);
        }
    }

    protected void fireAppointmentRemoved(Collection<Appointment> appointment)
    {
        setHasChanged( true);
        AppointmentListener[] listeners = getAppointmentListeners();
        for (int i = 0; i < listeners.length; i++)
        {
            listeners[i].appointmentRemoved(appointment);
        }
    }

    protected void fireAppointmentChanged(Collection<Appointment> appointment)
    {
        setHasChanged( true);
        AppointmentListener[] listeners = getAppointmentListeners();
        for (int i = 0; i < listeners.length; i++)
        {
            listeners[i].appointmentChanged(appointment);
        }
    }

    protected void fireAppointmentSelected(Collection<Appointment> appointment)
    {
        AppointmentListener[] listeners = getAppointmentListeners();
        for (int i = 0; i < listeners.length; i++)
        {
            listeners[i].appointmentSelected(appointment);
        }
    }

    public void setHasChanged(boolean flag)
    {
        hasChanged = flag;
    }

    public boolean hasChanged()
    {
        return hasChanged;
    }
}
