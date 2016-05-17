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
package org.rapla.client.swing.internal.edit.fields;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.AbstractCellEditor;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.MultiLanguageName;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class MultiLanguageField extends AbstractEditField implements ChangeListener, ActionListener, CellEditorListener, SetGetField<MultiLanguageName>
{
    JPanel panel = new JPanel();
    TextField textField;
    RaplaButton button = new RaplaButton(RaplaButton.SMALL);
    MultiLanguageName name = new MultiLanguageName();
    MultiLanguageEditorDialog editorDialog;

    String[] availableLanguages;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final IOInterface ioInterface;

    private MultiLanguageField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, RaplaImages raplaImages,
            IOInterface ioInterface, DialogUiFactoryInterface dialogUiFactory, TextFieldFactory textFieldFactory, String fieldName)
    {
        this(facade, i18n, raplaLocale, logger, raplaImages, ioInterface, dialogUiFactory, textFieldFactory);
        setFieldName(fieldName);
    }

    private MultiLanguageField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, RaplaImages raplaImages,
            IOInterface ioInterface, DialogUiFactoryInterface dialogUiFactory, TextFieldFactory textFieldFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.ioInterface = ioInterface;
        this.dialogUiFactory = dialogUiFactory;
        textField = textFieldFactory.create("name");
        availableLanguages = getRaplaLocale().getAvailableLanguages().toArray(new String[0]);
        panel.setLayout(new BorderLayout());
        panel.add(textField.getComponent(), BorderLayout.CENTER);
        panel.add(button, BorderLayout.EAST);
        button.addActionListener(this);
        button.setIcon(raplaImages.getIconFromKey("icon.language-select"));
        textField.addChangeListener(this);
    }

    public void requestFocus()
    {
        textField.getComponent().requestFocus();
    }

    public void selectAll()
    {
        textField.selectAll();
    }

    public void stateChanged(ChangeEvent e)
    {
        if (name != null)
        {
            name.setName(getI18n().getLang(), textField.getValue());
            fireContentChanged();
        }
    }

    public void actionPerformed(ActionEvent evt)
    {
        editorDialog = new MultiLanguageEditorDialog(button);
        editorDialog.addCellEditorListener(this);
        editorDialog.setEditorValue(name);
        try
        {
            editorDialog.show();
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
        }
    }

    public void editingStopped(ChangeEvent e)
    {
        setValue((MultiLanguageName) editorDialog.getEditorValue());
        fireContentChanged();
    }

    public void editingCanceled(ChangeEvent e)
    {
    }

    public MultiLanguageName getValue()
    {
        return this.name;
    }

    public void setValue(MultiLanguageName object)
    {
        this.name = object;
        textField.setValue(name.getName(getI18n().getLang()));
    }

    public JComponent getComponent()
    {
        return panel;
    }

    class MultiLanguageEditorDialog extends AbstractCellEditor
    {
        private static final long serialVersionUID = 1L;

        JTable table = new JTable();
        JLabel label = new JLabel();
        JPanel comp = new JPanel();
        MultiLanguageName editorValue;
        Component owner;

        MultiLanguageEditorDialog(JComponent owner)
        {
            this.owner = owner;
            table.setPreferredScrollableViewportSize(new Dimension(300, 200));
            comp.setLayout(new BorderLayout());
            comp.add(label, BorderLayout.NORTH);
            comp.add(new JScrollPane(table), BorderLayout.CENTER);
        }

        public void setEditorValue(Object value)
        {
            this.editorValue = (MultiLanguageName) value;
            table.setModel(new TranslationTableModel(editorValue));
            table.getColumnModel().getColumn(0).setPreferredWidth(30);
            table.getColumnModel().getColumn(1).setPreferredWidth(200);
            label.setText(getI18n().format("translation.format", editorValue));
        }

        public Object getEditorValue()
        {
            return editorValue;
        }

        public Object getCellEditorValue()
        {
            return getEditorValue();
        }

        public void show() throws RaplaException
        {
            DialogInterface dlg = dialogUiFactory.create(new SwingPopupContext(owner, null), true, comp, new String[] { getString("ok"), getString("cancel") });
            dlg.setTitle(getString("translation"));
            // Workaround for Bug ID  4480264 on developer.java.sun.com
            if (table.getRowCount() > 0)
            {
                table.editCellAt(0, 0);
                table.editCellAt(0, 1);
            }
            dlg.start(true);
            if (dlg.getSelectedIndex() == 0)
            {
                for (int i = 0; i < availableLanguages.length; i++)
                {
                    String value = (String) table.getValueAt(i, 1);
                    if (value != null)
                        editorValue.setName(availableLanguages[i], value);
                }
                if (table.isEditing())
                {
                    if (table.getEditingColumn() == 1)
                    {
                        JTextField textField = (JTextField) table.getEditorComponent();
                        addCopyPaste(textField, getI18n(), getRaplaLocale(), ioInterface, getLogger());
                        int row = table.getEditingRow();
                        String value = textField.getText();
                        editorValue.setName(availableLanguages[row], value);
                    }
                }
                fireEditingStopped();
            }
            else
            {
                fireEditingCanceled();
            }
        }
    }

    class TranslationTableModel extends DefaultTableModel
    {
        private static final long serialVersionUID = 1L;

        public TranslationTableModel(MultiLanguageName name)
        {
            super();
            addColumn(getString("language"));
            addColumn(getString("translation"));
            Collection<String> trans = name.getAvailableLanguages();
            for (int i = 0; i < availableLanguages.length; i++)
            {
                String lang = availableLanguages[i];
                String[] row = new String[2];
                row[0] = lang;
                row[1] = trans.contains(lang) ? name.getName(lang) : "";
                addRow(row);
            }
        }

        public boolean isCellEditable(int row, int col)
        {
            //Note that the data/cell address is constant,
            //no matter where the cell appears onscreen.
            return col >= 1;
        }
    }

    @Singleton
    public static class MultiLanguageFieldFactory
    {

        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final RaplaImages raplaImages;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final TextFieldFactory textFieldFactory;
        private final IOInterface ioInterface;

        @Inject
        public MultiLanguageFieldFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, RaplaImages raplaImages,
                DialogUiFactoryInterface dialogUiFactory, TextFieldFactory textFieldFactory, IOInterface ioInterface)
        {
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.raplaImages = raplaImages;
            this.dialogUiFactory = dialogUiFactory;
            this.textFieldFactory = textFieldFactory;
            this.ioInterface = ioInterface;
        }

        public MultiLanguageField create()
        {
            return new MultiLanguageField(facade, i18n, raplaLocale, logger, raplaImages, ioInterface, dialogUiFactory, textFieldFactory);
        }

        public MultiLanguageField create(String fieldName)
        {
            return new MultiLanguageField(facade, i18n, raplaLocale, logger, raplaImages, ioInterface, dialogUiFactory, textFieldFactory, fieldName);
        }
    }

}
