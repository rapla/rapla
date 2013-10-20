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
package org.rapla.gui.internal.edit.reservation;

import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.ReservationCheck;
import org.rapla.gui.toolkit.DialogUI;

public class DefaultReservationCheck extends RaplaGUIComponent implements ReservationCheck
{
    public DefaultReservationCheck(RaplaContext context) {
        super(context);
    }

    public boolean check(Reservation reservation, Component sourceComponent) throws RaplaException {
        try
        {
            getClientFacade().checkReservation( reservation);
            Appointment[] appointments = reservation.getAppointments();
            Appointment duplicatedAppointment = null;
            for (int i=0;i<appointments.length;i++) {
                for (int j=i + 1;j<appointments.length;j++)
                    if (appointments[i].matches(appointments[j])) {
                        duplicatedAppointment = appointments[i];
                        break;
                    }
            }
            
            
            JPanel warningPanel = new JPanel();
            warningPanel.setLayout( new BoxLayout( warningPanel, BoxLayout.Y_AXIS));
            if (duplicatedAppointment != null) {
                JLabel warningLabel = new JLabel();
                warningLabel.setForeground(java.awt.Color.red);
                warningLabel.setText
                    (getI18n().format
                     (
                      "warning.duplicated_appointments"
                      ,getAppointmentFormater().getShortSummary(duplicatedAppointment)
                      )
                     );
                warningPanel.add( warningLabel);
            }
            
            if (reservation.getAllocatables().length == 0)
            {
                JLabel warningLabel = new JLabel();
                warningLabel.setForeground(java.awt.Color.red);
                warningLabel.setText(getString("warning.no_allocatables_selected"));
                warningPanel.add( warningLabel);
            }
            
            if (  warningPanel.getComponentCount() > 0) {
                DialogUI dialog = DialogUI.create(
                        getContext()
                        ,sourceComponent
                        ,true
                        ,warningPanel
                        ,new String[] {
                        getString("continue")
                        ,getString("back")
                }
                );
                dialog.setTitle( getString("warning"));
                dialog.setIcon(getIcon("icon.warning"));
                dialog.setDefault(1);
                dialog.getButton(0).setIcon(getIcon("icon.save"));
                dialog.getButton(1).setIcon(getIcon("icon.cancel"));
                dialog.start();
                if (dialog.getSelectedIndex() == 0)
                {
                    return true;
                }
                else
                {
                	return false;
                }
            }

            

            return true;
        }
        catch (RaplaException ex)
        {
            showWarning( ex.getMessage(), sourceComponent);
            return false;
        }
    }

}



