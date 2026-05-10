package org.rapla.plugin.externaleventimport.client.swing;

import org.rapla.client.RaplaWidget;
import org.rapla.client.ReservationEdit;
import org.rapla.client.swing.ReservationToolbarExtension;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportController;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportEnabledCondition;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportResources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import javax.swing.JButton;
import java.awt.Component;
import java.util.Collection;
import java.util.Collections;

/**
 * Toolbar button on the reservation editor that triggers a sync from the external
 * source against the currently-edited reservation.
 */
@Service(ExternalEventSyncButtonExtension.ID)
@Conditional(ExternalEventImportEnabledCondition.class)
public class ExternalEventSyncButtonExtension implements ReservationToolbarExtension
{
    public static final String ID = "org.rapla.plugin.externaleventimport.sync";

    private final ExternalEventImportController controller;
    private final ExternalEventImportResources resources;

    private JButton button;
    private ReservationEdit reservationEdit;

    @Autowired
    public ExternalEventSyncButtonExtension(ExternalEventImportController controller, ExternalEventImportResources resources)
    {
        this.controller = controller;
        this.resources = resources;
    }

    @Override
    public Collection<RaplaWidget> createExtensionButtons(ReservationEdit edit)
    {
        this.reservationEdit = edit;
        button = new JButton(resources.getString("synchronize"));
        button.addActionListener(e -> {
            Component source = (Component) e.getSource();
            controller.syncReservation(reservationEdit, new SwingPopupContext(source, null));
        });
        return Collections.singleton((RaplaWidget) () -> button);
    }

    @Override
    public void setReservation(Reservation newReservation, Appointment mutableAppointment)
    {
        if (button != null)
        {
            // Enable the button only when editing an existing reservation. Detection of
            // "already imported" is done server-side at sync time, not client-side here.
            button.setEnabled(reservationEdit != null && !reservationEdit.isNew());
        }
    }
}
