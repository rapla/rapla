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

import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.HTMLView;
import org.rapla.components.i18n.LocaleChangeEvent;
import org.rapla.components.i18n.LocaleChangeListener;
import org.rapla.framework.RaplaInitializationException;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.Component;
import java.awt.Dimension;

final public class LicenseInfoUI
   implements
        HyperlinkListener
        ,RaplaWidget
        ,LocaleChangeListener
{
    JScrollPane scrollPane;
    HTMLView license;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final Provider<LicenseUI> licenseUiProvider;
    private final RaplaSystemInfo systemInfoI18n;
    private final RaplaResources i18n;

    @Inject
    public LicenseInfoUI(RaplaResources i18n,RaplaSystemInfo systemInfoI18n,  DialogUiFactoryInterface dialogUiFactory, Provider<LicenseUI> licenseUiProvider) throws RaplaInitializationException{
        this.dialogUiFactory = dialogUiFactory;
        this.licenseUiProvider = licenseUiProvider;
        this.systemInfoI18n = systemInfoI18n;
        this.i18n = i18n;
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
        license.setBody(systemInfoI18n.getString("license.text"));
    }

    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String link = e.getDescription();
            viewLicense(getComponent(),  link);
        }
    }

    public JComponent getComponent() {
        return scrollPane;
    }

    public void viewLicense(Component owner,String link) {
        final SwingPopupContext popupContext = new SwingPopupContext(owner, null);
        try {
            LicenseUI license =  licenseUiProvider.get();
            final JComponent component = license.getComponent();
            component.setSize(600,400);

            DialogInterface dialog = dialogUiFactory.createContentDialog(popupContext, component, new String[] {i18n.getString("ok")} );
            dialog.setTitle(systemInfoI18n.getString("licensedialog.title"));
            if (link.equals("warranty")) {
                dialog.start(true);
                component.revalidate();
                license.showBottom();
            } else {
                dialog.start(true);
                component.revalidate();
                license.showTop();
            }
        } catch (Exception ex) {
            dialogUiFactory.showException(ex, popupContext);
        }
    }

}









