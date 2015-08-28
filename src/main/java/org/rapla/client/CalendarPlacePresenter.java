package org.rapla.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.ActivityManager.Place;
import org.rapla.client.CalendarPlaceView.Presenter;
import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.event.PlaceChangedEvent;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

import com.google.web.bindery.event.shared.EventBus;

public class CalendarPlacePresenter<W> implements Presenter, PlacePresenter
{
    private static final String PLACE_ID = "cal";
    private final CalendarPlaceView<W> view;
    private final ClientFacade facade;
    private final CalendarOptions calendarOptions;
    private final CalendarSelectionModel model;
    private final EventBus eventBus;
    private final RaplaResources i18n;
    private List<CalendarPlugin> viewPluginPresenter;
    private CalendarPlugin selectedView;
    private Logger logger;
    private String calendar;

    @Inject
    public CalendarPlacePresenter(final CalendarPlaceView view, final ClientFacade facade, final RaplaResources i18n, final CalendarOptions calendarOptions,
            final CalendarSelectionModel model, final Logger logger, final EventBus eventBus)
    {
        this.view = view;
        this.facade = facade;
        this.i18n = i18n;
        this.calendarOptions = calendarOptions;
        this.model = model;
        this.logger = logger;
        this.eventBus = eventBus;
        view.setPresenter(this);
    }

    private void init()
    {
        try
        {
            List<String> viewNames = new ArrayList<String>();
            for (CalendarPlugin plugin : viewPluginPresenter)
            {
                viewNames.add(plugin.getName());
            }
            List<String> calendarNames = new ArrayList<String>();
            final Preferences preferences = facade.getPreferences();
            Map<String, CalendarModelConfiguration> exportMap = preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
            if (exportMap != null)
            {
                calendarNames.addAll(exportMap.keySet());
            }
            Collections.sort(calendarNames);
            final String defaultCalendar = i18n.getString("default");
            calendarNames.add(0, defaultCalendar);
            if(calendar == null)
            {
                calendar = defaultCalendar;
            }
            view.show(viewNames, selectedView.getName(), calendarNames, calendar);
            final Date selectedDate = model.getSelectedDate();
            view.updateDate(selectedDate);
        }
        catch (RaplaException e)
        {
            logger.error("Error initializing calendar selection: " + e.getMessage(), e);
        }
    }

    @Override
    public void changeCalendar(String newCalendarName)
    {
        try
        {
            calendar = newCalendarName;
            model.load(newCalendarName == i18n.getString("default") ? null : newCalendarName);
            updatePlace();
            updateResourceSelection();
            updateView();
        }
        catch (Exception e)
        {
            logger.error("error changing to calendar " + newCalendarName, e);
        }
    }

    @Override
    public void selectDate(Date newDate)
    {
        model.setSelectedDate(newDate);
        view.updateDate(newDate);
        updateView();
    }

    @Override
    public void next()
    {
        final Date selectedDate = model.getSelectedDate();
        final Date nextDate = selectedView.calcNext(selectedDate);
        model.setSelectedDate(nextDate);
        view.updateDate(nextDate);
        updateView();
    }

    @Override
    public void previous()
    {
        final Date selectedDate = model.getSelectedDate();
        final Date nextDate = selectedView.calcPrevious(selectedDate);
        model.setSelectedDate(nextDate);
        view.updateDate(nextDate);
        updateView();
    }

    public void updatePlace()
    {
        String name = PLACE_ID;
        String id = calcCalId() + "/" + calcViewId();
        Place place = new Place(name, id);
        PlaceChangedEvent event = new PlaceChangedEvent(place);
        eventBus.fireEvent(event);
    }

    private String calcCalId()
    {
        if(calendar == null){
            return "";
        }
        return calendar;
    }

    private String calcViewId()
    {
        if(selectedView == null)
            return "";
        return selectedView.getId();
    }

    private void updateResourceSelection()
    {
        // TODO change
        updateResourcesTree();
    }

    private void updateResourcesTree()
    {
        try
        {
            Allocatable[] allocatables = facade.getAllocatables();
            Allocatable[] entries = allocatables;
            Collection<Allocatable> selectedAllocatables = Arrays.asList(model.getSelectedAllocatables());
            view.updateResources(entries, selectedAllocatables);
        }
        catch (RaplaException e)
        {
            logger.error("error while updating resource tree " + e.getMessage(), e);
        }
    }

    @Override
    public void resourcesSelected(Collection<Allocatable> selected)
    {
        model.setSelectedObjects(selected);
        updateView();
    }

    @Inject
    public void setViews(Set<CalendarPlugin> views)
    {
        this.viewPluginPresenter = new ArrayList<CalendarPlugin>(views);
        if (views.size() > 0)
        {
            setSelectedViewIndex(0);
        }
    }

    @Override
    public boolean isResposibleFor(Place place)
    {
        if (PLACE_ID.equals(place.getName()))
        {
            String id = place.getId();
            String[] split = id.split("/");
            calendar = split[0];
            try
            {
                model.load(calendar == i18n.getString("default") ? null : calendar);
            }
            catch (RaplaException e)
            {
            }
            String viewId = split[1];
            int index = findView(viewId);
            setSelectedViewIndex(index);
            return true;
        }
        return false;
    }

    private int findView(String viewId)
    {
        int i = 0;
        for (CalendarPlugin calendarPlugin : viewPluginPresenter)
        {
            if (calendarPlugin.getId().equals(viewId))
            {
                return i;
            }
            i++;
        }
        return -1;
    }

    private void setSelectedViewIndex(int index)
    {
        if (index >= 0)
        {
            selectedView = viewPluginPresenter.get(index);
            updateView();
            view.replaceContent(selectedView);
        }
    }

    @Override
    public void changeView(String view)
    {
        for (int i = 0; i < viewPluginPresenter.size(); i++)
        {
            if (viewPluginPresenter.get(i).getName().equals(view))
            {
                setSelectedViewIndex(i);
                updatePlace();
                return;
            }
        }
    }

    public void updateView()
    {
        try
        {
            selectedView.updateContent();
        }
        catch (Exception e)
        {
            logger.error("error updating view " + e.getMessage(), e);
        }
    }

    public W provideContent()
    {
        init();
        updateResourcesTree();
        return view.provideContent();
    }
}
