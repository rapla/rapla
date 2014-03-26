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
package org.rapla.gui.internal.print;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.Collection;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import org.rapla.components.iolayer.IOInterface;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.common.NamedListCellRenderer;
import org.rapla.gui.toolkit.DialogUI;


public class ExportServiceList extends RaplaGUIComponent  {

    HashMap<Object,ExportService> exporters = new HashMap<Object,ExportService>();
    /**
     * @param sm
     * @throws RaplaException
     */
    public ExportServiceList(RaplaContext sm) throws RaplaException {
        super(sm);
        IOInterface printInterface =  getService( IOInterface.class);
        boolean applet =(getService(StartupEnvironment.class)).getStartupMode() == StartupEnvironment.APPLET;
        if (printInterface.supportsPostscriptExport() && !applet) {
            PSExportService exportService = new PSExportService(getContext());
            addService("psexport",exportService);
        }
        
        if (!applet) {
        	 PDFExportService exportService = new PDFExportService(getContext());
             addService("pdf",exportService);
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
        panel.add(new JLabel(getString("weekview.print.choose_export")),BorderLayout.NORTH);
        panel.add(list,BorderLayout.CENTER);
        setRenderer(list);
        list.setSelectedIndex(0);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DialogUI dlg = DialogUI.create(getContext(),parentComponent,true,panel,
                                       new String[] {
                                           getString("export")
                                           ,getString("cancel")
                                           });
        dlg.setTitle(getString("weekview.print.choose_export"));
        dlg.getButton(0).setIcon(getIcon("icon.save"));
        dlg.getButton(1).setIcon(getIcon("icon.cancel"));
        dlg.start();
        if (dlg.getSelectedIndex() != 0 || list.getSelectedIndex() == -1)
            return false;

        ExportService selectedService = (ExportService)serviceArray[list.getSelectedIndex()];
        boolean result = selectedService.export(printable,pageFormat, parentComponent);
		return result;
    }

	@SuppressWarnings("unchecked")
	private void setRenderer(JList list) {
		list.setCellRenderer(new NamedListCellRenderer(getI18n().getLocale()));
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
