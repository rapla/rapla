package org.rapla.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.CalendarPlaceView.Presenter;
import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.event.AbstractActivityController.Place;
import org.rapla.client.event.PlaceChangedEvent;
import org.rapla.client.event.PlacePresenter;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.ModificationEventImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;

import com.google.web.bindery.event.shared.EventBus;

@Extension(provides = PlacePresenter.class, id = CalendarPlacePresenter.PLACE_ID)
public class CalendarPlacePresenter implements Presenter, PlacePresenter
{
    public static final String PLACE_ID = "cal";
    private static final String TODAY_DATE = "today";

    private final CalendarPlaceView view;
    private final RaplaFacade facade;
    private final CalendarSelectionModel model;
    private final EventBus eventBus;
    private final RaplaResources i18n;
    private Map<String, CalendarPlugin> viewPluginPresenter;
    private CalendarPlugin selectedView;
    private Logger logger;
    private String calendar;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Inject
    public CalendarPlacePresenter(final CalendarPlaceView view, final RaplaFacade facade, final RaplaResources i18n, final CalendarSelectionModel model,
            final Logger logger, final EventBus eventBus, Map<String, CalendarPlugin> views)
    {
        this.view = view;
        this.facade = facade;
        this.i18n = i18n;
        this.model = model;
        this.logger = logger;
        this.eventBus = eventBus;
        view.setPresenter(this);

        viewPluginPresenter = new LinkedHashMap<String, CalendarPlugin>();
        for (Entry<String, CalendarPlugin> entry : views.entrySet())
        {
            viewPluginPresenter.put(entry.getKey(), entry.getValue());
        }
        if (views.size() > 0)
        {
            selectView(null);
        }

    }

    private void init()
    {
        try
        {
            List<String> viewNames = new ArrayList<String>();
            for (CalendarPlugin plugin : viewPluginPresenter.values())
            {
                if (plugin.isEnabled())
                {
                    viewNames.add(plugin.getName());
                }
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
            if (calendar == null)
            {
                changeCalendar(defaultCalendar, false);
            }
            view.show(viewNames, selectedView.getName(), calendarNames, calendar);
            final Date selectedDate = model.getSelectedDate();
            view.updateDate(selectedDate);
            final ModificationEventImpl event = new ModificationEventImpl();
            updateView(event);
            view.replaceContent(selectedView.provideContent());
        }
        catch (RaplaException e)
        {
            logger.error("Error initializing calendar selection: " + e.getMessage(), e);
        }
    }

    @Override
    public void changeCalendar(String newCalendarName)
    {
        changeCalendar(newCalendarName, true);
    }

    public void changeCalendar(String newCalendarName, boolean fireEvent)
    {
        try
        {
            if (!newCalendarName.equals(calendar))
            {
                calendar = newCalendarName;
                Date selectedDate = model.getSelectedDate();
                model.load(newCalendarName == i18n.getString("default") ? null : newCalendarName);
                model.setSelectedDate(selectedDate);
                if (fireEvent)
                {
                    updatePlace();
                }
                updateResourcesTree();
            }
        }
        catch (Exception e)
        {
            logger.error("error changing to calendar " + newCalendarName, e);
        }
    }

    @Override
    public void selectDate(Date newDate)
    {
        updateSelectedDate(newDate);
        updatePlace();
    }

    @Override
    public void next()
    {
        final Date selectedDate = model.getSelectedDate();
        final Date nextDate = selectedView.calcNext(selectedDate);
        updateSelectedDate(nextDate);
        updatePlace();
    }

    @Override
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
        Place place = new Place(id, info);
        PlaceChangedEvent event = new PlaceChangedEvent(place);
        eventBus.fireEvent(event);
    }

    private String calcDate()
    {
        final Date date = model.getSelectedDate();
        Date today = facade.today();
        final String dateString = date != null ? (DateTools.isSameDay(date, today) ? TODAY_DATE : SerializableDateTimeFormat.INSTANCE.formatDate(date)) : null;
        return dateString;
    }

    private String calcCalId()
    {
        if (calendar == null)
        {
            return "";
        }
        return calendar;
    }

    private String calcViewId()
    {
        if (selectedView == null)
            return "";
        for (Entry<String, CalendarPlugin> entry : viewPluginPresenter.entrySet())
        {
            if (entry.getValue() == selectedView)
            {
                return entry.getKey();
            }
        }
        return "";
    }

    private void updateResourcesTree()
    {
        try
        {
            Allocatable[] allocatables = facade.getAllocatables();
            Allocatable[] entries = allocatables;
            Collection<Allocatable> selectedAllocatables = model.getSelectedAllocatablesAsList();
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    //@Inject
    public void setViews(Map<String, CalendarPlugin> views)
    {

    }

    @Override
    public void resetPlace()
    {
        if ( viewPluginPresenter != null)
        {
            final Collection<CalendarPlugin> values = viewPluginPresenter.values();
            selectedView = values.iterator().next();
        }
    }

    @Override
    public void initForPlace(Place place)
    {
        String id = place.getInfo();
        String[] split = id.split("/");
        changeCalendar(split[0], false);
        if (split.length > 1)
        {
            String date = split[1];
            Date nextDate;
            if (TODAY_DATE.equalsIgnoreCase(date))
            {
                model.setSelectedDate(facade.today());
            }
            else
            {
                try
                {
                    nextDate = SerializableDateTimeFormat.INSTANCE.parseDate(date, false);
                    model.setSelectedDate(nextDate);
                    view.updateDate(nextDate);
                }
                catch (ParseDateException e)
                {
                    logger.error("Error loading date from place: " + e.getMessage(), e);
                }
            }
            if (split.length > 2)
            {
                String viewId = split[2];
                selectView(viewId);
            }
        }
    }

    private void selectView(String viewId)
    {
        if (viewId != null)
        {
            selectedView = viewPluginPresenter.get(viewId);
        }
        if (selectedView == null)
        {
            selectedView = viewPluginPresenter.values().iterator().next();
        }
    }

    @Override
    public void changeView(String view)
    {
        selectView(view);
        updatePlace();
    }

    public void updateView(ModificationEvent event)
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

    public Object provideContent()
    {
        init();
        return view.provideContent();
    }

}
