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
package org.rapla.gui.internal;

import java.util.Locale;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.OptionPanel;
import org.rapla.gui.RaplaGUIComponent;
public class WarningsOption extends RaplaGUIComponent implements OptionPanel
{
    JPanel panel = new JPanel();
    Preferences preferences;
    JCheckBox showConflictWarningsField = new JCheckBox();

    public WarningsOption(RaplaContext sm) {
        super( sm);
        showConflictWarningsField.setText("");        
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout(new double[][] {{pre, 5,pre}, {pre}}));
        panel.add( new JLabel(getString("warning.conflict")),"0,0");
        panel.add( showConflictWarningsField,"2,0");

    }

    public JComponent getComponent() {
        return panel;
    }
    public String getName(Locale locale) {
        return getString("warnings");
    }

    public void setPreferences( Preferences preferences) {
        this.preferences = preferences;
    }

    public void show() throws RaplaException {
    	// get the options 
        boolean config = preferences.getEntryAsBoolean( CalendarOptionsImpl.SHOW_CONFLICT_WARNING, true);
        showConflictWarningsField.setSelected( config);
    }

    public void commit() {
    	// Save the options
        
        boolean selected = showConflictWarningsField.isSelected();
        preferences.putEntry( CalendarOptionsImpl.SHOW_CONFLICT_WARNING, selected);
	}


}