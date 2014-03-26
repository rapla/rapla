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

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.UpdateModule;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.OptionPanel;
import org.rapla.gui.RaplaGUIComponent;

public class ConnectionOption extends RaplaGUIComponent implements OptionPanel {
    JPanel panel = new JPanel();
    RaplaNumber seconds = new RaplaNumber(new Double(10),new Double(10),null, false);
    Preferences preferences;
    
    public ConnectionOption(RaplaContext sm) {
        super( sm);
        seconds.getNumberField().setBlockStepSize( 60);
        seconds.getNumberField().setStepSize( 10);
        double pre = TableLayout.PREFERRED;
        double fill = TableLayout.FILL;
        panel.setLayout( new TableLayout(new double[][] {{pre, 5, pre,5, pre}, {pre,fill}}));

        panel.add( new JLabel(getString("refresh") + ": " + getI18n().format("interval.format", "","")),"0,0"  );
        panel.add( seconds,"2,0");
        panel.add( new JLabel(getString("seconds")),"4,0"  );
        addCopyPaste( seconds.getNumberField());
    }

    public JComponent getComponent() {
        return panel;
    }
    public String getName(Locale locale) {
        return getString("connection");
    }

    public void setPreferences( Preferences preferences) {
        this.preferences = preferences;

    }

    public void show() throws RaplaException {
        int delay = preferences.getEntryAsInteger( UpdateModule.REFRESH_INTERVAL_ENTRY, UpdateModule.REFRESH_INTERVAL_DEFAULT);
        seconds.setNumber( new Long(delay / 1000));
    }

    public void commit() {
        int delay = seconds.getNumber().intValue() * 1000;
        preferences.putEntry( UpdateModule.REFRESH_INTERVAL_ENTRY, delay );
    }


}
