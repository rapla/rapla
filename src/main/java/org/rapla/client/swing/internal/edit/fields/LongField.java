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
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.RaplaResources;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class LongField extends AbstractEditField implements ChangeListener, FocusListener, MultiEditField, SetGetField<Long>{
	JPanel panel;
	RaplaNumber field;
	boolean multipleValues = false; // indicator, shows if multiple different
									// values are shown in this field

	JLabel multipleValuesLabel = new JLabel();
	
	private LongField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface, String fieldName) {
		this(facade, i18n, raplaLocale, logger, ioInterface, (Long)null);
		setFieldName(fieldName);
	}

	private LongField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface) {
        this(facade, i18n, raplaLocale, logger, ioInterface, (Long)null);
    }

	
	private LongField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface, Long minimum)
	{
		super(facade, i18n, raplaLocale, logger);
		panel = new JPanel(){
		    @Override
		    public void setEnabled(boolean enabled)
		    {
		        super.setEnabled(enabled);
		        field.setEnabled(enabled);
		    }
		    
		    @Override
		    public void setBackground(Color bg)
		    {
		        super.setBackground(bg);
		        if(field != null)
		        {
		            field.setBackground(bg);
		        }
		    }
		    
		    @Override
		    public void setForeground(Color fg)
		    {
		        super.setForeground(fg);
		        if(field != null)
	            {
		            field.setForeground(fg);
	            }
		    }
		};
		panel.setLayout(new BorderLayout());
		panel.setOpaque(false);
		field = new RaplaNumber(minimum, minimum, null, minimum == null);
		addCopyPaste(field.getNumberField(), i18n, raplaLocale, ioInterface, logger);
		field.setColumns(8);
		field.addChangeListener(this);
		panel.add(field, BorderLayout.WEST);
		panel.add(multipleValuesLabel, BorderLayout.CENTER);

		field.addFocusListener(this);
	}
	
	public Long getValue()  {
		if (field.getNumber() != null)
			return new Long(field.getNumber().longValue());
		else
			return null;
	}
	
	public Integer getIntValue()  {
		if (field.getNumber() != null)
			return new Integer(field.getNumber().intValue());
		else
			return null;
	}

	public void setValue(Long object) {
		if (object != null) {
			field.setNumber( object);
		} else {
			field.setNumber(null);
		}
	}
	
	public void setValue(Integer object) {
		if (object != null) {
			field.setNumber( object);
		} else {
			field.setNumber(null);
		}
	}

	public void stateChanged(ChangeEvent evt) {
		// if entry was executed: a common value has been set -> change flag, no
		// place holder has to be shown anymore
		if (multipleValues) {
			multipleValues = false;
			multipleValuesLabel.setText("");
		}
		fireContentChanged();
	}

	public JComponent getComponent() {
		return panel;
	}

	public void focusGained(FocusEvent evt) {

		Component focusedComponent = evt.getComponent();
		Component parent = focusedComponent.getParent();
		if (parent instanceof JPanel) {
			((JPanel) parent).scrollRectToVisible(focusedComponent
					.getBounds(null));
		}

	}

	public void focusLost(FocusEvent evt) {

	}

	// implementation for interface MultiEditField
	public boolean hasMultipleValues() {
		return multipleValues;
	}

	// implementation for interface MultiEditField
	public void setFieldForMultipleValues() {
		multipleValues = true;
		// place holder for multiple different values:
		multipleValuesLabel.setText(TextField.getOutputForMultipleValues());
		multipleValuesLabel.setFont(multipleValuesLabel.getFont().deriveFont(Font.ITALIC));
	}
	
    @Singleton
    public static final class LongFieldFactory
    {

        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final IOInterface ioInterface;

        @Inject
        public LongFieldFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface)
        {
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.ioInterface = ioInterface;
        }

        public LongField create(Long minimum)
        {
            return new LongField(facade, i18n, raplaLocale, logger, ioInterface, minimum);
        }

        public LongField create(String fieldName)
        {
            return new LongField(facade, i18n, raplaLocale, logger, ioInterface, fieldName);
        }

        public LongField create()
        {
            return new LongField(facade, i18n, raplaLocale, logger, ioInterface);
        }
    }
}
