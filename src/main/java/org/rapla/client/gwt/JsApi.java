package org.rapla.client.gwt;

import com.google.gwt.core.client.JsDate;
import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.rapla.RaplaResources;
import org.rapla.client.ReservationController;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.menu.MenuInterface;
import org.rapla.client.menu.gwt.VueMenu;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.RaplaTableModel;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.dbrm.RemoteAuthentificationService;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsType
public class JsApi {
    private final RaplaFacade facade;
    private final Logger logger;
    private final ReservationController reservationController;
    private final CalendarSelectionModel calendarModel;
    private final RemoteAuthentificationService remoteAuthentificationService;
    private final RaplaLocale raplaLocale;
    private final ClientFacade clientFacade;
    private final Provider<RaplaBuilder> raplaBuilder;
    private final RaplaResources i18n;
    private final MenuFactory menuFactory;
    private final TableConfig.TableConfigLoader tableConfigLoader;

    @JsIgnore
    @Inject
    public JsApi(ClientFacade facade, Logger logger, ReservationController reservationController, CalendarSelectionModel calendarModel,
            RemoteAuthentificationService remoteAuthentificationService, RaplaLocale raplaLocale, Provider<RaplaBuilder> raplaBuilder, RaplaResources i18n,
            MenuFactory menuFactory, TableConfig.TableConfigLoader tableConfigLoader) {
        this.clientFacade = facade;
        this.i18n = i18n;
        this.menuFactory = menuFactory;
        this.tableConfigLoader = tableConfigLoader;
        this.facade = clientFacade.getRaplaFacade();
        this.logger = logger;
        this.reservationController = reservationController;
        this.calendarModel = calendarModel;
        this.remoteAuthentificationService = remoteAuthentificationService;
        this.raplaLocale = raplaLocale;
        this.raplaBuilder = raplaBuilder;
    }

    public User getUser() throws RaplaException {
        return clientFacade.getUser();
    }

    public RaplaFacade getFacade() {
        return facade;
    }

    public MenuFactory getMenuFactory()
    {
        return menuFactory;
    }

    public CalendarSelectionModel getCalendarModel() {
        return calendarModel;
    }

    public RemoteAuthentificationService getRemoteAuthentification() {
        return remoteAuthentificationService;
    }

    public RaplaLocale getRaplaLocale() {
        return raplaLocale;
    }

    public ReservationController getReservationController() {
        return reservationController;
    }

    public RaplaBuilder createBuilder() {
        return raplaBuilder.get();
    }

    public RaplaResources getI18n() {
        return i18n;
    }

    public void warn(String message)
    {
        logger.warn( message);
    }

    public void info(String message)
    {
        logger.info( message);
    }

    public void error(String message)
    {
        logger.error( message);
    }

    public void debug(String message)
    {
        logger.debug( message);
    }

    public CalendarOptions getCalendarOptions() throws RaplaException {
        return RaplaComponent.getCalendarOptions(getUser(), facade);
    }
    
    public MenuInterface createVueMenu() {
        return new VueMenu();
    }

    public String[] getCalendarNames() throws RaplaException {
        final Preferences preferences = getFacade().getPreferences(getUser());
        RaplaMap<CalendarModelConfiguration> exportMap = preferences.getEntry(AutoExportPlugin.PLUGIN_ENTRY);
        if (exportMap != null) {
            return exportMap.keySet().toArray(new String[]{});
        }
        return new String[]{};
    }

    public JsDate toJsDate(Date date) {
      if (date != null)
        return JsDate.create(date.getTime());
      return null;
    }

    public Object[] toArray(Collection<?> collection) {
        return collection.toArray();
    }
    
    public Object[] streamToArray(Stream<?> stream) {
        return stream.toArray();
    }

    public Set<Object> asSet(Object[] elements) {
        return Arrays.stream(elements).collect(Collectors.toSet());
    }

    public TimeInterval createInterval(Date from, Date to) {
        return new TimeInterval(from, to);
    }

    public Promise<RaplaTableModel> loadTableModel(CalendarSelectionModel model)
    {
        final String viewId = model.getViewId();
        if (viewId.equals("table_appointments"))
        {
            final Supplier<Promise<List<AppointmentBlock>>> initFunction =(()-> model.queryBlocks(model.getTimeIntervall()));
            return loadTableModel("appointments", initFunction);
        }
        else if (viewId.equals("table_events"))
        {
            final Supplier<Promise<List<Reservation>>> initFunction =(()-> model.queryReservations(model.getTimeIntervall()).thenApply(ArrayList::new));
            return loadTableModel("events", initFunction);
        }
        else
        {
            return new ResolvedPromise<>(new RaplaException("No table data found for view "  + viewId));
        }
    }

    private <T> Promise<RaplaTableModel> loadTableModel(String viewId, Supplier<Promise<List<T>>> initFunction)
    {
        RaplaTableModel<T, Object> tableModel;
        try
        {
            User user = getUser();
            List<RaplaTableColumn<T,Object>> raplaTableColumns = tableConfigLoader.loadColumns(viewId, user);
            tableModel = new RaplaTableModel<>(raplaTableColumns);
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
        return initFunction.get().thenApply((blocks)-> tableModel.setObjects( blocks));
    }

}
