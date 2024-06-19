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
package org.rapla.plugin.csvexport.client.swing;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ExportMenuExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.User;
import org.rapla.entities.domain.ReservationStartComparator;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.csvexport.CSVExportPlugin;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.RaplaTableModel;
import org.rapla.plugin.tableview.client.swing.AppointmentTableViewFactory;
import org.rapla.plugin.tableview.client.swing.AppointmentsPerDayViewFactory;
import org.rapla.plugin.tableview.client.swing.ReservationTableViewFactory;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.*;

@Extension(provides = ExportMenuExtension.class, id = CSVExportPlugin.PLUGIN_ID)
public class CSVExportMenu extends RaplaGUIComponent implements ExportMenuExtension, ActionListener {
    JMenuItem exportEntry;
    private final TableConfig.TableConfigLoader tableConfigLoader;
    private final CalendarSelectionModel model;
    private final IOInterface io;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public CSVExportMenu(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TableConfig.TableConfigLoader tableConfigLoader, CalendarSelectionModel model, IOInterface io, DialogUiFactoryInterface dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.tableConfigLoader = tableConfigLoader;
        this.model = model;
        this.io = io;
        this.dialogUiFactory = dialogUiFactory;
        exportEntry = new JMenuItem(getString("csv.export"));
        exportEntry.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.export")));
        exportEntry.addActionListener(this);
    }

    public void actionPerformed(ActionEvent evt) {
        try {
            export(model);
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
        }
    }

    @Override
    public String getId() {
        return CSVExportPlugin.PLUGIN_ID;
    }

    @Override
    public JMenuItem getComponent() {
        return exportEntry;
    }

    private static final String LINE_BREAK = "\n";
    private static final String CELL_BREAK = ";";

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void export(final CalendarSelectionModel model) throws RaplaException {
        // generates a text file from all filtered events;
        Promise<List<Object>> promise;

        final String viewId = model.getViewId();
        final String tableViewName;
        final Comparator fallbackComparator;
        if (viewId.equals(ReservationTableViewFactory.TABLE_VIEW)) {
            promise = model.queryReservations(model.getTimeIntervall()).thenApply((list) ->
                    new ArrayList<>(list));
            fallbackComparator = new ReservationStartComparator(getRaplaLocale().getLocale());
            tableViewName = TableConfig.EVENTS_VIEW;
        } else if (viewId.equals(AppointmentTableViewFactory.TABLE_VIEW)) {
            tableViewName = TableConfig.APPOINTMENTS_VIEW;
            fallbackComparator = Comparator.naturalOrder();
            promise = model.queryBlocks(model.getTimeIntervall()).thenApply((list) -> new ArrayList<>(list));
        } else if (viewId.equals(AppointmentsPerDayViewFactory.TABLE_VIEW)) {
            tableViewName = TableConfig.APPOINTMENTS_PER_DAY_VIEW;
            fallbackComparator = Comparator.naturalOrder();
            promise = model.queryBlocks(model.getTimeIntervall()).thenApply((list) ->
                    new ArrayList<>(list));
        } else {
            throw new RaplaException(i18n.getString("error.cvs_export_only_works_with_tableviews"));
        }
        promise.thenCompose((objects) ->
        {
            String buf = getCSV(model, tableViewName, objects, fallbackComparator);
            byte[] bytes = buf.getBytes();
            final String filename = model.getFilename() + ".csv";
            if (saveFile(bytes, filename, "csv")) {
                return exportFinished(getMainComponent());
            } else {
                return ResolvedPromise.VOID_PROMISE;
            }
        }).exceptionally((ex) ->
                dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null))
        );
    }

    @NotNull
    private <T,C> String  getCSV(CalendarSelectionModel model, String tableViewName, List<T> objects, Comparator<T> fallBackComparator) throws RaplaException {
        final User user = getUser();
        List< RaplaTableColumn<T>> columnPlugins = tableConfigLoader.loadColumns(tableViewName, user);
        if (tableViewName.equals(TableConfig.APPOINTMENTS_PER_DAY_VIEW)) {
            List columnPluginsPlusDate = new ArrayList(columnPlugins);
            columnPluginsPlusDate.add(0, tableConfigLoader.createDateColumn("appointment_per_date_date", user));
            columnPlugins = columnPluginsPlusDate;
        }
        Map<RaplaTableColumn<T>, Integer> sortDirections = RaplaTableModel.getSortDirections(model,columnPlugins, tableViewName);
        String contextAnnotationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
        final List<T> rows = RaplaTableModel.sortRows(objects, sortDirections, fallBackComparator, contextAnnotationName);
        return RaplaTableModel.getCSV(columnPlugins, rows, contextAnnotationName, false);
    }

    protected Promise<Void> exportFinished(Component topLevel) {
        DialogInterface dlg = dialogUiFactory.createTextDialog(
                new SwingPopupContext(topLevel, null)
                , getString("export")
                , getString("file_saved")
                , new String[]{getString("ok")}
        );
        dlg.setIcon(i18n.getIcon("icon.export"));
        dlg.setDefault(0);
        return dlg.start(true).thenApply((index) -> null);
    }

    @Override
    public boolean isEnabled() {
        try {
           return getFacade().getSystemPreferences().getEntryAsBoolean(CSVExportPlugin.ENABLED, false);
        } catch (RaplaException e) {
            return false;
        }
    }

    @Override
    public void setEnabled(boolean b) {

    }

    public boolean saveFile(byte[] content, String filename, String extension) throws RaplaException {
        final Frame frame = (Frame) SwingUtilities.getRoot(getMainComponent());
        try {
            String file = io.saveFile(frame, null, new String[]{extension}, filename, content);
            return file != null;
        } catch (IOException e) {
            throw new RaplaException(e.getMessage(), e);
        }
    }


}

