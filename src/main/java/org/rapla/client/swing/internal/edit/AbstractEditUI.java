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
package org.rapla.client.swing.internal.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.RaplaResources;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.EditFieldLayout;
import org.rapla.client.swing.internal.edit.fields.EditFieldWithLayout;
import org.rapla.components.layout.TableLayout;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

/** 
 */
public abstract class AbstractEditUI<T> extends RaplaGUIComponent
implements
    EditComponent<T,JComponent>
    ,ChangeListener
{

    protected JPanel editPanel = new JPanel();
    protected List<T> objectList;
    protected List<EditField> fields = Collections.emptyList();

    ArrayList<ChangeListener> listenerList = new ArrayList<ChangeListener>();
    
    public AbstractEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
    {
        super(facade, i18n, raplaLocale, logger);
    }

    public void addChangeListener(ChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.toArray(new ChangeListener[]{});
    }

    protected void fireContentChanged( ) {
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].stateChanged(evt);
        }
    }

    final protected void setFields(Collection<? extends EditField> fields) {
        for (EditField field:fields) {
            field.removeChangeListener(this);
        }
        this.fields = new ArrayList<EditField>(fields);
        for (EditField field:fields) {
            field.addChangeListener(this);
        }
        editPanel.removeAll();
        layout();
        editPanel.revalidate();
    }

    protected void layout() {
        TableLayout tableLayout = new TableLayout();
        editPanel.setLayout(tableLayout);
        tableLayout.insertColumn(0,5);
        tableLayout.insertColumn(1,TableLayout.PREFERRED);
        tableLayout.insertColumn(2,5);
        tableLayout.insertColumn(3,TableLayout.FILL);
        tableLayout.insertColumn(4,5);
        int variableSizedBlocks = 0;
        for (EditField field:fields) {
            
            EditFieldLayout layout = getLayout(field);
            if (layout.isVariableSized())
            {
                variableSizedBlocks ++;
            }
        }
        int row = 0;
        for (EditField field:fields) {
            tableLayout.insertRow(row,5);
            row ++;
            EditFieldLayout layout = getLayout(field);
            if (layout.isVariableSized()) {
                @SuppressWarnings("cast")
                double size = 0.99 / ((double) variableSizedBlocks);
                tableLayout.insertRow(row,size);
            } else{
                tableLayout.insertRow(row,TableLayout.PREFERRED);
            }
            if (layout.isBlock()) {
                editPanel.add("1," + row + ",3," + row+",l", field.getComponent());
            } else {
                editPanel.add("1," + row +",l,c", new JLabel(getFieldName(field) + ":"));
                editPanel.add("3," + row +",l,c", field.getComponent());
            }
            row ++;
        }
        if (variableSizedBlocks == 0) {
            tableLayout.insertRow(row,TableLayout.FILL);
            editPanel.add("0," + row + ",4," + row ,new JLabel(""));
        }
    }

    private EditFieldLayout getLayout(EditField field) 
    {
        if ( field instanceof EditFieldWithLayout)
        {
            return ((EditFieldWithLayout) field).getLayout();
        }
        else
        {
            return new EditFieldLayout();
        }
    }

    final public String getFieldName(EditField field) 
    {
        String fieldName = field.getFieldName();
        if ( fieldName == null)
        {
            return "";
        }
        return fieldName;
    }

    public void setObjects(List<T> o) throws RaplaException {
        this.objectList = o;
        mapFromObjects();
    }

    abstract protected void mapFromObjects() throws RaplaException;

    public List<T> getObjects() {
        return objectList;
    }

    public boolean isBlock() {
        return false;
    }

    public JComponent getComponent() {
        return editPanel;
    }

   

    public void stateChanged(ChangeEvent evt) {
        fireContentChanged();
    }

}

