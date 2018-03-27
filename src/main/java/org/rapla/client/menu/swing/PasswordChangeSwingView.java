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
package org.rapla.client.menu.swing;

import org.rapla.RaplaResources;
import org.rapla.client.menu.PasswordChangeView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


@DefaultImplementation(of=PasswordChangeView.class,context = InjectionContext.swing)
public class PasswordChangeSwingView
    implements
    PasswordChangeView
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

    @Inject
    public PasswordChangeSwingView( RaplaResources i18n) {
        superPanel.setLayout(new BoxLayout(superPanel, BoxLayout.Y_AXIS));
        panel2.add(new JLabel(i18n.getString("password_change_info")));
        panel.setLayout(gridLayout1);
        gridLayout1.setColumns(2);
        gridLayout1.setRows( 4);
        gridLayout1.setHgap(10);
        gridLayout1.setVgap(10);
        panel.add(label1);
        panel.add(tf1);
        panel.add(label2);
        panel.add(tf2);
        panel.add(label3);
        panel.add(tf3);
        label1.setText(i18n.getString("old_password") + ":");
        label2.setText(i18n.getString("new_password") + ":");
        label3.setText(i18n.getString("password_verification") + ":");

        final JCheckBox showPassword = new JCheckBox(i18n.getString("show_password"));
		panel.add( showPassword);
		showPassword.addActionListener(e -> {
            boolean show = showPassword.isSelected();
            tf1.setEchoChar( show ? ((char) 0): '*');
            tf2.setEchoChar( show ? ((char) 0): '*');
             tf3.setEchoChar( show ? ((char) 0): '*');
        });
		 
        superPanel.add(panel);
        superPanel.add(panel2);
    }

    @Override
    public void dontShowOldPassword()
    {
        gridLayout1.setRows( 3);
        panel.remove(label1);
        panel.remove(tf1);
    }

    public JComponent getComponent() {
        return superPanel;
    }

    @Override
    public char[] getOldPassword() {
        return tf1.getPassword();
    }

    @Override
    public char[] getNewPassword() {
        return tf2.getPassword();
    }

    @Override
    public char[] getPasswordVerification() {
        return tf3.getPassword();
    }
}









