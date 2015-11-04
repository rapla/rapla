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
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.common.NamedListCellRenderer;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.Collection;
import java.util.HashMap;


@Singleton
public class ExportServiceList   {

    HashMap<Object,ExportService> exporters = new HashMap<Object,ExportService>();
    private final RaplaImages raplaImages;
    private final DialogUiFactory dialogUiFactory;
    private final RaplaResources i18n;

    @Inject
    public ExportServiceList(StartupEnvironment startupEnvironment, RaplaResources i18n, IOInterface printInterface, RaplaImages raplaImages, DialogUiFactory dialogUiFactory) throws RaplaException {
        this.i18n = i18n;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        boolean applet =startupEnvironment.getStartupMode() == StartupEnvironment.APPLET;
        if (printInterface.supportsPostscriptExport() && !applet) {
            PSExportService exportService = new PSExportService( printInterface, i18n);
            addService("psexport",exportService);
        }
    }

    public boolean export(Printable printable,PageFormat pageFormat,Component parentComponent) throws Exception
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
        DialogUI dlg = dialogUiFactory.create(parentComponent,true,panel,
                                       new String[] {
                                           i18n.getString("export")
                                           ,i18n.getString("cancel")
                                           });
        dlg.setTitle(i18n.getString("weekview.print.choose_export"));
        dlg.getButton(0).setIcon(raplaImages.getIconFromKey("icon.save"));
        dlg.getButton(1).setIcon(raplaImages.getIconFromKey("icon.cancel"));
        dlg.start();
        if (dlg.getSelectedIndex() != 0 || list.getSelectedIndex() == -1)
            return false;

        ExportService selectedService = (ExportService)serviceArray[list.getSelectedIndex()];
        boolean result = selectedService.export(printable,pageFormat, parentComponent);
		return result;
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

    public ExportService select(Object policy) throws RaplaContextException {
        ExportService result =  exporters.get(policy);
        if (result == null)
            throw new RaplaContextException("Export Service not found for key " + policy);
        return result;
    }

    public boolean isSelectable(Object policy) {
        return exporters.get(policy) != null;
    }

    public ExportService[] getServices() {
        return exporters.values().toArray(new ExportService[0]);
    }

}
