package org.rapla.plugin.resourcerequest.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.client.swing.IntervalChooserPanel;
import org.rapla.plugin.resourcerequest.ResourceRequestPlugin;
import org.rapla.plugin.resourcerequest.ResourceRequestResources;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.client.swing.SwingTableView;
import org.rapla.plugin.tableview.client.swing.extensionpoints.ReservationSummaryExtension;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Singleton
//@Extension(provides = SwingViewFactory.class, id = ResourceRequestPlugin.RESOURCE_REQUEST_TABLE_VIEW)
public class ResourceRequestViewFactory implements SwingViewFactory
{
    private final Set<ReservationSummaryExtension> reservationSummaryExtensions;
    private final TableConfig.TableConfigLoader tableConfigLoader;
    private final MenuFactory menuFactory;
    private final ReservationController reservationController;
    private final InfoFactory infoFactory;
    private final IntervalChooserPanel dateChooser;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final Logger logger;
    private final RaplaLocale raplaLocale;
    private final RaplaResources i18n;
    private final ResourceRequestResources i18nResourceRequest;
    private final ClientFacade facade;
    private final IOInterface ioInterface;
    private final RaplaMenuBarContainer menuBar;
    private final EditController editController;


    @Inject
    public ResourceRequestViewFactory(ClientFacade facade, RaplaResources i18n, ResourceRequestResources i18nResourceRequest,RaplaLocale raplaLocale, Logger logger,
                                      Set<ReservationSummaryExtension> reservationSummaryExtensions, TableConfig.TableConfigLoader tableConfigLoader, MenuFactory menuFactory,
                                      ReservationController reservationController, InfoFactory infoFactory, IntervalChooserPanel dateChooser,
                                      DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface, RaplaMenuBarContainer menuBar, EditController editController)
    {
        this.facade = facade;
        this.i18n = i18n;
        this.i18nResourceRequest = i18nResourceRequest;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.reservationSummaryExtensions = reservationSummaryExtensions;
        this.tableConfigLoader = tableConfigLoader;
        this.menuFactory = menuFactory;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.dateChooser = dateChooser;
        this.dialogUiFactory = dialogUiFactory;
        this.ioInterface = ioInterface;
        this.menuBar = menuBar;
        this.editController = editController;
    }
    
    @Override
    public boolean isEnabled()
    {
        return true;
    }

    public final static String TABLE_VIEW = ResourceRequestPlugin.RESOURCE_REQUEST_TABLE_VIEW;

    public SwingCalendarView createSwingView(CalendarModel model, boolean editable, boolean printing) throws RaplaException
    {
        Supplier<Promise<List<Reservation>>> initFunction = (() ->model.queryReservations(model.getTimeIntervall()).thenApply((list)->new ArrayList<>(list)));
        final String tableName = TableConfig.EVENTS_VIEW;
        List<RaplaTableColumn<Reservation>> raplaTableColumns = new ArrayList<>(tableConfigLoader.loadColumns(tableName, facade.getUser()));
        raplaTableColumns.remove(2);
        return new SwingTableView<Reservation>(menuBar,facade, i18n, raplaLocale, logger, model, reservationSummaryExtensions, editable, printing, raplaTableColumns, menuFactory,
                editController, reservationController, infoFactory,  dateChooser,  dialogUiFactory, ioInterface, initFunction, tableName);
    }

    public String getViewId()
    {
        return TABLE_VIEW;
    }

    public String getName()
    {
        return i18nResourceRequest.getString("resource_requests");
    }

    Icon icon;

    public Icon getIcon()
    {
        if (icon == null)
        {
            icon = RaplaImages.getIcon("/org/rapla/plugin/resourcerequest/images/resourcerequest.png");
        }
        return icon;
    }

    public String getMenuSortKey()
    {
        return "0";
    }

}