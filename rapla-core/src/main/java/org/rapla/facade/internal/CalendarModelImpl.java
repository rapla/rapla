/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.facade.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.*;
import org.rapla.entities.dynamictype.internal.EvalContext;
import org.rapla.entities.dynamictype.internal.ParseContext;
import org.rapla.entities.dynamictype.internal.ParsedText;
import org.rapla.entities.dynamictype.internal.ParsedText.Variable;
import org.rapla.entities.extensionpoints.Function;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.Conflict;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.plugin.planningstatus.PlanningStatusPlugin;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.rapla.entities.configuration.CalendarModelConfiguration.EXPORT_ENTRY;

import java.time.LocalDateTime;
public class CalendarModelImpl implements CalendarSelectionModel, org.rapla.facade.SyncCalendarModel
{
    private static final String DEFAULT_VIEW = "week";//WeekViewFactory.WEEK_VIEW;
    private static final String ICAL_EXPORT_ENABLED = "org.rapla.plugin.export2ical" + ".selected";
    private static final String HTML_EXPORT_ENABLED = EXPORT_ENTRY + ".selected";
    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarModelImpl.class);
    LocalDateTime startDate;
    LocalDateTime endDate;
    LocalDateTime selectedDate;
    Collection<RaplaObject> selectedObjects = new LinkedHashSet<>();
    String title;
    final StorageOperator operator;
    String selectedView;
    private User user;
    Map<String, String> optionMap = new HashMap<>();

    boolean defaultEventTypes = true;
    boolean defaultResourceTypes = true;
    Collection<TimeInterval> timeIntervals = Collections.emptyList();
    Collection<Allocatable> markedAllocatables = Collections.emptyList();
    Locale locale;
    boolean markedIntervalTimeEnabled = false;
    Map<DynamicType, ClassificationFilter> reservationFilter = new LinkedHashMap<>();
    Map<DynamicType, ClassificationFilter> allocatableFilter = new LinkedHashMap<>();
    public static final RaplaConfiguration ALLOCATABLES_ROOT = new RaplaConfiguration("rootnode", "allocatables");
    public static final RaplaConfiguration USER_ROOT = new RaplaConfiguration("userroot", "users");

    Predicate<Appointment> appointmentFilter;

    @Autowired public CalendarModelImpl(ClientFacade clientFacade, RaplaLocale locale) throws RaplaInitializationException
    {
        this(locale.getLocale(), getUser(clientFacade), ((ClientFacadeImpl)clientFacade).getOperator());
        try
        {
            load(null);
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
    }

    private static User getUser(ClientFacade clientFacade) throws RaplaInitializationException
    {
        try
        {
            return clientFacade.getUser();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
    }


    Preferences getSystemPreferences() throws RaplaException
    {
        return operator.getPreferences(null, true);
    }

    public CalendarModelImpl(Locale locale, User user, StorageOperator operator) throws RaplaInitializationException
    {
        this.locale = locale;
        this.operator = operator;
        LocalDateTime today = this.operator.today().atStartOfDay();
        setSelectedDate(today);
        setStartDate(today);
        setEndDate(DateTools.addYear(getStartDate()));
        try
        {
            DynamicType[] types = getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
            if (types.length == 0)
            {
                types = getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
            }
            setSelectedObjects(types.length > 0 ? Collections.singletonList(types[0]) : Collections.emptyList());
            setViewId(DEFAULT_VIEW);
            this.user = user;
            optionMap.put(CalendarModel.SAVE_SELECTED_DATE, "false");
            resetExports();
        }
        catch (RaplaException ex)
        {
            throw new RaplaInitializationException(ex);
        }
    }

    public void resetExports()
    {
        setTitle(null);
        setOption(CalendarModel.SHOW_NAVIGATION_ENTRY, "true");
        setOption(HTML_EXPORT_ENABLED, "false");
        setOption(ICAL_EXPORT_ENABLED, "false");
    }

    public boolean isMatchingSelectionAndFilter(Appointment appointment) throws RaplaException
    {
        Reservation reservation = appointment.getReservation();
        if (reservation == null)
        {
            return false;
        }
        return isMatchingSelectionAndFilter(reservation, appointment);
    }

    public boolean isMatchingSelectionAndFilter(Reservation reservation, Appointment appointment) throws RaplaException
    {
        Set<RaplaObject> hashSet;
        if ( appointment == null)
        {
            hashSet = new HashSet<>(Arrays.asList(reservation.getAllocatables()));
        }
        else
            {
            hashSet = reservation.getAllocatablesFor(appointment).collect(Collectors.toSet());
        }

        hashSet.add(reservation.getClassification().getType());
        final ReferenceInfo<User> ownerId = reservation.getOwnerRef();
        if (ownerId != null)
        {
            User resolvedOwner = operator.tryResolve(ownerId);
            // only admins can see calendar models from other users so its ok if resolvedOwner is null for non admin users
            if (resolvedOwner != null)
            {
                hashSet.add(resolvedOwner);
            }
        }
        Collection<Allocatable> allAllocatables = getAllAllocatables();
        hashSet.retainAll(allAllocatables);
        boolean matchesEventObjects = hashSet.size() != 0 || allAllocatables.size() == 0;
        if (!matchesEventObjects)
        {
            return false;
        }

        Classification classification = reservation.getClassification();
        if (isDefaultEventTypes())
        {
            return true;
        }

        ClassificationFilter[] reservationFilter = getReservationFilter();
        for (ClassificationFilter filter : reservationFilter)
        {
            if (filter.matches(classification))
            {
                return true;
            }
        }
        return false;
    }

    public boolean setConfiguration(CalendarModelConfiguration config, final Map<String, String> alternativOptions, boolean updateSelectedDates) throws RaplaException
    {
        ArrayList<RaplaObject> selectedObjects = new ArrayList<>();

        allocatableFilter.clear();
        reservationFilter.clear();
        if (config == null)
        {
            defaultEventTypes = true;
            defaultResourceTypes = true;
            DynamicType type = null;
            {
                DynamicType[] dynamicTypes = getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
                if (dynamicTypes.length > 0)
                {
                    type = dynamicTypes[0];
                }
            }
            if (type == null)
            {
                DynamicType[] dynamicTypes = getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
                if (dynamicTypes.length > 0)
                {
                    type = dynamicTypes[0];
                }
            }
            if (type != null)
            {
                setSelectedObjects(Collections.singletonList(type));
            }
            return true;
        }
        else
        {
            defaultEventTypes = config.isDefaultEventTypes();
            defaultResourceTypes = config.isDefaultResourceTypes();
        }
        boolean couldResolveAllEntities = true;

        // get filter
        title = config.getTitle();
        selectedView = config.getView();
        //selectedObjects
        optionMap = new TreeMap<>();
        //  viewOptionMap = new TreeMap<String,String>();
        if (config.getOptionMap() != null)
        {
            Map<String, String> configOptions = config.getOptionMap();
            addOptions(configOptions);
        }
        if (alternativOptions != null)
        {
            addOptions(alternativOptions);
        }
        final String saveDate = optionMap.get(CalendarModel.SAVE_SELECTED_DATE);
        final boolean isSaveDate = saveDate == null || saveDate.equals("true");
        if (config.getSelectedDate() != null && isSaveDate)
        {
            setSelectedDate(config.getSelectedDate());
        }
        else if (updateSelectedDates)
        {
            setSelectedDate(operator.today().atStartOfDay());
        }
        final LocalDateTime startDate = config.getStartDate();
        if (startDate != null && isSaveDate)
        {
            setStartDate(startDate);
        }
        else if (updateSelectedDates)
        {
            setStartDate(operator.today().atStartOfDay());
        }
        final LocalDateTime endDate = config.getEndDate();
        if (endDate != null && isSaveDate)
        {
            setEndDate(endDate);
        }
        else if (updateSelectedDates)
        {
            setEndDate( (endDate != null && startDate != null) ? DateTools.addDays( getStartDate(), DateTools.countDays(startDate, endDate)) : DateTools.addYear(this.startDate));
        }
        selectedObjects.addAll(config.getSelected());
        if (config.isResourceRootSelected())
        {
            selectedObjects.add(ALLOCATABLES_ROOT);
        }

        setSelectedObjects(selectedObjects);
        for (ClassificationFilter f : config.getFilter())
        {
            final DynamicType type = f.getType();
            final String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            boolean eventType = annotation != null && annotation.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            Map<DynamicType, ClassificationFilter> map = eventType ? reservationFilter : allocatableFilter;
            map.put(type, f);
        }
        return couldResolveAllEntities;
    }

    protected void addOptions(Map<String, String> configOptions)
    {
        for (Map.Entry<String, String> entry : configOptions.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();
            optionMap.put(key, value);
        }
    }

    public User getUser()
    {
        return user;
    }

    public void setUser(User user)
    {
        this.user = user;
    }

    public CalendarModelConfigurationImpl createConfiguration() throws RaplaException
    {
        ClassificationFilter[] allocatableFilter = isDefaultResourceTypes() ? null : getAllocatableFilter();
        ClassificationFilter[] eventFilter = isDefaultEventTypes() ? null : getReservationFilter();
        return createConfiguration(allocatableFilter, eventFilter);
    }

    private CalendarModelConfigurationImpl createConfiguration(ClassificationFilter[] allocatableFilter, ClassificationFilter[] eventFilter)
            throws RaplaException
    {
        String viewName = selectedView;
        Set<Entity> selected = new HashSet<>();

        Collection<RaplaObject> selectedObjects = getSelectedObjects();
        for (RaplaObject object : selectedObjects)
        {
            if (!(object instanceof Conflict || object instanceof Reservation) && (object instanceof Entity))
            {
                //  throw new RaplaException("Storing the conflict view is not possible with Rapla.");
                selected.add((Entity) object);
            }
        }

        final LocalDateTime selectedDate = getSelectedDate();
        final LocalDateTime startDate = getStartDate();
        final LocalDateTime endDate = getEndDate();
        boolean resourceRootSelected = selectedObjects.contains(ALLOCATABLES_ROOT);
        return newRaplaCalendarModel(selected, resourceRootSelected, allocatableFilter, eventFilter, title, startDate, endDate, selectedDate, viewName,
                optionMap);
    }

    public static boolean isDefaultFilter(ClassificationFilter[] allocatableFilter, int allTypes) {
        int filteredTypes = 0;
        if (allocatableFilter != null)
        {
            for (ClassificationFilter entry : allocatableFilter)
            {
                filteredTypes++;
                if (entry.ruleSize() > 0)
                {
                    return false;
                }
            }
        }
        return filteredTypes >= allTypes;
    }

    public CalendarModelConfigurationImpl newRaplaCalendarModel(Collection<Entity> selected, boolean resourceRootSelected,
            ClassificationFilter[] allocatableFilter, ClassificationFilter[] eventFilter, String title, LocalDateTime startDate, LocalDateTime endDate, LocalDateTime selectedDate,
            String view, Map<String, String> optionMap) throws RaplaException
    {
        boolean defaultResourceTypes;
        boolean defaultEventTypes;

        int eventTypes = 0;
        int resourceTypes = 0;
        defaultResourceTypes = true;
        defaultEventTypes = true;
        List<ClassificationFilter> filter = new ArrayList<>();
        if (allocatableFilter != null)
        {
            for (ClassificationFilter entry : allocatableFilter)
            {
                ClassificationFilter clone = entry.clone();
                filter.add(clone);
                resourceTypes++;
                if (entry.ruleSize() > 0)
                {
                    defaultResourceTypes = false;
                }
            }
        }
        if (eventFilter != null)
        {
            for (ClassificationFilter entry : eventFilter)
            {
                ClassificationFilter clone = entry.clone();
                filter.add(clone);
                eventTypes++;
                if (entry.ruleSize() > 0)
                {
                    defaultEventTypes = false;
                }
            }
        }

        DynamicType[] allEventTypes;
        allEventTypes = getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        if (allEventTypes.length > eventTypes && eventFilter != null)
        {
            defaultEventTypes = false;
        }
        final DynamicType[] allTypes = getDynamicTypes(null);
        final int allResourceTypes = allTypes.length - allEventTypes.length;
        if (allResourceTypes > resourceTypes && allocatableFilter != null)
        {
            defaultResourceTypes = false;
        }

        final ClassificationFilter[] filterArray = filter.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
        List<String> selectedIds = new ArrayList<>();
        Collection<Class<? extends Entity>> idTypeList = new ArrayList<>();
        for (Entity obj : selected)
        {
            Class<? extends Entity> raplaType = obj.getTypeClass();
            if (CalendarModelConfigurationImpl.canReference(raplaType))
            {
                selectedIds.add(obj.getId());
                idTypeList.add(raplaType);
            }
        }

        CalendarModelConfigurationImpl calendarModelConfigurationImpl = new CalendarModelConfigurationImpl(selectedIds, idTypeList, resourceRootSelected,
                filterArray, defaultResourceTypes, defaultEventTypes, title, startDate, endDate, selectedDate, view, optionMap);
        calendarModelConfigurationImpl.setResolver(operator);
        return calendarModelConfigurationImpl;
    }

    public void setReservationFilter(ClassificationFilter[] array)
    {
        reservationFilter.clear();
        if (array == null)
        {
            defaultEventTypes = true;
            return;
        }
        try
        {
            defaultEventTypes = createConfiguration(null, array).isDefaultEventTypes();
        }
        catch (RaplaException e)
        {
            // DO Not set the types
        }
        for (ClassificationFilter entry : array)
        {
            final DynamicType type = entry.getType();
            reservationFilter.put(type, entry);
        }
    }

    public void setAllocatableFilter(ClassificationFilter[] array)
    {
        allocatableFilter.clear();
        if (array == null)
        {
            defaultResourceTypes = true;
            return;
        }
        try
        {
            defaultResourceTypes = createConfiguration(array, null).isDefaultResourceTypes();
        }
        catch (RaplaException e)
        {
            // DO Not set the types
        }
        for (ClassificationFilter entry : array)
        {
            final DynamicType type = entry.getType();
            allocatableFilter.put(type, entry);
        }
    }

    @Override public LocalDateTime getSelectedDate()
    {
        return selectedDate;
    }

    @Override public void setSelectedDate(LocalDateTime date)
    {
        if (date == null)
            throw new IllegalStateException("Date can't be null");
        if (selectedDate != null && !date.equals(selectedDate))
        {
            Collection<TimeInterval> empty = Collections.emptyList();
            setMarkedIntervals(empty, false);
        }
        this.selectedDate = date;

    }

    @Override public LocalDateTime getStartDate()
    {
        return startDate;
    }

    @Override public void setStartDate(LocalDateTime date)
    {
        if (date == null)
            throw new IllegalStateException("Date can't be null");
        this.startDate = date;
    }

    @Override public LocalDateTime getEndDate()
    {
        return endDate;
    }

    @Override public void setEndDate(LocalDateTime date)
    {
        if (date == null)
            throw new IllegalStateException("Date can't be null");
        this.endDate = date;
    }

    @Override public String getTitle()
    {
        return title;
    }

    @Override public void setTitle(String title)
    {
        this.title = title;
    }

    @Override public void setViewId(String view)
    {
        this.selectedView = view;
    }

    @Override public String getViewId()
    {
        return this.selectedView;
    }

    public void setTemplateId(String templateId)
    {
        this.templateId = templateId;
    }

    /* use resources() timeIntervall and selectedDate function*/
    @Deprecated class CalendarModelParseContext implements ParseContext
    {
        public Function resolveVariableFunction(String variableName) throws IllegalAnnotationException
        {
            if (variableName.equals("allocatables"))
            {
                return new Variable(variableName)
                {
                    @Override public Object eval(EvalContext context)
                    {
                        try
                        {
                            return getSelectedAllocatablesSorted();
                        }
                        catch (RaplaException e)
                        {
                            return Collections.emptyList();
                        }
                    }

                };
            }
            else if (variableName.equals("timeIntervall"))
            {
                return new Variable(variableName)
                {
                    @Override public Object eval(EvalContext context)
                    {
                        return getTimeIntervall();
                    }

                };
            }
            else if (variableName.equals("selectedDate"))
            {
                return new Variable(variableName)
                {
                    @Override public Object eval(EvalContext context)
                    {
                        return getSelectedDate();
                    }

                };
            }
            return null;
        }

        @Override public FunctionFactory getFunctionFactory(String functionName)
        {
            return operator.getFunctionFactory(functionName);
        }

    }

    public TimeInterval getTimeIntervall()
    {
        return new TimeInterval(getStartDate(), getEndDate());
    }

    @Override public String getNonEmptyTitle()
    {
        String annotationName = null;
        return getNonEmptyTitle(annotationName);
    }

    public String getAnnotation(String annotationName) {
        ParseContext parseContext = new CalendarModelParseContext();
        ParsedText parsedTitle;
        try {
            parsedTitle = new ParsedText(title);
            parsedTitle.init(parseContext);
            return parsedTitle.getExternalRepresentation( parseContext);
        } catch (IllegalAnnotationException e) {
            return e.getMessage();
        }
    }

    public String getNonEmptyTitle(String annotationName) {
        String title = getTitle();
        if (title != null && title.trim().length() > 0)
        {
            ParseContext parseContext = new CalendarModelParseContext();
            ParsedText parsedTitle;
            try
            {
                parsedTitle = new ParsedText(title);
                parsedTitle.init(parseContext);
            }
            catch (IllegalAnnotationException e)
            {
                return e.getMessage();
            }
            final PermissionController permissionController = operator.getPermissionController();
            Map<String, Object> environment = operator.getThreadContextMap();
            EvalContext evalContext = new EvalContext(locale, annotationName, permissionController,environment, user, Collections.singletonList(this));
            String result = parsedTitle.formatName(evalContext);
            return result;
        }

        String types = "";
        /*
        String dateString = getRaplaLocale().formatDate(getSelectedDate());
        if  ( isListingAllocatables()) {
            try {
                Collection list = getSelectedObjectsAndChildren();
                if (list.size() == 1) {
                    Object obj = list.iterator().next();
                    if (!( obj instanceof DynamicType))
                    {
                        types = getI18n().format("allocation_view",getNamespace( obj ),dateString);
                    }
                }

            } catch (RaplaException ex) {
            }
            if ( types == null )
                types = getI18n().format("allocation_view",  getI18n().getString("resources_persons"));
        } else if ( isListingReservations()) {
             types =  getI18n().getString("reservations");
        } else {
            types = "unknown";
        }
        */

        return types;
    }

    @Override
    public String getFilename() {
        StringBuilder builder = new StringBuilder();
        String rawFilename = getNonEmptyTitle();
        if (rawFilename.isEmpty()) {
            try {
                rawFilename =operator.getPreferences(null, true).getEntryAsString(AbstractRaplaLocale.TITLE, "calendar");
            } catch (RaplaException e) {
                rawFilename = "calendar";
            }
        }

        final String str = convertToFilename(rawFilename);
        builder.append(str);
        builder.append("_");
        builder.append(SerializableDateTimeFormat.INSTANCE.formatDate( getStartDate(),false,null));
        builder.append("-");
        builder.append(SerializableDateTimeFormat.INSTANCE.formatDate( getEndDate(),true,null));
        final String name = builder.toString();
        return name;
    }

    private String convertToFilename(String filename) {
        return filename.replaceAll("[^A-Za-z0-9]","_");
    }


    public String getName(Object object)
    {
        if (object == null)
            return "";
        if (object instanceof Named)
        {
            String name = ((Named) object).getName(locale);
            return (name != null) ? name : "";
        }
        return object.toString();
    }

    private Collection<Allocatable> getFilteredAllocatables() throws RaplaException
    {
        Collection<Allocatable> list = new LinkedHashSet<>();
        // TODO should be replaced with getAllocatables(allocatableFilter.values();
        ClassificationFilter[] filters = allocatableFilter.values().toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);

        for (Allocatable allocatable : operator.getAllocatables(defaultResourceTypes ? null:filters))
        {
            if (canRead(allocatable))
            {
                list.add(allocatable);
            }
        }
        return list;
    }

    private boolean isInFilterAndCanRead(Allocatable allocatable)
    {
        return isInFilter(allocatable) && canRead(allocatable);
    }

    private boolean canRead(Allocatable allocatable)
    {
        final PermissionController permissionController = operator.getPermissionController();
        return user == null || permissionController.canRead(allocatable, user);
    }

    private boolean isInFilter(Allocatable classifiable)
    {
        //        if (isTemplateModus())
        //        {
        //            return true;
        //        }
        final Classification classification = classifiable.getClassification();
        final DynamicType type = classification.getType();
        final ClassificationFilter classificationFilter = allocatableFilter.get(type);
        if (classificationFilter != null)
        {
            final boolean matches = classificationFilter.matches(classification);
            return matches;
        }
        else
        {
            return defaultResourceTypes;
        }
    }

    public Collection<Allocatable> getAllAllocatables() throws RaplaException
    {
        Collection<Allocatable> allocatables = new ArrayList<>();
        for (RaplaObject obj : getSelectedObjectsAndChildren())
        {
            if (obj instanceof Allocatable)
            {
                allocatables.add((Allocatable) obj);
            }
        }
        final Collection<Allocatable> result = operator.getDependent(allocatables);
        return result;
    }

    protected Collection<RaplaObject> getSelectedObjectsAndChildren() throws RaplaException
    {
        Assert.notNull(selectedObjects);

        ArrayList<DynamicType> dynamicTypes = new ArrayList<>();
        for (Iterator<RaplaObject> it = selectedObjects.iterator(); it.hasNext(); )
        {
            Object obj = it.next();
            if (obj instanceof DynamicType)
            {
                dynamicTypes.add((DynamicType) obj);
            }
        }

        HashSet<RaplaObject> result = new LinkedHashSet<>();
        result.addAll(selectedObjects);

        boolean allAllocatablesSelected = selectedObjects.contains(CalendarModelImpl.ALLOCATABLES_ROOT);

        if (dynamicTypes.size() > 0 || allAllocatablesSelected)
        {
            Collection<Allocatable> filteredList = getFilteredAllocatables();
            for (Allocatable oneSelectedItem : filteredList)
            {
                if (selectedObjects.contains(oneSelectedItem))
                {
                    continue;
                }
                Classification classification = oneSelectedItem.getClassification();
                if (classification == null)
                {
                    continue;
                }
                if (allAllocatablesSelected || dynamicTypes.contains(classification.getType()))
                {
                    result.add(oneSelectedItem);
                    continue;
                }
            }
        }

        return result;
    }

    @Override public void setSelectedObjects(Collection<? extends Object> selectedObjects)
    {
        this.selectedObjects = retainRaplaObjects(selectedObjects);
        if (markedAllocatables != null && !markedAllocatables.isEmpty())
        {
            markedAllocatables = new LinkedHashSet<>(markedAllocatables);
            try
            {
                markedAllocatables.retainAll(getSelectedAllocatablesAsList());
            }
            catch (RaplaException e)
            {
                markedAllocatables = Collections.emptyList();
            }
        }
    }

    private List<RaplaObject> retainRaplaObjects(Collection<? extends Object> list)
    {
        List<RaplaObject> result = new ArrayList<>();
        for (Iterator<? extends Object> it = list.iterator(); it.hasNext(); )
        {
            Object obj = it.next();
            if (obj instanceof RaplaObject)
            {
                result.add((RaplaObject) obj);
            }
        }
        return result;
    }

    @Override public Collection<RaplaObject> getSelectedObjects()
    {
        return selectedObjects;
    }

    @Override public ClassificationFilter[] getReservationFilter() throws RaplaException
    {
        Collection<ClassificationFilter> filter;
        if (isDefaultEventTypes() /*|| isTemplateModus()*/)
        {
            filter = new ArrayList<>();
            for (DynamicType type : getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION))
            {
                filter.add(type.newClassificationFilter());
            }
        }
        else
        {
            filter = reservationFilter.values();
        }
        return filter.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
    }

    @Override public ClassificationFilter[] getAllocatableFilter() throws RaplaException
    {
        Collection<ClassificationFilter> filter;
        if (isDefaultResourceTypes() /*|| isTemplateModus()*/)
        {
            filter = new ArrayList<>();
            for (DynamicType type : getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
            {
                filter.add(type.newClassificationFilter());
            }
            for (DynamicType type : getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON))
            {
                filter.add(type.newClassificationFilter());
            }

        }
        else
        {
            filter = allocatableFilter.values();
        }
        return filter.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
    }

    public CalendarSelectionModel clone()
    {
        CalendarModelImpl clone;
        try
        {
            clone = new CalendarModelImpl(locale, user, operator);
            CalendarModelConfiguration config = createConfiguration();
            Map<String, String> alternativOptions = null;
            clone.setConfiguration(config, alternativOptions, true);
            clone.setAppointmentFilter( appointmentFilter);
        }
        catch (RaplaException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
        return clone;
    }

    @Override public Promise<AppointmentMapping> queryAppointmentBindings(TimeInterval interval)
    {
        if (operator instanceof org.rapla.storage.SyncStorageOperator)
        {
            try { return new ResolvedPromise<>(queryAppointmentBindingsSync(interval)); }
            catch (RaplaException e) { return new ResolvedPromise<>(e); }
        }
        final Collection<Allocatable> allocatables = new LinkedHashSet<>();
        final Collection<User> owners = new LinkedHashSet<>();
        final Collection<RaplaObject> selectedRaplaObjects;
        try { selectedRaplaObjects = getSelectedRaplaObjects(true); }
        catch (RaplaException e) { return new ResolvedPromise<>(e); }
        for (RaplaObject obj : selectedRaplaObjects)
        {
            if (obj instanceof Allocatable) allocatables.add((Allocatable) obj);
            if (obj instanceof User) owners.add((User) obj);
        }
        final LocalDateTime startDate = interval != null ? interval.getStart() : null;
        final LocalDateTime endDate = interval != null ? interval.getEnd() : null;
        final boolean useFilter = getSelectedConflicts().isEmpty() && getSelectedResourceRequests().isEmpty();
        final String cacheKey = createCacheKey(allocatables, startDate, endDate);
        if (cachingEnabled && cacheValidString != null && cacheValidString.equals(cacheKey) && cachedReservations != null)
        {
            return new ResolvedPromise<>(cachedReservations);
        }
        final ClassificationFilter[] reservationFilters;
        try { reservationFilters = isDefaultEventTypes() || !useFilter ? null : getReservationFilter(); }
        catch (RaplaException e) { return new ResolvedPromise<>(e); }
        return operator.queryAppointments(null, allocatables, owners, startDate, endDate, reservationFilters, templateId)
                .thenApply(map ->
                {
                    if (cachingEnabled)
                    {
                        cachedReservations = map;
                        cacheValidString = cacheKey;
                    }
                    return map;
                });
    }

    @Override public AppointmentMapping queryAppointmentBindingsSync(TimeInterval interval) throws RaplaException
    {
        final long start = System.currentTimeMillis();
        Collection<Allocatable> allocatables = new LinkedHashSet<>();
        Collection<User> owners = new LinkedHashSet<>();
        Collection<RaplaObject> selectedRaplaObjects = getSelectedRaplaObjects(true);
        for (RaplaObject raplaObject : selectedRaplaObjects) {
            if (raplaObject instanceof Allocatable) {
                allocatables.add((Allocatable) raplaObject);
            }
            if (raplaObject instanceof User) {
                owners.add((User) raplaObject);
            }
        }

        final long selectedAllocatableTimes = System.currentTimeMillis() - start;
        LocalDateTime startDate = interval != null ? interval.getStart() : null;
        LocalDateTime endDate = interval != null ? interval.getEnd() : null;

        boolean useFilter = getSelectedConflicts().isEmpty() && getSelectedResourceRequests().isEmpty();
        AppointmentMapping result = queryAppointmentBindingsSync(allocatables, owners, startDate, endDate, useFilter);
        LOGGER.debug("queryAppointments for {} resources took {} ms (selected allocatables {} ms). Found appointments for  {} resources.",
                allocatables.size(), (System.currentTimeMillis() - start), selectedAllocatableTimes, result.size());
        return result;
    }


    @Override public Promise<Collection<Reservation>> queryReservations(TimeInterval interval)
    {
        if (operator instanceof org.rapla.storage.SyncStorageOperator)
        {
            try { return new ResolvedPromise<>(queryReservationsSync(interval)); }
            catch (RaplaException e) { return new ResolvedPromise<>(e); }
        }
        return queryAppointmentBindings(interval)
                .thenApply(bindings -> getAllReservations(bindings.getAllAppointments()));
    }

    @Override public Collection<Reservation> queryReservationsSync(TimeInterval interval) throws RaplaException
    {
        Collection<Appointment> appointments;
        Collection<Conflict> conflicts = getSelectedConflicts();
        Collection<Reservation> requests = getSelectedResourceRequests();
        if (conflicts.size() > 0)
        {
            appointments = getAppointmentsSync(conflicts);
        }
        else if (requests.size() > 0)
        {
            appointments = getAppointmentsForRequestsSync(requests);
        }
        else
        {
            AppointmentMapping bindings = queryAppointmentBindingsSync(interval);
            appointments = bindings.getAllAppointments();
        }
        return getAllReservations(appointments);
    }

    public static Collection<Reservation> getAllReservations(Collection<Appointment> appointments)
    {
        return appointments.stream().map(Appointment::getReservation).distinct().collect(Collectors.toList());
    }

    String templateId = null;

    private String cacheValidString;
    /**
     * <b>Swing-legacy (PRD 030 Phase 6).</b> Cross-navigation cache for the
     * appointment-bindings query. Server-side rendered surfaces
     * ({@code /calendar/view}, {@code /table/*}, {@code /export/csv})
     * never hit this cache — each request runs the engine fresh. The cache
     * is retained for the in-process Swing path; it stays {@code disabled}
     * by default ({@link #cachingEnabled} {@code = false}), so it's
     * effectively dormant. Don't add new callers; don't remove until the
     * Swing client is retired.
     */
    private AppointmentMapping cachedReservations;
    private boolean cachingEnabled = false;

    private AppointmentMapping queryAppointmentBindingsSync(Collection<Allocatable> allocatables, final Collection<User> owners, LocalDateTime start, LocalDateTime end, boolean useFilter) throws RaplaException
    {
        final String cacheKey = createCacheKey(allocatables, start, end);
        if (cachingEnabled)
        {
            if (cacheValidString != null && cacheValidString.equals(cacheKey) && cachedReservations != null)
            {
                return cachedReservations;
            }
        }

        ClassificationFilter[] reservationFilters = isDefaultEventTypes() || !useFilter ? null : getReservationFilter();

        // FIXME Evalute if its only the owner
        User user = null;
        AppointmentMapping map = requireSyncOperator().queryAppointmentsSync(user, allocatables, owners, start, end, reservationFilters, templateId);
        if (cachingEnabled)
        {
            cachedReservations = map;
            cacheValidString = cacheKey;
        }
        return map;
    }

    private org.rapla.storage.SyncStorageOperator requireSyncOperator()
    {
        if (!(operator instanceof org.rapla.storage.SyncStorageOperator))
        {
            throw new UnsupportedOperationException("Sync CalendarModel methods require an in-process StorageOperator (server-side); the current operator is " + operator.getClass().getName());
        }
        return (org.rapla.storage.SyncStorageOperator) operator;
    }

    public void invalidateCache()
    {
        cacheValidString = null;
        cachedReservations = null;
    }

    private String createCacheKey(Collection<Allocatable> allocatables, LocalDateTime start, LocalDateTime end)
    {
        StringBuilder buf = new StringBuilder();
        if (allocatables != null)
        {
            for (Allocatable alloc : allocatables)
            {
                buf.append(alloc.getId());
                buf.append(";");
            }
        }
        else
        {
            buf.append("all_reservations;");
        }
        if (start != null)
        {
            buf.append(DateTools.toMilli(start) + ";");
        }
        if (end != null)
        {
            buf.append(DateTools.toMilli(end) + ";");
        }
        return buf.toString();
    }

    public void setCachingEnabled(boolean enable)
    {
        this.cachingEnabled = enable;
    }

    @Override public List<Allocatable> getSelectedAllocatablesSorted() throws RaplaException
    {
        List<Allocatable> result = new ArrayList<>(getSelectedAllocatablesAsList());
        long start = System.currentTimeMillis();
        Collections.sort(result, new SortedClassifiableComparator(locale));
        LOGGER.debug("sort allocatables took {} ms for {} objects.", (System.currentTimeMillis() - start), result.size());

        //List<Allocatable> filled = operator.queryDependent(result);
        return result;
    }


    public Collection<Allocatable> getSelectedAllocatablesAsList() throws RaplaException
    {
        Collection<Allocatable> selectedRaplaObjects = getSelectedRaplaObjects(false).stream().map(x->(Allocatable)x).collect(Collectors.toSet());
        return selectedRaplaObjects;
    }

    @NotNull
    private Collection<RaplaObject> getSelectedRaplaObjects(boolean addUser) throws RaplaException {
        long start = System.currentTimeMillis();

        Collection<RaplaObject> result = new HashSet<>();
        Collection<RaplaObject> selectedObjectsAndChildren = getSelectedObjectsAndChildren();
        boolean conflictsDetected = false;
        for (RaplaObject object : selectedObjectsAndChildren)
        {
            Allocatable alloc = null;
            if (object.getTypeClass() == Conflict.class || object.getTypeClass() == Reservation.class)
            {
                if (!conflictsDetected)
                {
                    // We ignore the allocatable selection if there are conflicts selected
                    result.clear();
                    conflictsDetected = true;
                }
                if (object.getTypeClass() == Conflict.class) {
                    alloc = ((Conflict) object).getAllocatable();
                } else {
                    Collection<Allocatable> requestedAllocatables = ((Reservation) object).getRequestedAllocatables();
                    result.addAll( requestedAllocatables );
                }

            }
            if (!conflictsDetected && object.getTypeClass() == Allocatable.class)
            {
                alloc = (Allocatable) object;
            }
            if (alloc != null && isInFilterAndCanRead(alloc))
            {
                result.add(alloc);
            }
            if (object.getTypeClass() == User.class && addUser)
            {
                User owner  = (User) object;
                result.add( owner);
            }
        }
        LOGGER.debug("getSelectedAllocatables took {} ms for {} objects.", (System.currentTimeMillis() - start), result.size());

        return result;
    }


    public Collection<Conflict> getSelectedConflicts()
    {
        return getSelected(Conflict.class);
    }

    public Collection<Reservation> getSelectedResourceRequests()
    {
        return getSelected(Reservation.class);
    }


    public Set<DynamicType> getSelectedTypes(String classificationType) throws RaplaException
    {
        Set<DynamicType> result = new HashSet<>();
        Iterator<RaplaObject> it = getSelectedObjectsAndChildren().iterator();
        while (it.hasNext())
        {
            RaplaObject object = it.next();
            if (object.getTypeClass() == DynamicType.class)
            {
                if (classificationType == null || ((DynamicType) object).getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE)
                        .equals(classificationType))
                {
                    result.add((DynamicType) object);
                }
            }
        }
        return result;
    }

    private <T extends RaplaObject<T>> Set<T> getSelected(Class<T> type)
    {
        Set<T> result = new HashSet<>();
        Iterator<RaplaObject> it = getSelectedObjects().iterator();
        while (it.hasNext())
        {
            RaplaObject object = it.next();
            if (object.getTypeClass() == type)
            {
                @SuppressWarnings("unchecked") T casted = (T) object;
                result.add(casted);
            }
        }
        return result;
    }


    @Override public String getOption(String name)
    {
        return optionMap.get(name);
    }

    @Override public void setOption(String name, String string)
    {
        if (string == null)
        {
            optionMap.remove(name);
        }
        else
        {
            optionMap.put(name, string);
        }
    }

    @Override public boolean isDefaultEventTypes()
    {
        return defaultEventTypes;
    }

    @Override public boolean isDefaultResourceTypes()
    {
        return defaultResourceTypes;
    }

    public Promise<Void> save(final String filename)
    {

        final CalendarModelConfiguration conf;
        final Collection toEdit;
        try {
            conf = createConfiguration();
            final Preferences preferences = operator.getPreferences(user, true);
            toEdit = Collections.singleton(preferences);
        }
        catch (RaplaException ex)
        {
            return new ResolvedPromise<>(ex);
        }
        boolean isUndo = false;
        final Promise<Map<Entity,Entity>>  editPromise = operator.editObjectsAsync(toEdit, user, isUndo);
        final Promise<Set<Preferences>> modifyPromise = editPromise.thenApply((editables) ->
        {
            Preferences clone = (Preferences) editables.values().iterator().next();
            if (filename == null) {
                clone.putEntry(CalendarModelConfiguration.CONFIG_ENTRY, conf);
            } else {
                RaplaMap< CalendarModelConfiguration> exportMap = clone.getEntry(EXPORT_ENTRY);
                Map<String, CalendarModelConfiguration> newMap;
                if (exportMap == null)
                    newMap = new TreeMap<>();
                else
                    newMap = new TreeMap<>(exportMap.toMap());
                newMap.put(filename, conf);
                RaplaMapImpl map = new RaplaMapImpl(newMap);
                map.setResolver(operator);
                clone.putEntry(EXPORT_ENTRY, map);
            }
            return Collections.singleton(clone);
        });
        Promise<Void> result = modifyPromise.thenCompose((toStore) -> operator.storeAndRemoveAsync(toStore, Collections.emptyList(), user, false ));
        return result;
    }

    // Old defaultname behaviour. Duplication of language resource names. But the system has to be replaced anyway in the future, because it doesnt allow for multiple language outputs on the server.
    private boolean isOldDefaultNameBehavoir(final String filename)
    {
        List<String> translations = new ArrayList<>();
        translations.add("default");
        translations.add("Default");
        translations.add("Standard");
        translations.add("Standaard");
        // special for polnish
        if (filename.startsWith("Domy") && filename.endsWith("lne"))
        {
            return true;
        }
        if (filename.startsWith("D") && filename.endsWith("faut"))
        {
            return true;
        }

        if (filename.startsWith("Est") && filename.endsWith("ndar"))
        {
            return true;
        }
        return translations.contains(filename);
    }

    @Override public void load(final String filename) throws RaplaException {
        final CalendarModelConfiguration modelConfig;
        boolean createIfNotNull = false;

        {
            final Preferences preferences = operator.getPreferences(user, createIfNotNull);
            modelConfig = getModelConfig(filename, preferences);
        }
        if (modelConfig == null && filename != null)
        {
            throw new CalendarNotFoundExeption("Calendar with name " + filename + " not found.");
        }
        else
        {
            final boolean isDefault = filename == null;
            Map<String, String> alternativeOptions = new HashMap<>();
            if (modelConfig != null && modelConfig.getOptionMap() != null)
            {

                // All old default calendars have no selected date
                Map<String, String> optionMap = modelConfig.getOptionMap();
                String notPlanned = optionMap.get(PlanningStatusPlugin.PUBLISH_NON_PLANNED);
                if ( notPlanned == null)
                {
                    alternativeOptions.put(PlanningStatusPlugin.PUBLISH_NON_PLANNED, "false");
                }
                if (isDefault && (optionMap.get(CalendarModel.SAVE_SELECTED_DATE) == null))
                {
                    alternativeOptions.put(CalendarModel.SAVE_SELECTED_DATE, "false");
                }
                // All old calendars are exported
                if (!isDefault && optionMap.get(HTML_EXPORT_ENABLED) == null)
                {
                    alternativeOptions.put(HTML_EXPORT_ENABLED, "true");
                }
            }
            setConfiguration(modelConfig, alternativeOptions,true);
        }
    }

    public CalendarModelConfiguration getModelConfig(final String filename, final Preferences preferences)
    {
        final CalendarModelConfiguration modelConfig;
        if (preferences != null)
        {
            final boolean isDefault = filename == null;
            if (isDefault)
            {
                modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
            }
            else if (filename != null && !isDefault)
            {
                RaplaMap<CalendarModelConfiguration> entry = preferences.getEntry(EXPORT_ENTRY);
                final CalendarModelConfiguration config;
                if ( entry != null) {
                    Map<String, CalendarModelConfiguration> exportMap = entry.toMap();
                    if (exportMap != null) {
                        config = exportMap.get(filename);
                    } else {
                        config = null;
                    }
                } else {
                    config = null;
                }
                if (config == null && isOldDefaultNameBehavoir(filename))
                {
                    modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
                }
                else
                {
                    modelConfig = config;
                }
            }
            else
            {
                modelConfig = null;
            }
        }
        else
        {
            modelConfig = null;
        }
        return modelConfig;
    }

    //Set<Appointment> conflictList = new HashSet<Appointment>();
    //  if ( selectedConflicts != null)
    //  {
    //      for (Conflict conflict: selectedConflicts)
    //      {
    //          if ( conflict.getAppointment1().equals( app.getId()))
    //          {
    //              conflictList.add(conflict.getAppointment2());
    //          }
    //          else if ( conflict.getAppointment2().equals( app.getId()))
    //          {
    //              conflictList.add(conflict.getAppointment1());
    //          }
    //      }
    //  }

    private Collection<Appointment> getAppointmentsSync(Collection<Conflict> conflicts) throws RaplaException
    {
        Collection<ReferenceInfo<Reservation>> ids = new HashSet<>();
        Collection<ReferenceInfo<Appointment>> appointmentIds = new HashSet<>();
        for (Conflict conflict : conflicts)
        {
            ids.add(conflict.getReservation1());
            ids.add(conflict.getReservation2());
            appointmentIds.add(conflict.getAppointment1());
            appointmentIds.add(conflict.getAppointment2());
        }
        Map<ReferenceInfo<Reservation>, Reservation> values = requireSyncOperator().getFromIdSync(ids, true);
        return values.values().stream()
                .flatMap(Reservation::getAppointmentStream)
                .filter((app) -> appointmentIds.contains(app.getReference()))
                .collect(Collectors.toList());
    }

    private Collection<Appointment> getAppointmentsForRequestsSync(Collection<Reservation> requests) throws RaplaException
    {
        Collection<Appointment> selectedAppointments = ReservationImpl.getRequestedAppointments(requests);
        Collection<ReferenceInfo<Reservation>> ids = new HashSet<>();
        for (Reservation request : requests) {
            ids.add(request.getReference());
        }
        Map<ReferenceInfo<Reservation>, Reservation> values = requireSyncOperator().getFromIdSync(ids, true);
        return values.values().stream()
                .flatMap(Reservation::getAppointmentStream)
                .filter((app) -> selectedAppointments.contains(app))
                .collect(Collectors.toList());
    }



    @Override public Promise<List<AppointmentBlock>> queryBlocks(final TimeInterval timeInterval)
    {
        if (operator instanceof org.rapla.storage.SyncStorageOperator)
        {
            try { return new ResolvedPromise<>(queryBlocksSync(timeInterval)); }
            catch (RaplaException e) { return new ResolvedPromise<>(e); }
        }
        final LocalDateTime start = getStartDate();
        final LocalDateTime end   = getEndDate();
        return queryAppointments(timeInterval).thenApply(apps ->
        {
            List<AppointmentBlock> blocks = new ArrayList<>();
            for (Appointment a : apps)
            {
                a.createBlocks(start, end, blocks);
            }
            return blocks;
        });
    }

    @Override public List<AppointmentBlock> queryBlocksSync(final TimeInterval timeInterval) throws RaplaException
    {
        List<AppointmentBlock> appointments = new ArrayList<>();
        Collection<Conflict> selectedConflicts = getSelectedConflicts();
        Collection<Reservation> requests = getSelectedResourceRequests();
        Collection<Appointment> conflictAppointments;
        if (!selectedConflicts.isEmpty()) {
            conflictAppointments = getAppointmentsSync(selectedConflicts);
        } else {
            conflictAppointments = getAppointmentsForRequestsSync(requests);
        }
        Collection<Appointment> allAppointments = queryAppointmentsSync(timeInterval);
        Map<Appointment, Set<Appointment>> conflictingAppointments = ConflictImpl.getMap(selectedConflicts, conflictAppointments);
        for (Appointment app : allAppointments)
        {
            Collection<Appointment> conflictList = conflictingAppointments.get(app);
            if (conflictList == null || conflictList.isEmpty())
            {
                app.createBlocks(getStartDate(), getEndDate(), appointments);
            }
            else
            {
                List<AppointmentBlock> blocks = new ArrayList<>();
                app.createBlocks(getStartDate(), getEndDate(), blocks);
                Iterator<AppointmentBlock> it = blocks.iterator();
                while (it.hasNext())
                {
                    AppointmentBlock block = it.next();
                    boolean found = false;
                    for (Appointment conflictingApp : conflictList)
                    {
                        if (conflictingApp.overlapsBlock(block))
                        {
                            found = true;
                            break;
                        }
                    }
                    if (!found)
                    {
                        it.remove();
                    }
                }
                appointments.addAll(blocks);
            }
        }
        Collections.sort(appointments);
        return appointments;
    }

    private DynamicType[] getDynamicTypes(String elementKey) throws RaplaException
    {
        User user = this.user;
        return FacadeImpl.getDynamicTypes(operator, elementKey, user);
    }

    @Override public DynamicType guessNewEventType() throws RaplaException
    {
        Set<DynamicType> selectedTypes = getSelectedTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        DynamicType guessedType;
        if (selectedTypes.size() > 0)
        {
            guessedType = selectedTypes.iterator().next();
        }
        else
        {
            guessedType = getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        }
        ClassificationFilter[] reservationFilter = getReservationFilter();
        DynamicType firstType = null;
        boolean found = false;
        // assure that the guessed type is in the filter selection list
        for (ClassificationFilter filter : reservationFilter)
        {
            DynamicType type = filter.getType();
            if (firstType == null)
            {
                firstType = type;
            }
            if (type.equals(guessedType))
            {
                found = true;
                break;
            }
        }
        if (!found && firstType != null)
        {
            guessedType = firstType;
        }
        return guessedType;
    }

    @Override public Collection<TimeInterval> getMarkedIntervals()
    {
        return timeIntervals;
    }

    @Override public void setMarkedIntervals(Collection<TimeInterval> timeIntervals, boolean timeEnabled)
    {
        if (timeIntervals != null)
        {
            this.timeIntervals = Collections.unmodifiableCollection(timeIntervals);
            markedIntervalTimeEnabled = timeEnabled;
        }
        else
        {
            this.timeIntervals = Collections.emptyList();
            markedIntervalTimeEnabled = false;
        }
    }

    @Override public void markInterval(LocalDateTime start, LocalDateTime end)
    {
        TimeInterval timeInterval = new TimeInterval(start, end);
        setMarkedIntervals(Collections.singletonList(timeInterval), false);
    }

    @Override public Collection<Allocatable> getMarkedAllocatables()
    {
        return markedAllocatables;
    }

    @Override public void setMarkedAllocatables(Collection<Allocatable> allocatables)
    {
        this.markedAllocatables = allocatables;
    }

    @Override public boolean isMarkedIntervalTimeEnabled()
    {
        return markedIntervalTimeEnabled;
    }

    public Promise<Collection<Appointment>> queryAppointments(TimeInterval interval)
    {
        if (operator instanceof org.rapla.storage.SyncStorageOperator)
        {
            try { return new ResolvedPromise<>(queryAppointmentsSync(interval)); }
            catch (RaplaException e) { return new ResolvedPromise<>(e); }
        }
        return queryAppointmentBindings(interval)
                .thenApply(bindings -> bindings.getAllAppointments(appointmentFilter));
    }

    @Override public Collection<Appointment> queryAppointmentsSync(TimeInterval interval) throws RaplaException
    {
        AppointmentMapping bindings = queryAppointmentBindingsSync(interval);
        return bindings.getAllAppointments(appointmentFilter);
    }

    public static String getStartEndDate(RaplaLocale raplaLocale, CalendarSelectionModel model) {
        String dateString;
        final String viewId = model.getViewId();
        if( viewId != null && viewId.startsWith("table"))
            dateString = raplaLocale.formatDate(model.getStartDate()) + " - " + raplaLocale.formatDate(model.getEndDate());
        else
            dateString =  raplaLocale.formatDate(model.getSelectedDate());
        return dateString;
    }

    @Override
    public void setAppointmentFilter(Predicate<Appointment> appointmentFilter) {
        this.appointmentFilter = appointmentFilter;
    }

    @Override
    public Predicate<Appointment> getAppointmentFilter() {
        return appointmentFilter;
    }
}


