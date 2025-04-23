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
package org.rapla.plugin.appointmentnote.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.extensionpoints.AppointmentEditExtensionFactory;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.domain.Appointment;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.appointmentnote.AppointmentNotePlugin;
import org.rapla.plugin.appointmentnote.AppointmentNoteFunctions;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

@Extension (provides = AppointmentEditExtensionFactory.class, id = AppointmentNotePlugin.PLUGIN_ID)
@Singleton
public class AppointmentNoteEditFactory implements AppointmentEditExtensionFactory {
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;
    private final TextField.TextFieldFactory textFieldFactory;

    @Inject
    public AppointmentNoteEditFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TextField.TextFieldFactory textFieldFactory)
    {
        super();
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.textFieldFactory = textFieldFactory;
    }

    @Override
    public RaplaWidget createField(AppointmentEditExtensionEvents events) throws RaplaException {
        if (!isEnabled()) {
            return null;
        }
        return new NoteEditor(events);
    }

    public boolean isEnabled() {
        try {
            return facade.getRaplaFacade().getSystemPreferences().getEntryAsBoolean(AppointmentNotePlugin.ENABLED, false);
        } catch (RaplaException e) {
            return false;
        }
    }


    class NoteEditor implements RaplaWidget, Consumer<Appointment>
    {
        TextField textField;
        JPanel panel = new JPanel();
        JLabel label = new JLabel();
        //JTextField noteField = new JTextField(10);
        private boolean isSaving = false; // Flag to prevent infinite loop
        Appointment appointment;
        AppointmentEditExtensionEvents events;
        NoteEditor(AppointmentEditExtensionEvents events)
        {
            this.events = events;
            textField = textFieldFactory.create("appointment.note");
            //Appointment appointment = events.getAppointment();
            //accept( appointment);
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            panel.add(Box.createHorizontalStrut(10));
            label.setText(i18n.getString("appointment.note"));
            panel.add(label);
            panel.add(Box.createHorizontalStrut(10));
            panel.add(textField.getComponent());
            panel.add(Box.createHorizontalGlue()    );
            events.init(this);

            textField.addChangeListener(e->
            {
                if (!isSaving) {
                    String comment = textField.getValue();
                    isSaving = true;
                    try {
                        UndoNoteChange undoCommentChange = new UndoNoteChange(appointment, comment);
                        events.getCommandHistory().storeAndExecute(undoCommentChange);
                    } finally {
                        isSaving = false;
                    }
                }
            });
            /*
            noteField.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    // Do nothing
                }

                @Override
                public void focusLost(FocusEvent e) {
                    // Save the comment when the field loses focus
                    if (!isSaving) {
                        String comment = noteField.getText();
                        isSaving = true;

                        try {
                            UndoNoteChange undoCommentChange = new UndoNoteChange(appointment, comment);
                            events.getCommandHistory().storeAndExecute(undoCommentChange);
                        } finally {
                            isSaving = false;
                        }
                    }
                }
            });
*/

        }


        public JComponent getComponent()
        {
            return panel;
        }

        @Override
        public void accept(Appointment appointment) {
            SwingUtilities.invokeLater(()-> {
                this.appointment = appointment;
                String comment = AppointmentNoteFunctions.getNote(appointment);
                textField.setValue(comment);
            });
        }

        public class UndoNoteChange implements CommandUndo<RuntimeException> {

            final  Appointment appointment;
            final String oldComment;
            final String newComment;

            public UndoNoteChange(Appointment appointment, String newComment) {
                this.appointment = appointment;
                this.newComment = newComment;
                this.oldComment = AppointmentNoteFunctions.getNote(appointment);
            }

            @Override
            public Promise<Void> execute() {
                AppointmentNoteFunctions.setNote(appointment, newComment);
                try {
                    textField.setValue(newComment);
                    events.appointmentChanged();
                } finally {
                    isSaving = false;
                }
                return ResolvedPromise.VOID_PROMISE;
            }

            @Override
            public Promise<Void> undo() {
                AppointmentNoteFunctions.setNote(appointment, oldComment);
                try {
                    textField.setValue(oldComment);
                    events.appointmentChanged();
                } finally {
                    isSaving = false;
                }
                return ResolvedPromise.VOID_PROMISE;
            }

            @Override
            public String getCommandoName() {
                return i18n.getString("appointment.note");
            }
        }
    }


}
