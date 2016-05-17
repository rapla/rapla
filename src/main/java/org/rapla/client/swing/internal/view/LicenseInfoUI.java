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
package org.rapla.client.swing.internal.view;

import java.awt.Component;
import java.awt.Dimension;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.HTMLView;
import org.rapla.client.RaplaWidget;
import org.rapla.components.xmlbundle.LocaleChangeEvent;
import org.rapla.components.xmlbundle.LocaleChangeListener;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

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
    private final DialogUiFactoryInterface dialogUiFactory;
    private final Provider<LicenseUI> licenseUiProvider;

    @Inject
    public LicenseInfoUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, DialogUiFactoryInterface dialogUiFactory, Provider<LicenseUI> licenseUiProvider) throws RaplaInitializationException{
        super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
        this.licenseUiProvider = licenseUiProvider;
        license = new HTMLView();
        license.addHyperlinkListener(this);
        scrollPane= new JScrollPane(license);
        scrollPane.setOpaque(true);
        scrollPane.setPreferredSize(new Dimension(450, 100));
        scrollPane.setBorder(null);
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
            LicenseUI license =  licenseUiProvider.get();
            DialogInterface dialog = dialogUiFactory.create(new SwingPopupContext(owner, null),modal,license.getComponent(), new String[] {getString("ok")} );
            dialog.setTitle(getString("licensedialog.title"));
            dialog.setSize(600,400);
            if (link.equals("warranty")) {
                dialog.start(true);
                license.getComponent().revalidate();
                license.showBottom();
            } else {
                dialog.start(true);
                license.getComponent().revalidate();
                license.showTop();
            }
        } catch (Exception ex) {
            dialogUiFactory.showException(ex,new SwingPopupContext(owner, null));
        }
    }

}









