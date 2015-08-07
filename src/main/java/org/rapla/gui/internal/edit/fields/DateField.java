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
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Date;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.framework.RaplaContext;

public class DateField extends AbstractEditField implements DateChangeListener, FocusListener, SetGetField<Date> ,MultiEditField{
    RaplaCalendar field;
    JPanel panel;
    boolean multipleValues = false; // Indikator, ob mehrere verschiedene Werte ueber dieses Feld angezeigt werden
    
    JLabel multipleValuesLabel = new JLabel();
    
    public DateField(RaplaContext context,String fieldName) {
        this( context);
        setFieldName(fieldName);
    }
    public DateField(RaplaContext context) {
        super( context);
        panel = new JPanel();
        field = createRaplaCalendar();
        panel.setLayout(new BorderLayout());
        panel.add(field,BorderLayout.WEST);
        panel.add( multipleValuesLabel, BorderLayout.CENTER);
        panel.setOpaque( false );
        field.setNullValuePossible( true);
        field.addDateChangeListener(this);
        field.addFocusListener(this);
    }

    public Date getValue() {
        return field.getDate();
    }
    public void setValue(Date date) {
//    	//check if standard-value exists and is a Date-Object
//    	if(object instanceof Date)
//    		 date = (Date) object;
//    	//if it's not a Date-Object, set the current Date as Standart
//    	else
//    		date = new Date();
        field.setDate(date);
    }

    public RaplaCalendar getCalendar() {
        return field;
    }

    public void dateChanged(DateChangeEvent evt) {
//		Eingabe wurde getaetigt: einheitlicher Werte wurde gesetzt => Flag aendern, da kein Platzhalter mehr angezeigt wird
    	if(multipleValues){
    		multipleValues = false;
    	}
        fireContentChanged();
    }

    public JComponent getComponent() {
        return panel;
    }

    public void focusGained(FocusEvent evt) {
		Component focusedComponent = evt.getComponent(); 
		Component  parent = focusedComponent.getParent();
		if(parent instanceof JPanel) {
			((JPanel)parent).scrollRectToVisible(focusedComponent.getBounds(null)); 
		}
    }
    
    public void focusLost(FocusEvent evt) {

    }
    
//	Implementierung fuer Interface MultiEditField
	public boolean hasMultipleValues() {
		return multipleValues;
	}

//	Implementierung fuer Interface MultiEditField
	public void setFieldForMultipleValues() {
		multipleValues = true;
		multipleValuesLabel.setText(TextField.getOutputForMultipleValues());
        multipleValuesLabel.setFont(multipleValuesLabel.getFont().deriveFont(Font.ITALIC));
        field.setDate( null);
	}
}

