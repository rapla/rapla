/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.swing.internal.print;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.print.PageFormat;
import java.util.Map;


public class PrintAction extends RaplaAction {
    CalendarSelectionModel model;
    PageFormat m_pageFormat;
    final Map<String,SwingViewFactory> factoryMap;
    private final Provider<CalendarPrintDialog> calendarPringDialogProvider;
    private final DialogUiFactoryInterface dialogUiFactory;
    @Inject
    public PrintAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, Map<String, SwingViewFactory> factoryMap,Provider<CalendarPrintDialog> calendarPringDialogProvider, DialogUiFactoryInterface dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.factoryMap = factoryMap;
        this.calendarPringDialogProvider = calendarPringDialogProvider;
        this.dialogUiFactory = dialogUiFactory;
        setEnabled(false);
        putValue(NAME,getString("print"));
        setIcon(i18n.getIcon("icon.print"));
    }

    public void setModel(CalendarSelectionModel settings) {
        this.model = settings;
        setEnabled(settings != null);
    }


    public void setPageFormat(PageFormat pageFormat) {
        m_pageFormat = pageFormat;
    }


    public void actionPerformed() {
        Component parent = getMainComponent();
        try {
            boolean modal = true;
            final CalendarPrintDialog dialog = calendarPringDialogProvider.get();

            dialog.init(modal,factoryMap,model,m_pageFormat);
            final Dimension dimension = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            dialog.setSize(new Dimension(
                                        Math.min(dimension.width,900)
                                        ,Math.min(dimension.height-10,700)
                                        )
                          );
            
            SwingUtilities.invokeLater(() -> dialog.setSize(new Dimension(
                                     Math.min(dimension.width,900)
                                     ,Math.min(dimension.height-11,699)
                                     )
                       )
            );
            dialog.start(false);
            
        } catch (Exception ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(parent, null));
        }
    }
}

