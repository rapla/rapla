package org.rapla.client;

import org.rapla.RaplaResources;
import org.rapla.client.CalendarPlaceView.Presenter;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.CalendarEventBus;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.internal.ConflictSelectionPresenter;
import org.rapla.client.internal.ResourceSelectionPresenter;
import org.rapla.client.internal.SavedCalendarInterface;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.ModificationEventImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.scheduler.Subject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;

@Singleton
@Extension(provides = TaskPresenter.class, id = CalendarPlacePresenter.PLACE_ID) public class CalendarPlacePresenter implements Presenter, TaskPresenter
{
    public static final String PLACE_ID = "cal";
    public static final TypedComponentRole<Boolean> SHOW_CONFLICTS_CONFIG_ENTRY = new TypedComponentRole<>("org.rapla.showConflicts");
    public static final TypedComponentRole<Boolean> SHOW_SELECTION_CONFIG_ENTRY = new TypedComponentRole<>("org.rapla.showSelection");
    public static final String SHOW_SELECTION_MENU_ENTRY = "show_resource_selection";
    public static final String SHOW_CONFLICTS_MENU_ENTRY = "show_conflicts";
    private static final String TODAY_DATE = "today";
    private final Subject<String> busyIdleObservable;
    boolean listenersDisabled = false;

    private final CalendarPlaceView view;
    private DialogUiFactoryInterface dialogUiFactory;
    private final RaplaFacade facade;
    private final CalendarSelectionModel model;
    private Logger logger;

    final private ResourceSelectionPresenter resourceSelectionPresenter;
    final private SavedCalendarInterface savedViews;
    final private ConflictSelectionPresenter conflictsView;
    final private CalendarContainer calendarContainer;
    final ClientFacade clientFacade;

    @SuppressWarnings({ "rawtypes", "unchecked" }) @Inject public CalendarPlacePresenter(final CalendarPlaceView view, final ClientFacade clientFacade,
            final RaplaResources i18n, final CalendarSelectionModel model, final Logger logger, final CalendarEventBus eventBus,/*, Map<String, CalendarPlugin> views*/
            ResourceSelectionPresenter resourceSelectionPresenter, SavedCalendarInterface savedViews, ConflictSelectionPresenter conflictsSelectionPresenter,
            CalendarContainer calendarContainer,final CommandScheduler scheduler, DialogUiFactoryInterface dialogUiFactory) throws RaplaInitializationException
    {
        this.view = view;
        this.dialogUiFactory = dialogUiFactory;
        this.facade = clientFacade.getRaplaFacade();
        this.clientFacade = clientFacade;
        this.model = model;
        this.logger = logger;
        this.resourceSelectionPresenter = resourceSelectionPresenter;
        this.savedViews = savedViews;
        this.conflictsView = conflictsSelectionPresenter;
        this.calendarContainer = calendarContainer;
        this.busyIdleObservable = scheduler.createPublisher();
        resourceSelectionPresenter.setCallback(() ->
        {
            resourceSelectionChanged();
        });
        view.addSavedViews(savedViews);
        view.addResourceSelectionView(resourceSelectionPresenter.provideContent());
        view.addConflictsView(conflictsSelectionPresenter.getConflictsView());
        view.addSummaryView(conflictsSelectionPresenter.getSummaryComponent());
        view.addCalendarView(calendarContainer.provideContent());
        updateOwnReservationsSelected();
        try
        {
            calendarContainer.init(true,model, () -> {
                calendarUpdated();
                start();
            });
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        view.setPresenter(this);
        eventBus.getCalendarRefreshObservable().subscribe((evt)-> {
            busyIdleObservable.onNext("Laden");
                    calendarContainer.update().doOnComplete(() ->
                            busyIdleObservable.onNext(""))
            .doOnError((ex2)->
                    logger.error( ex2.getMessage(), ex2))
            .subscribe();
        });
        eventBus.getCalendarPreferencesObservable().subscribe((evt)
        ->
                {
                    try
                    {
                        Entity preferences = facade.getPreferences(clientFacade.getUser());
                        ModificationEventImpl modificationEvt = new ModificationEventImpl();
                        modificationEvt.addChanged(preferences);
                        resourceSelectionPresenter.dataChanged(modificationEvt);
                        calendarContainer.update(modificationEvt);
                        conflictsSelectionPresenter.dataChanged(modificationEvt);
                    }
                    catch (Exception ex)
                    {
                        dialogUiFactory.showException(ex, null);
                    }
                }
        );
        try
        {
            updateViews();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }

    }

    @Override
    public Subject<String> getBusyIdleObservable() {
        return busyIdleObservable;
    }

    public String getTitle(ApplicationEvent event)
    {
        return "";
    }


    /*
    public void selectDate(Date newDate)
    {
        updateSelectedDate(newDate);
        updatePlace();
    }

    public void next()
    {
        final Date selectedDate = model.getSelectedDate();
        final Date nextDate = selectedView.calcNext(selectedDate);
        updateSelectedDate(nextDate);
        updatePlace();
    }

    public void previous()
    {
        final Date selectedDate = model.getSelectedDate();
        final Date nextDate = selectedView.calcPrevious(selectedDate);
        updateSelectedDate(nextDate);
        updatePlace();
    }

    private void updateSelectedDate(final Date nextDate)
    {
        model.setSelectedDate(nextDate);
        view.updateDate(nextDate);
    }

    public void updatePlace()
    {
        String id = PLACE_ID;
        String info = calcCalId() + "/" + calcDate() + "/" + calcViewId();
        ApplicationEvent event = new ApplicationEvent(id, info, null);
        eventBus.fireEvent(event);
    }

    private String calcDate()
    {
        final Date date = model.getSelectedDate();
        Date today = facade.today();
        final String dateString = date != null ? (DateTools.isSameDay(date, today) ? TODAY_DATE : SerializableDateTimeFormat.INSTANCE.formatDate(date)) : null;
        return dateString;
    }

    @Override
    public void resourcesSelected(Collection<Allocatable> selected)
    {
        model.setSelectedObjects(selected);
        updateView(null);
    }
*/

    //    @Override
    //    public void resetPlace()
    //    {
    //        if ( viewPluginPresenter != null)
    //        {
    //            final Collection<CalendarPlugin> values = viewPluginPresenter.values();
    //            selectedView = values.iterator().next();
    //        }
    //    }
    //
    @Override public <T> Promise<RaplaWidget> startActivity(ApplicationEvent activity)
    {
        String info = activity.getInfo();
        try
        {
            if ( info != null && info.equalsIgnoreCase("Standard"))
            {
                info = null;
            }

            // remember current selected date if model is switched
            final Date tmpDate = model.getSelectedDate();
            // keep in mind if current model had saved date
            boolean tmpModelHasStoredCalenderDate = hasStoredDate(model);
            model.load(info);
            boolean newModelHasStoredCalenderDate = hasStoredDate(model);
            if (!newModelHasStoredCalenderDate && !tmpModelHasStoredCalenderDate) {

                model.setSelectedDate(tmpDate);
            }
            updateView( null);
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
        final ResolvedPromise<RaplaWidget> raplaWidgetResolvedPromise = new ResolvedPromise<>(view);
        return raplaWidgetResolvedPromise;
    }

    private boolean hasStoredDate(CalendarModel model)
    {
        String selectedDate = model.getOption(CalendarModel.SAVE_SELECTED_DATE);
        if(selectedDate == null)
            return false;
        final boolean storedDate = selectedDate.equals("true");
        return storedDate;
    }

    public void updateView(ModificationEvent evt)
    {
        listenersDisabled = true;
        try
        {
            resourceSelectionPresenter.dataChanged(evt);
            conflictsView.dataChanged(evt);
            calendarContainer.update(evt);
            savedViews.update();
            updateViews();
            // this is done in calendarContainer update
            //updateOwnReservationsSelected();
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, null);
        }
        finally
        {
            listenersDisabled = false;
        }
    }

    private void updateViews() throws RaplaException
    {
        User user = clientFacade.getUser();
        final Preferences preferences = facade.getPreferences(user);
        boolean showConflicts = preferences.getEntryAsBoolean(CalendarPlacePresenter.SHOW_CONFLICTS_CONFIG_ENTRY, true);
        boolean showSelection = preferences.getEntryAsBoolean(CalendarPlacePresenter.SHOW_SELECTION_CONFIG_ENTRY, true);
        boolean templateMode = clientFacade.getTemplate() != null;
        view.updateView(showConflicts, showSelection, templateMode);
    }

    public void updateOwnReservationsSelected()
    {
        final CalendarSelectionModel model = resourceSelectionPresenter.getModel();
        boolean isSelected = model.isOnlyCurrentUserSelected();
        //FIXME onlyOwnReservationSelected
    }

    @Override public void minmaxPressed()
    {
        closeFilter();
        try
        {
            User user = clientFacade.getUser();
            TypedComponentRole<Boolean> configEntry = CalendarPlacePresenter.SHOW_SELECTION_CONFIG_ENTRY;
            facade.update(facade.getPreferences(user),(prefs)->
                    {
                        final boolean oldEntry = prefs.getEntryAsBoolean(configEntry, true);
                        boolean newSelected = !oldEntry;
                        prefs.putEntry(configEntry, newSelected);
                    }).exceptionally(ex->dialogUiFactory.showException( ex, null));
        }
        catch (RaplaException e)
        {
            dialogUiFactory.showException( e, null);
        }

    }

    public void start()
    {
        calendarContainer.scrollToStart();
    }

    @Override public void closeTemplate()
    {
        clientFacade.setTemplate(null);
    }

    public void closeFilter()
    {
        // CKO Not a good solution. FilterDialogs should close themselfs when model changes.
        // BJO 00000139
        resourceSelectionPresenter.closeFilterButton();
        calendarContainer.closeFilterButton();

        // BJO 00000139
    }

    @Override
    public Promise<Void> processStop(ApplicationEvent event)
    {
        return new ResolvedPromise<>(Promise.VOID);
    }


    public void calendarUpdated()
    {
        if (listenersDisabled)
        {
            return;
        }
        try
        {
            resourceSelectionPresenter.updateMenu();
        }
        catch (RaplaException e1)
        {
            logger.error(e1.getMessage(), e1);
        }
    }


    public void resourceSelectionChanged()
    {
        if (listenersDisabled)
        {
            return;
        }
        conflictsView.clearSelection();
        
    }

}
