package org.rapla.client.gwt;

import com.google.gwt.core.client.JsDate;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.IsWidget;
import jsinterop.annotations.JsIgnore;
import org.rapla.RaplaResources;
import org.rapla.client.Application;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.gwt.VueDialog;
import org.rapla.client.dialog.gwt.components.VueLabel;
import org.rapla.client.dialog.gwt.components.VueLayout;
import org.rapla.client.dialog.gwt.components.VueTree;
import org.rapla.client.dialog.gwt.components.VueTreeNode;
import org.rapla.client.gwt.components.util.GWTDateUtils;
import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.menu.MenuFactory;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.storage.ReferenceInfo;
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
import org.rapla.plugin.weekview.client.weekview.CalendarWeekViewPresenter;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Provider;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsApi {

  public final RaplaFacade facade;
  private final Logger logger;
  public final CalendarSelectionModel calendarModel;
  public final RaplaLocale locale;
  private final ClientFacade clientFacade;
  private final Provider<RaplaBuilder> raplaBuilder;
  public final RaplaResources i18n;
  public final MenuFactory menuFactory;
  private final TableConfig.TableConfigLoader tableConfigLoader;
  public final Application application;
  private final ReservationController reservationController; // TODO: needed?
  public final AppointmentFormater appointmentFormater;
  public final TreeFactoryImpl treeFactory;
  public final CalendarWeekViewPresenter weekViewPresenter;

  @JsIgnore
  @Inject
  public JsApi(Provider<Application> application,
               ClientFacade facade,
               Logger logger,
               ReservationController reservationController,
               CalendarSelectionModel calendarModel,
               RaplaLocale locale,
               Provider<RaplaBuilder> raplaBuilder,
               RaplaResources i18n,
               MenuFactory menuFactory,
               TableConfig.TableConfigLoader tableConfigLoader,
               TreeFactoryImpl treeFactory,
               AppointmentFormater appointmentFormater,
              CalendarWeekViewPresenter weekViewPresenter
  ) {
    this.clientFacade = facade;
    this.i18n = i18n;
    this.application = application.get();
    this.menuFactory = menuFactory;
    this.tableConfigLoader = tableConfigLoader;
    this.facade = clientFacade.getRaplaFacade();
    this.logger = logger;
    this.reservationController = reservationController;
    this.calendarModel = calendarModel;
    this.locale = locale;
    this.raplaBuilder = raplaBuilder;
    this.appointmentFormater = appointmentFormater;
    this.treeFactory = treeFactory;
    this.weekViewPresenter = weekViewPresenter;
  }

  public ReservationController getReservationController() {
    return reservationController;
  }

  public RaplaBuilder createBuilder() {
    return raplaBuilder.get();
  }

  public void warn(String message) {
    logger.warn(message);
  }

  public void info(String message) {
    logger.info(message);
  }

  public void error(String message) {
    logger.error(message);
  }

  public void debug(String message) {
    logger.debug(message);
  }

  public CalendarOptions getCalendarOptions() throws RaplaException {
    return RaplaComponent.getCalendarOptions(getUser(), facade);
  }

  public Element updateWeekview() throws RaplaException
  {
      weekViewPresenter.updateContent();
      return ((IsWidget) weekViewPresenter.provideContent()).asWidget().getElement();
  }

  public void initWeekview() throws RaplaException
  {
    Element coreDiv = DOM.getElementById("weekview");
    final Element isWidget = updateWeekview();
    coreDiv.appendChild( isWidget );
  }


  public User getUser() throws RaplaException {
    return clientFacade.getUser();
  }

  public RaplaFacade getFacade() {
    return facade;
  }

  public RaplaLocale getRaplaLocale() {
    return locale;
  }

  public CalendarSelectionModel getCalendarModel()
  {
    return calendarModel;
  }

  public String[] getCalendarNames() throws RaplaException {
    final Preferences preferences = facade.getPreferences(getUser());
    RaplaMap<CalendarModelConfiguration> exportMap = preferences.getEntry(AutoExportPlugin.PLUGIN_ENTRY);
    if (exportMap != null) {
      return exportMap.keySet().toArray(new String[] {});
    }
    return new String[] {};
  }

  public JsDate toJsDate(Date date) {
    if (date != null)
      return JsDate.create(date.getTime());
    return null;
  }

  public Date addDays(Date date, int days) {
    return DateTools.addDays(date, days);
  }

  public Date fromDateString(String string, boolean fillDate) throws ParseDateException
  {
    return SerializableDateTimeFormat.INSTANCE.parseDate( string, fillDate);
  }

  public Object[] toArray(Collection<?> collection) {
    return collection.toArray();
  }

  public Object[] streamToArray(Stream<?> stream) {
    return stream.toArray();
  }

  public Promise<Integer> testDialog() {
    VueDialog dialog = new VueDialog(
      new VueLayout(VueLayout.Direction.COLUMN)
        .addChild(new VueLabel("Hallo Welt 1"))
        .addChild(new VueLabel("Hallo Welt 2"))
        .addChild(new VueLabel("Hallo Welt 3"))
      ,
      new String[] {}
    );
    dialog.start(false)
          .thenAccept(i -> RaplaVue.emit("gwt-dialog-close"));
    return dialog.getPromise();
  }

  public String[] getSelectedIds(CalendarSelectionModel model) throws RaplaException
  {
    final Collection<Allocatable> selectedAllocatablesAsList = model.getSelectedAllocatablesAsList();
    String[] result = new String[selectedAllocatablesAsList.size()];
    int i= 0;
    for ( Allocatable allocatable: selectedAllocatablesAsList) {
      result[i++]= allocatable.getId();
    }
    return result;
  }
  public Integer toInteger(int integer) {
    return Integer.valueOf(integer);
  }

  public Set<Object> asSet(Object[] elements) {
    return Arrays.stream(elements).collect(Collectors.toSet());
  }

  public TimeInterval createInterval(Date from, Date to) {
    return new TimeInterval(from, to);
  }

  public Promise<RaplaTableModel> loadTableModel(CalendarSelectionModel model) {
    final String viewId = model.getViewId();
    if (viewId.equals("table_appointments")) {
      return loadTableModel("appointments", (() -> model.queryBlocks(model.getTimeIntervall())));
    } else if (viewId.equals("table") || viewId.equals("table_events")) {
      return loadTableModel("events",
                            (() -> model.queryReservations(model.getTimeIntervall()).thenApply(ArrayList::new)));
    } else {
      return new ResolvedPromise<>(new RaplaException("No table data found for view " + viewId));
    }
  }

  private <T> Promise<RaplaTableModel> loadTableModel(String viewId, Supplier<Promise<List<T>>> initFunction) {
    RaplaTableModel<T> tableModel;
    try {
      User user = getUser();
      List<RaplaTableColumn<T>> raplaTableColumns = tableConfigLoader.loadColumns(viewId, user);
      tableModel = new RaplaTableModel<>(raplaTableColumns);
    } catch (RaplaException e) {
      return new ResolvedPromise<>(e);
    }
    return initFunction.get().thenApply(tableModel::setObjects);
  }

  public VueTree buildConflictTree() {
    // TODO: this is a placeholder, return the real conflicts here shown in the main view
    final VueTreeNode root = new VueTreeNode("DEMO: Konflikte", null);
    root.add(new VueTreeNode("DEMO: Konflikt 1", null));
    root.add(new VueTreeNode("DEMO: Konflikt 2", null));
    return new VueTree(root);
  }

  // nullable
  public Category findCategoryById(String id) throws EntityNotFoundException{
    return facade.resolve(new ReferenceInfo<>(id, Category.class));
  }
  // nullable
  public Allocatable findAllocatableById(String id) throws EntityNotFoundException {
    return facade.resolve(new ReferenceInfo<>(id, Allocatable.class));
  }

  public void throwEx() {
    throw new RuntimeException("fehler");
  }

  public View[] getViews() {
    // TODO: this is a placeholder, return the real views here
    return new View[] {
      new View("table", "Veranstaltungen"),
      new View("table_appointments", "Reservierungen"),
      //new View("day", "Tag"),
      new View("week", "Woche"),
      //new View("month", "Monat")
    };
  }


  public void jumpDate(int direction) throws RaplaException
  {
    final CalendarSelectionModel calendarModel = getCalendarModel();
    Date date2 = calendarModel.getSelectedDate();
    DateTools.IncrementSize incrementSize = DateTools.IncrementSize.WEEK_OF_YEAR;
    if ( direction != 0)
    {
      date2 = DateTools.add(date2, incrementSize, direction * getIncrementAmount(incrementSize));
    }
    else
    {
      date2 = facade.today();
    }
    calendarModel.setSelectedDate( date2 );
  }

  protected int getIncrementAmount(DateTools.IncrementSize incrementSize) throws RaplaException
  {
    if (incrementSize == DateTools.IncrementSize.WEEK_OF_YEAR)
    {
      int daysInWeekview = getCalendarOptions().getDaysInWeekview();
      return Math.max(1,daysInWeekview / 7 );
    }
    return 1;
  }

    class View {

    public final String id;
    public final String label;

    public View(final String id, final String label) {
      this.id = id;
      this.label = label;
    }
  }
}
