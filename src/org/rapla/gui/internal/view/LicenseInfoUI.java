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
package org.rapla.gui.internal.view;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.rapla.components.xmlbundle.LocaleChangeEvent;
import org.rapla.components.xmlbundle.LocaleChangeListener;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.HTMLView;
import org.rapla.gui.toolkit.RaplaWidget;

final public class LicenseInfoUI
    extends
        RaplaGUIComponent
    implements
        HyperlinkListener
        ,RaplaWidget
        ,LocaleChangeListener
{
    JScrollPane scrollPane;
    HTMLView license;
    LocaleSelector localeSelector;

    public LicenseInfoUI(RaplaContext context) throws RaplaException  {
        super( context);
        license = new HTMLView();
        license.addHyperlinkListener(this);
        scrollPane= new JScrollPane(license);
        scrollPane.setOpaque(true);
        scrollPane.setPreferredSize(new Dimension(450, 100));
        scrollPane.setBorder(null);
        localeSelector = context.lookup( LocaleSelector.class);
        localeSelector.addLocaleChangeListener(this);
        setLocale();
    }

    public void localeChanged(LocaleChangeEvent evt) {
        setLocale();
        scrollPane.invalidate();
        scrollPane.repaint();
    }

    private void setLocale() {
        license.setBody(getString("license.text"));
    }

    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String link = e.getDescription();
            viewLicense(getComponent(), false, link);
        }
    }

    public JComponent getComponent() {
        return scrollPane;
    }

    public void viewLicense(Component owner,boolean modal,String link) {
        try {
            LicenseUI license =  new LicenseUI( getContext());
            DialogUI dialog = DialogUI.create(getContext(),owner,modal,license.getComponent(), new String[] {getString("ok")} );
            dialog.setTitle(getString("licensedialog.title"));
            dialog.setSize(600,400);
            if (link.equals("warranty")) {
                dialog.start();
                license.getComponent().revalidate();
                license.showBottom();
            } else {
                dialog.start();
                license.getComponent().revalidate();
                license.showTop();
            }
        } catch (Exception ex) {
            showException(ex,owner);
        }
    }

}









