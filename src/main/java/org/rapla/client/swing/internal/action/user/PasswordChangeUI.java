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
package org.rapla.client.swing.internal.action.user;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

import org.rapla.RaplaResources;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.RaplaWidget;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;


public class PasswordChangeUI extends RaplaGUIComponent
    implements
    RaplaWidget
{
	JPanel superPanel = new JPanel();
    JPanel panel = new JPanel();
    JPanel panel2 = new JPanel();
    GridLayout gridLayout1 = new GridLayout();
     // The Controller for this Dialog

    JLabel label1 = new JLabel();
    JLabel label2 = new JLabel();
    JLabel label3 = new JLabel();

    JPasswordField tf1 = new JPasswordField(10);
    JPasswordField tf2 = new JPasswordField(10);
    JPasswordField tf3 = new JPasswordField(10);

    public PasswordChangeUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        this(facade, i18n, raplaLocale, logger, true);
    }

    public PasswordChangeUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,boolean askForOldPassword) {
        super(facade, i18n, raplaLocale, logger);
        superPanel.setLayout(new BoxLayout(superPanel, BoxLayout.Y_AXIS));
        panel2.add(new JLabel(getString("password_change_info")));
        panel.setLayout(gridLayout1);
        gridLayout1.setRows(askForOldPassword ? 4 : 3);
        gridLayout1.setColumns(2);
        gridLayout1.setHgap(10);
        gridLayout1.setVgap(10);
        if (askForOldPassword) {
            panel.add(label1);
            panel.add(tf1);
        }

        panel.add(label2);
        panel.add(tf2);
        panel.add(label3);
        panel.add(tf3);
        label1.setText(getString("old_password") + ":");
        label2.setText(getString("new_password") + ":");
        label3.setText(getString("password_verification") + ":");

        final JCheckBox showPassword = new JCheckBox(getString("show_password"));
		panel.add( showPassword);
		showPassword.addActionListener(new ActionListener() {		
			public void actionPerformed(ActionEvent e) {
				boolean show = showPassword.isSelected();
				tf1.setEchoChar( show ? ((char) 0): '*');
				tf2.setEchoChar( show ? ((char) 0): '*');
			 	tf3.setEchoChar( show ? ((char) 0): '*');
			}
		});
		 
        superPanel.add(panel);
        superPanel.add(panel2);
    }

    public JComponent getComponent() {
        return superPanel;
    }

    public char[] getOldPassword() {
        return tf1.getPassword();
    }

    public char[] getNewPassword() {
        return tf2.getPassword();
    }

    public char[] getPasswordVerification() {
        return tf3.getPassword();
    }
}









