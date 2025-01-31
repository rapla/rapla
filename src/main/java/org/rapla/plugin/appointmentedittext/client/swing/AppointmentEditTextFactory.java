package org.rapla.plugin.appointmentedittext.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.extensionpoints.AppointmentEditExtensionFactory;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.entities.domain.Appointment;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.function.Consumer;

//@Extension(provides = AppointmentEditExtensionFactory.class, id="appointmenteditcomment")
@Singleton
public class AppointmentEditTextFactory implements AppointmentEditExtensionFactory {
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;

    @Inject
    public AppointmentEditTextFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
    {
        super();
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
    }
    @Override
    public RaplaWidget createField(AppointmentEditExtensionEvents events) throws RaplaException {
        return new AppointmentEditTextFactory.AppointmentCounter(events);
    }

    class AppointmentCounter  implements RaplaWidget, Consumer<Appointment>
    {
        JTextField statusBar = new JTextField();
        AppointmentEditExtensionEvents events;
        AppointmentCounter(AppointmentEditExtensionEvents events) {
            this.events = events;
            events.init( this);
            statusBar.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {

                }

                @Override
                public void removeUpdate(DocumentEvent e) {

                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    Appointment appointment = events.getAppointment();
                    //appointment.setComment
                    //events.appointmentChanged();
                }
            } );
        }

        public JComponent getComponent() {
            return statusBar;
        }

        @Override
        public void accept(Appointment appointment) {
            appointment.getStart().getTime();
            statusBar.setText("" + appointment.getStart().getTime());
        }
    }
}
