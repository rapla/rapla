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
package org.rapla.plugin.jndi.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.GridLayout;


public class PasswordEnterUI 
    implements
    RaplaWidget
{
    JPanel panel = new JPanel();
    GridLayout gridLayout1 = new GridLayout();
     // The Controller for this Dialog

    JLabel label1 = new JLabel();
    JLabel label2 = new JLabel();
   
    JTextField tf1 = new JTextField(10);
    JPasswordField tf2 = new JPasswordField(10);

    
    public PasswordEnterUI(RaplaResources i18n) {
        panel.setLayout(gridLayout1);
        gridLayout1.setRows( 2);
        gridLayout1.setColumns(2);
        gridLayout1.setHgap(10);
        gridLayout1.setVgap(10);
        panel.add(label1);
        panel.add(tf1);
        panel.add(label2);
        panel.add(tf2);
        label1.setText(i18n.getString("username") + ":");
        label2.setText(i18n.getString("password") + ":");
    }

    public JComponent getComponent() {
        return panel;
    }

    public String getUsername() {
        return tf1.getText();
    }

    public char[] getNewPassword() {
        return tf2.getPassword();
    }
}









