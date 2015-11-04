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

import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.rapla.RaplaResources;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;


public class BooleanField extends AbstractEditField implements ActionListener, FocusListener, MultiEditField, SetGetField<Boolean>
{
    JPanel panel = new JPanel();
    JRadioButton field1 = new JRadioButton();
    JRadioButton field2 = new JRadioButton();
    ButtonGroup group = new ButtonGroup();
	boolean multipleValues; // indicator, shows if multiple different values are
	// shown in this field

	JLabel multipleValuesLabel = new JLabel();
	
    public BooleanField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, String fieldName) 
    {
        this(facade, i18n, raplaLocale, logger);
        setFieldName( fieldName );
    }

    public BooleanField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) 
    {
        super(facade, i18n, raplaLocale, logger);
        field1.setOpaque( false );
        field2.setOpaque( false );
        panel.setOpaque( false );
        panel.setLayout( new BoxLayout(panel,BoxLayout.X_AXIS) );
        panel.add( field1 );
        panel.add( field2 );
        panel.add(multipleValuesLabel);
        group.add( field1 );
        group.add( field2 );

        //field2.setSelected( true );
        field1.addActionListener(this);
        field2.addActionListener(this);

        field1.setText(getString("yes"));
        field2.setText(getString("no"));
        field1.addFocusListener(this);

    }

    public Boolean getValue() {
        if (field1.isSelected())
        {
            return Boolean.TRUE;
        }
        if (field2.isSelected())
        {
            return Boolean.FALSE;
        }
        return null;
    }

    public void setValue(Boolean object) {
        if ( object == null)
        {
            field1.setSelected( false );
            field2.setSelected( false );
        }
        else
        {
            boolean selected = object.booleanValue();
            field1.setSelected(selected);
            field2.setSelected(!selected);
        }
    }

    public void actionPerformed(ActionEvent evt) {
    	// once an action is executed, the field shows a common value
		multipleValues = false;
		multipleValuesLabel.setText("");
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
    
    public void setFieldForMultipleValues() {
		// if multiple different values should be shown, no RadioButton is
		// activated (instead a place holder)
		group.clearSelection();
		multipleValues = true;
		multipleValuesLabel.setText(TextField.getOutputForMultipleValues());
		multipleValuesLabel.setFont(multipleValuesLabel.getFont().deriveFont(Font.ITALIC));
    }

	public boolean hasMultipleValues() {
		return multipleValues;
	}
	
    @Singleton
    public static class BooleanFieldFactory
    {

        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;

        @Inject
        public BooleanFieldFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
        {
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
        }

        public BooleanField create()
        {
            return new BooleanField(facade, i18n, raplaLocale, logger);
        }

        public BooleanField create(String fieldName)
        {
            return new BooleanField(facade, i18n, raplaLocale, logger, fieldName);
        }

    }
}