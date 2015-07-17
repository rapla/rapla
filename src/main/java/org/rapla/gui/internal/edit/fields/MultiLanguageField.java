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
package org.rapla.gui.internal.edit.fields;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

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

import org.rapla.entities.MultiLanguageName;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.RaplaButton;

public class MultiLanguageField extends AbstractEditField implements ChangeListener, ActionListener,CellEditorListener, SetGetField<MultiLanguageName> {
    JPanel panel = new JPanel();
    TextField textField;
    RaplaButton button = new RaplaButton(RaplaButton.SMALL);
    MultiLanguageName name = new MultiLanguageName();
    MultiLanguageEditorDialog editorDialog;

    String[] availableLanguages;

    public MultiLanguageField(RaplaContext context, String fieldName) 
    {
        this(context);
        setFieldName(fieldName);
    }

    public MultiLanguageField(RaplaContext context) 
    {
        super( context);
        textField = new TextField(context, "name");
        availableLanguages = getRaplaLocale().getAvailableLanguages();
        panel.setLayout( new BorderLayout() );
        panel.add( textField.getComponent(), BorderLayout.CENTER );
        panel.add( button, BorderLayout.EAST );
        button.addActionListener( this );
        button.setIcon( getIcon("icon.language-select") );
        textField.addChangeListener( this );
    }

    public void requestFocus() {
        textField.getComponent().requestFocus();
    }
    
    public void selectAll()
    {
    	textField.selectAll();
    }

    public void stateChanged(ChangeEvent e) {
        if (name != null) {
            name.setName(getI18n().getLang(),textField.getValue());
            fireContentChanged();
        }
    }

    public void actionPerformed(ActionEvent evt) {
        editorDialog = new MultiLanguageEditorDialog( button );
        editorDialog.addCellEditorListener( this );
        editorDialog.setEditorValue( name );
        try {
			editorDialog.show();
		} catch (RaplaException ex) {
			showException( ex, getComponent());
		}
    }

    public void editingStopped(ChangeEvent e) {
        setValue((MultiLanguageName) editorDialog.getEditorValue());
        fireContentChanged();
    }

    public void editingCanceled(ChangeEvent e) {
    }

    public MultiLanguageName getValue() {
        return this.name;
    }

    public void setValue(MultiLanguageName object) {
        this.name = object;
        textField.setValue(name.getName(getI18n().getLang()));
    }

    
    public JComponent getComponent() {
        return panel;
    }

    class MultiLanguageEditorDialog extends AbstractCellEditor {
        private static final long serialVersionUID = 1L;

        JTable table = new JTable();
        JLabel label = new JLabel();
        JPanel comp = new JPanel();
        MultiLanguageName editorValue;
        Component owner;
        MultiLanguageEditorDialog(JComponent owner) {
            this.owner = owner;
            table.setPreferredScrollableViewportSize(new Dimension(300, 200));
            comp.setLayout(new BorderLayout());
            comp.add(label,BorderLayout.NORTH);
            comp.add(new JScrollPane(table),BorderLayout.CENTER);
        }

        public void setEditorValue(Object value) {
            this.editorValue = (MultiLanguageName)value;
            table.setModel(new TranslationTableModel(editorValue));
            table.getColumnModel().getColumn(0).setPreferredWidth(30);
            table.getColumnModel().getColumn(1).setPreferredWidth(200);
            label.setText(getI18n().format("translation.format", editorValue));
        }

        public Object getEditorValue() {
            return editorValue;
        }

        public Object getCellEditorValue() {
            return getEditorValue();
        }

        public void show() throws RaplaException {
            DialogUI dlg = DialogUI.create(getContext(),owner,true,comp,new String[] { getString("ok"),getString("cancel")});
            dlg.setTitle(getString("translation"));
            // Workaround for Bug ID  4480264 on developer.java.sun.com
            if (table.getRowCount() > 0 ) {
                table.editCellAt(0, 0);
                table.editCellAt(0, 1);
            }
            dlg.start();
            if (dlg.getSelectedIndex() == 0) {
                for (int i=0;i<availableLanguages.length;i++) {
                    String value = (String)table.getValueAt(i,1);
                    if (value != null)
                        editorValue.setName(availableLanguages[i],value);
                }
                if (table.isEditing()) {
                    if (table.getEditingColumn() == 1) {
                        JTextField textField = (JTextField) table.getEditorComponent();
                        addCopyPaste( textField);
                        int row = table.getEditingRow();
                        String value = textField.getText();
                        editorValue.setName(availableLanguages[row],value);
                    }
            }
                fireEditingStopped();
            } else {
                fireEditingCanceled();
            }
        }
    }

    class TranslationTableModel extends DefaultTableModel {
        private static final long serialVersionUID = 1L;

        public TranslationTableModel(MultiLanguageName name) {
            super();
            addColumn(getString("language"));
            addColumn(getString("translation"));
            Collection<String> trans = name.getAvailableLanguages();
            for (int i=0;i<availableLanguages.length;i++) {
                String lang = availableLanguages[i];
                String[] row = new String[2];
                row[0] = lang;
                row[1] = trans.contains(lang) ? name.getName(lang): "";
                addRow(row);
            }
        }

        public boolean isCellEditable(int row, int col) {
            //Note that the data/cell address is constant,
            //no matter where the cell appears onscreen.
            if (col < 1) {
                return false;
            } else {
                return true;
            }
        }
    }

}

