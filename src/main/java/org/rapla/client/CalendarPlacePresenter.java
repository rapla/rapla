package org.rapla.client;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.CalendarPlaceView.Presenter;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.CalendarRefreshEvent;
import org.rapla.client.event.OwnReservationsEvent;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.internal.ConflictSelectionPresenter;
import org.rapla.client.internal.MultiCalendarPresenter;
import org.rapla.client.internal.ResourceSelectionPresenter;
import org.rapla.client.internal.SavedCalendarPresenter;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.ModificationEventImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

@Extension(provides = TaskPresenter.class, id = CalendarPlacePresenter.PLACE_ID) public class CalendarPlacePresenter implements Presenter, TaskPresenter
{
    public static final String PLACE_ID = "cal";
    public static final TypedComponentRole<Boolean> SHOW_CONFLICTS_CONFIG_ENTRY = new TypedComponentRole<Boolean>("org.rapla.showConflicts");
    public static final TypedComponentRole<Boolean> SHOW_SELECTION_CONFIG_ENTRY = new TypedComponentRole<Boolean>("org.rapla.showSelection");
    public static final String SHOW_SELECTION_MENU_ENTRY = "show_resource_selection";
    public static final String SHOW_CONFLICTS_MENU_ENTRY = "show_conflicts";
    private static final String TODAY_DATE = "today";
    boolean listenersDisabled = false;

    private final CalendarPlaceView view;
    private DialogUiFactoryInterface dialogUiFactory;
    private final RaplaFacade facade;
    private final CalendarSelectionModel model;
    private final EventBus eventBus;
    private final RaplaResources i18n;
    private Logger logger;

    final private ResourceSelectionPresenter resourceSelectionPresenter;
    final private SavedCalendarPresenter savedViews;
    final private ConflictSelectionPresenter conflictsView;
    final private MultiCalendarPresenter calendarContainer;
    final ClientFacade clientFacade;

    @SuppressWarnings({ "rawtypes", "unchecked" }) @Inject public CalendarPlacePresenter(final CalendarPlaceView view, final ClientFacade clientFacade,
            final RaplaResources i18n, final CalendarSelectionModel model, final Logger logger, final EventBus eventBus,/*, Map<String, CalendarPlugin> views*/
            ResourceSelectionPresenter resourceSelectionPresenter, SavedCalendarPresenter savedViews, ConflictSelectionPresenter conflictsView,
            MultiCalendarPresenter calendarContainer, DialogUiFactoryInterface dialogUiFactory) throws RaplaInitializationException
    {
        this.view = view;
        this.dialogUiFactory = dialogUiFactory;
        this.facade = clientFacade.getRaplaFacade();
        this.clientFacade = clientFacade;
        this.i18n = i18n;
        this.model = model;
        this.logger = logger;
        this.eventBus = eventBus;
        this.resourceSelectionPresenter = resourceSelectionPresenter;
        this.savedViews = savedViews;
        this.conflictsView = conflictsView;
        this.calendarContainer = calendarContainer;
        resourceSelectionPresenter.setCallback(() ->
        {
            resourceSelectionChanged();
        });
        calendarContainer.setCallback(() ->
        {
            calendarUpdated();
        });
        view.addSavedViews(savedViews);
        view.addResourceSelectionView(resourceSelectionPresenter.provideContent());
        view.addConflictsView(conflictsView.getConflictsView());
        view.addSummaryView(conflictsView.getSummaryComponent());
        view.addCalendarView(calendarContainer.provideContent());

        updateOwnReservationsSelected();

        try
        {
            calendarContainer.init(true);
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        view.setPresenter(this);
        eventBus.addHandler(CalendarRefreshEvent.TYPE, (evt) -> {
            calendarContainer.update();
        });

        eventBus.addHandler(OwnReservationsEvent.TYPE, (evt) -> {
            try
            {
                Entity preferences = facade.getPreferences();
                ModificationEventImpl modificationEvt = new ModificationEventImpl();
                modificationEvt.addChanged(preferences);
                resourceSelectionPresenter.dataChanged(modificationEvt);
                calendarContainer.update(modificationEvt);
                conflictsView.dataChanged(modificationEvt);
            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, null);
            }
        });


        try
        {
            updateViews();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }

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
        return new ResolvedPromise<RaplaWidget>(view);
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
        boolean showConflicts = facade.getPreferences().getEntryAsBoolean(CalendarPlacePresenter.SHOW_CONFLICTS_CONFIG_ENTRY, true);
        boolean showSelection = facade.getPreferences().getEntryAsBoolean(CalendarPlacePresenter.SHOW_SELECTION_CONFIG_ENTRY, true);
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
            TypedComponentRole<Boolean> configEntry = CalendarPlacePresenter.SHOW_SELECTION_CONFIG_ENTRY;
            User user = clientFacade.getUser();
            Preferences prefs = facade.edit( facade.getPreferences(user));
            final boolean oldEntry = prefs.getEntryAsBoolean(configEntry, true);
            boolean newSelected = !oldEntry;
            prefs.putEntry(configEntry, newSelected);
            facade.store(prefs);
            // show Tooltip only when selection pane is visible
            javax.swing.ToolTipManager.sharedInstance().setEnabled(newSelected);
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
