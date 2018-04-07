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
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.common.NamedListCellRenderer;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.Collection;
import java.util.HashMap;


@Singleton
public class ExportServiceList   {

    HashMap<Object,ExportService> exporters = new HashMap<>();
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaResources i18n;

    @Inject
    public ExportServiceList(StartupEnvironment startupEnvironment, RaplaResources i18n, IOInterface printInterface, DialogUiFactoryInterface dialogUiFactory) throws
            RaplaInitializationException {
        this.i18n = i18n;
        this.dialogUiFactory = dialogUiFactory;
        boolean applet =startupEnvironment.getStartupMode() == StartupEnvironment.APPLET;
        if (printInterface.supportsPostscriptExport() && !applet) {
            PSExportService exportService = new PSExportService( printInterface, i18n);
            addService("psexport",exportService);
        }
    }

    public Promise<Boolean> export(Printable printable, PageFormat pageFormat, Component parentComponent)
    {
        Collection<ExportService> services = exporters.values();
        Object[] serviceArray = services.toArray();
        @SuppressWarnings("unchecked")
		JList list = new JList(serviceArray);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        panel.add(new JLabel(i18n.getString("weekview.print.choose_export")),BorderLayout.NORTH);
        panel.add(list,BorderLayout.CENTER);
        setRenderer(list);
        list.setSelectedIndex(0);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final SwingPopupContext popupContext = new SwingPopupContext(parentComponent, null);
        DialogInterface dlg = dialogUiFactory.createContentDialog(popupContext, panel,
                                       new String[] {
                                           i18n.getString("export")
                                           ,i18n.getString("cancel")
                                           });
        dlg.setTitle(i18n.getString("weekview.print.choose_export"));
        dlg.getAction(0).setIcon(i18n.getIcon("icon.save"));
        dlg.getAction(1).setIcon(i18n.getIcon("icon.cancel"));
        return dlg.start(true).thenApply( index->
        {
            if (index != 0 || list.getSelectedIndex() == -1) {
                return false;
            }
            ExportService selectedService = (ExportService) serviceArray[list.getSelectedIndex()];
            boolean result = selectedService.export(printable, pageFormat, parentComponent);
            return result;
        });
    }

	@SuppressWarnings("unchecked")
	private void setRenderer(JList list) {
		list.setCellRenderer(new NamedListCellRenderer(i18n.getLocale()));
	}

    public void addService(Object policy,ExportService exportService) {
        exporters.put(policy, exportService);
    }

    public void removeService(Object policy) {
        exporters.remove(policy);
    }

    public ExportService select(Object policy) throws RaplaException {
        ExportService result =  exporters.get(policy);
        if (result == null)
            throw new RaplaException("Export Service not found for key " + policy);
        return result;
    }

    public boolean isSelectable(Object policy) {
        return exporters.get(policy) != null;
    }

    public ExportService[] getServices() {
        return exporters.values().toArray(new ExportService[0]);
    }

}
