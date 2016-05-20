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
package org.rapla.plugin.tableview.client.swing;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ExportMenuExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.scheduler.Promise;

@Extension(provides = ExportMenuExtension.class, id = CSVExportMenu.PLUGIN_ID)
public class CSVExportMenu extends RaplaGUIComponent implements ExportMenuExtension, ActionListener
{
    public static final String PLUGIN_ID = "csv";
	JMenuItem exportEntry;
	private final TableConfig.TableConfigLoader tableConfigLoader;
    private final CalendarSelectionModel model;
    private final IOInterface io;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;

	@Inject
	public CSVExportMenu(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TableConfig.TableConfigLoader tableConfigLoader, CalendarSelectionModel model, IOInterface io, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger);
		this.tableConfigLoader = tableConfigLoader;
        this.model = model;
        this.io = io;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
		exportEntry = new JMenuItem(getString("csv.export"));
        exportEntry.setIcon( raplaImages.getIconFromKey("icon.export") );
        exportEntry.addActionListener(this);
    }
	
    public void actionPerformed(ActionEvent evt)
    {
        try
        {
            export(model);
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
        }
    }
    
	public String getId() {
		return PLUGIN_ID;
	}

	public JMenuItem getMenuElement() {
		return exportEntry;
	}
	
	private static final String LINE_BREAK = "\n"; 
	private static final String CELL_BREAK = ";"; 
	
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void export(final CalendarSelectionModel model) throws RaplaException
    {
        // generates a text file from all filtered events;
        Promise<List<Object>> promise;
        Collection<? extends RaplaTableColumn<?, ?>> columns;
        if (model.getViewId().equals(ReservationTableViewFactory.TABLE_VIEW))
        {
            columns = tableConfigLoader.loadColumns("events", getUser());
            promise = model.queryReservations(model.getTimeIntervall()).thenApply((list) ->
            {
                return new ArrayList<Object>(list);
            });
        }
        else
        {
            columns = tableConfigLoader.loadColumns("appointments", getUser());
            promise = model.getBlocks().thenApply((list) ->
            {
                return new ArrayList<Object>(list);
            });
        }
        promise.thenAccept((objects) ->
        {
            StringBuffer buf = new StringBuffer();
            for (RaplaTableColumn column : columns)
            {
                buf.append(column.getColumnName());
                buf.append(CELL_BREAK);
            }
            for (Object row : objects)
            {
                buf.append(LINE_BREAK);
                for (RaplaTableColumn column : columns)
                {
                    Object value = column.getValue(row);
                    Class columnClass = column.getColumnClass();
                    boolean isDate = columnClass.isAssignableFrom(java.util.Date.class);
                    String formated = "";
                    if (value != null)
                    {
                        if (isDate)
                        {
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            format.setTimeZone(getRaplaLocale().getTimeZone());
                            String timestamp = format.format((java.util.Date) value);
                            formated = timestamp;
                        }
                        else
                        {
                            String escaped = escape(value);
                            formated = escaped;
                        }
                    }
                    buf.append(formated);
                    buf.append(CELL_BREAK);
                }
            }
            byte[] bytes = buf.toString().getBytes();

            DateFormat sdfyyyyMMdd = new SimpleDateFormat("yyyyMMdd");
            final String calendarName = getQuery().getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, getString("rapla.title"));
            String filename = calendarName + "-" + sdfyyyyMMdd.format(model.getStartDate()) + "-" + sdfyyyyMMdd.format(model.getEndDate()) + ".csv";
            if (saveFile(bytes, filename, "csv"))
            {
                exportFinished(getMainComponent());
            }
        }).exceptionally((ex) ->
        {
            dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
            return null;
        });
    }	
	
	 protected boolean exportFinished(Component topLevel) {
			try {
				DialogInterface dlg = dialogUiFactory.create(
				                new SwingPopupContext(topLevel, null)
	                            ,true
	                            ,getString("export")
	                            ,getString("file_saved")
	                            ,new String[] { getString("ok")}
	                            );
				dlg.setIcon("icon.export");
	            dlg.setDefault(0);
	            dlg.start(true);
	            return (dlg.getSelectedIndex() == 0);
			} catch (RaplaException e) {
				return true;
			}

	    }

	private String escape(Object cell) { 
		return cell.toString().replace(LINE_BREAK, " ").replace(CELL_BREAK, " "); 
	}
	
	public boolean saveFile(byte[] content,String filename, String extension) throws RaplaException {
		final Frame frame = (Frame) SwingUtilities.getRoot(getMainComponent());
		try 
		{
			String file = io.saveFile( frame, null, new String[] {extension}, filename, content);
			return file != null;
		} 
		catch (IOException e) 
		{
			throw new RaplaException(e.getMessage(), e);
	    }
	}

	
}

