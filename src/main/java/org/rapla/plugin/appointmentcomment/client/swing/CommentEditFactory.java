/*
 * Copyright [2024] [Liselotte Lichtenstein]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rapla.plugin.appointmentcomment.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.extensionpoints.AppointmentEditExtensionFactory;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.appointmentcomment.AppointmentCommentFunctions;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

@Extension (provides = AppointmentEditExtensionFactory.class, id = "appointmentcomment")
@Singleton
public class CommentEditFactory implements AppointmentEditExtensionFactory {
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;

    @Inject
    public CommentEditFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
    {
        super();
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
    }

    @Override
    public RaplaWidget createField(AppointmentEditExtensionEvents events) throws RaplaException {
        return new CommentEditFactory.CommentEditor(events);
    }

    class CommentEditor implements RaplaWidget, Consumer<Appointment>
    {
        JTextField commentField = new JTextField(10);
        private boolean isSaving = false; // Flag to prevent infinite loop

        AppointmentEditExtensionEvents events;
        CommentEditor(AppointmentEditExtensionEvents events)
        {
            this.events = events;
            events.init(this);

            commentField.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    // Do nothing
                }

                @Override
                public void focusLost(FocusEvent e) {
                    // Save the comment when the field loses focus
                    if (!isSaving) {
                        isSaving = true;
                        Appointment appointment = events.getAppointment();
                        String comment = commentField.getText();
                        AppointmentCommentFunctions.setComment(appointment, comment);
                        try {
                            events.appointmentChanged();
                        } finally {
                            isSaving = false;
                        }
                    }
                }
            });


        }


        public JComponent getComponent()
        {
            return commentField;
        }

        @Override
        public void accept(Appointment appointment) {
            String comment = AppointmentCommentFunctions.getComment(appointment);
            commentField.setText(comment);
        }
    }

}
