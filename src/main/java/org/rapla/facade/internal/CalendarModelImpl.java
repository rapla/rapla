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

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
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
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.SortedClassifiableComparator;
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
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.rapla.entities.configuration.CalendarModelConfiguration.EXPORT_ENTRY;

@Singleton
@DefaultImplementation(of = CalendarSelectionModel.class, context = InjectionContext.client)
@DefaultImplementation(of = CalendarModel.class, context = InjectionContext.client)
public class CalendarModelImpl implements CalendarSelectionModel
{
    private static final String DEFAULT_VIEW = "week";//WeekViewFactory.WEEK_VIEW;
    private static final String ICAL_EXPORT_ENABLED = "org.rapla.plugin.export2ical" + ".selected";
    private static final String HTML_EXPORT_ENABLED = EXPORT_ENTRY + ".selected";
    private final Logger logger;
    Date startDate;
    Date endDate;
    Date selectedDate;
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

    @Inject public CalendarModelImpl(ClientFacade clientFacade, RaplaLocale locale) throws RaplaInitializationException
    {
        this(locale.getLocale(), getUser(clientFacade), ((ClientFacadeImpl)clientFacade).getOperator(), ((ClientFacadeImpl) clientFacade).getLogger());
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

    public CalendarModelImpl(Locale locale, User user, StorageOperator operator, Logger logger) throws RaplaInitializationException
    {
        this.logger = logger.getChildLogger("calendarmodel");
        this.locale = locale;
        this.operator = operator;
        Date today = this.operator.today();
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
            if (user != null && !user.isAdmin())
            {
                boolean selected = getSystemPreferences().getEntryAsBoolean(CalendarModel.ONLY_MY_EVENTS_DEFAULT, true);
                optionMap.put(CalendarModel.ONLY_MY_EVENTS, selected ? "true" : "false");
            }
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

    public boolean setConfiguration(CalendarModelConfiguration config, final Map<String, String> alternativOptions) throws RaplaException
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
        else
        {
            setSelectedDate(operator.today());
        }
        final Date startDate = config.getStartDate();
        if (startDate != null && isSaveDate)
        {
            setStartDate(startDate);
        }
        else
        {
            setStartDate(operator.today());
        }
        final Date endDate = config.getEndDate();
        if (endDate != null && isSaveDate)
        {
            setEndDate(endDate);
        }
        else
        {
            setEndDate( (endDate != null && startDate != null) ? DateTools.addDays( getStartDate(), DateTools.countDays(startDate, endDate)) : DateTools.addYear(this.startDate));
        }
        selectedObjects.addAll(config.getSelected());
        if (config.isResourceRootSelected())
        {
            selectedObjects.add(ALLOCATABLES_ROOT);
        }

        Set<User> selectedUsers = getSelected(User.class);
        User currentUser = getUser();
        if (currentUser != null && selectedUsers.size() == 1 && selectedUsers.iterator().next().equals(currentUser))
        {
            if (getOption(CalendarModel.ONLY_MY_EVENTS) == null && currentUser.isAdmin())
            {
                boolean selected = getSystemPreferences().getEntryAsBoolean(CalendarModel.ONLY_MY_EVENTS_DEFAULT, true);
                setOption(CalendarModel.ONLY_MY_EVENTS, selected ? "true" : "false");
                selectedObjects.remove(currentUser);
            }
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
            if (!(object instanceof Conflict) && (object instanceof Entity))
            {
                //  throw new RaplaException("Storing the conflict view is not possible with Rapla.");
                selected.add((Entity) object);
            }
        }

        final Date selectedDate = getSelectedDate();
        final Date startDate = getStartDate();
        final Date endDate = getEndDate();
        boolean resourceRootSelected = selectedObjects.contains(ALLOCATABLES_ROOT);
        return newRaplaCalendarModel(selected, resourceRootSelected, allocatableFilter, eventFilter, title, startDate, endDate, selectedDate, viewName,
                optionMap);
    }

    public CalendarModelConfigurationImpl newRaplaCalendarModel(Collection<Entity> selected, boolean resourceRootSelected,
            ClassificationFilter[] allocatableFilter, ClassificationFilter[] eventFilter, String title, Date startDate, Date endDate, Date selectedDate,
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

    @Override public Date getSelectedDate()
    {
        return selectedDate;
    }

    @Override public void setSelectedDate(Date date)
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

    @Override public Date getStartDate()
    {
        return startDate;
    }

    @Override public void setStartDate(Date date)
    {
        if (date == null)
            throw new IllegalStateException("Date can't be null");
        this.startDate = date;
    }

    @Override public Date getEndDate()
    {
        return endDate;
    }

    @Override public void setEndDate(Date date)
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
            EvalContext evalContext = new EvalContext(locale, null, permissionController, user, Collections.singletonList(this));
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
            clone = new CalendarModelImpl(locale, user, operator, logger);
            CalendarModelConfiguration config = createConfiguration();
            Map<String, String> alternativOptions = null;
            clone.setConfiguration(config, alternativOptions);
        }
        catch (RaplaException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
        return clone;
    }

    @Override public Promise<Map<Allocatable, Collection<Appointment>>> queryAppointmentBindings(TimeInterval interval)
    {
        final boolean debugEnabled = logger.isDebugEnabled();
        final long start = debugEnabled ? System.currentTimeMillis() : 0;
        Collection<Allocatable> allocatables;
        try
        {
            allocatables= getSelectedAllocatablesAsList();
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }

        final long selectedAllocatableTimes =  (debugEnabled) ?  System.currentTimeMillis() - start: 0;
        Date startDate = interval != null ? interval.getStart() : null;
        Date endDate = interval != null ? interval.getEnd() : null;

        boolean useFilter = getSelectedConflicts().isEmpty();
        final Promise<Map<Allocatable, Collection<Appointment>>> reservations = queryAppointmentBindings(allocatables, startDate, endDate, useFilter);
        reservations.thenAccept( (res) -> {
            if (debugEnabled)
            {
                logger.debug("queryAppointments for " + allocatables.size() + " resources took " + (System.currentTimeMillis() - start) + " ms (selected allocatables "
                        + selectedAllocatableTimes + " ms). Found appointments for  " + res.size() + " resources.");
            }
        });
        return reservations;
    }

    @Override public Promise<Collection<Reservation>> queryReservations(TimeInterval interval)
    {
        Promise<Collection<Appointment>> appointments;
        Collection<Conflict> conflicts = getSelectedConflicts();
        if (conflicts.size() > 0)
        {
            appointments = getAppointments(conflicts);
        }
        else
        {
            final Promise<Map<Allocatable, Collection<Appointment>>> appointments1 = queryAppointmentBindings(interval);
            appointments = appointments1.thenApply((apps) ->getAllAppointments(apps));
        }
        Promise<Collection<Reservation>> asList = appointments.thenApply((apps)->getAllReservations(apps));
        return asList;
    }

    public static Collection<Reservation> getAllReservations(Collection<Appointment> appointments)
    {
        return appointments.stream().map(Appointment::getReservation).distinct().collect(Collectors.toList());
    }

    public static Collection<Reservation> getAllReservations(Map<Allocatable, Collection<Appointment>> appointmentMap)
    {
        final Collection<Appointment> allAppointments = getAllAppointments(appointmentMap);
        return getAllReservations(allAppointments);
    }

    public static Collection<Appointment> getAllAppointments(Map<Allocatable, Collection<Appointment>> appointmentMap)
    {
        Collection<Appointment> allAppointments = new LinkedHashSet<>();
        for (Collection<Appointment> appointments : appointmentMap.values())
        {
            allAppointments.addAll(appointments);
        }
        return allAppointments;
    }

    String templateId = null;

    private String cacheValidString;
    private Map<Allocatable, Collection<Appointment>> cachedReservations;
    private boolean cachingEnabled = false;

    private Promise<Map<Allocatable, Collection<Appointment>>> queryAppointmentBindings(Collection<Allocatable> allocatables, Date start, Date end, boolean useFilter)
    {
        final String cacheKey = createCacheKey(allocatables, start, end);
        if (cachingEnabled)
        {
            if (cacheValidString != null && cacheValidString.equals(cacheKey) && cachedReservations != null)
            {
                return new ResolvedPromise<>(cachedReservations);
            }
        }

        ClassificationFilter[] reservationFilters;
		try {
			reservationFilters = isDefaultEventTypes() || !useFilter ? null : getReservationFilter();
		} catch (RaplaException ex) {
			return new ResolvedPromise<>( ex);
		}
		// FIXME Evalute if its only the owner
		User user = null;
        final Promise<Map<Allocatable, Collection<Appointment>>> reservationsAsync = operator
                .queryAppointments(user, allocatables, start, end, reservationFilters, templateId);

        return reservationsAsync.thenApply((map) -> {
            if (cachingEnabled)
            {
                cachedReservations = map;
                cacheValidString = cacheKey;
            }
            return map;
        });
    }

    public void invalidateCache()
    {
        cacheValidString = null;
        cachedReservations = null;
    }

    private String createCacheKey(Collection<Allocatable> allocatables, Date start, Date end)
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
            buf.append(start.getTime() + ";");
        }
        if (end != null)
        {
            buf.append(end.getTime() + ";");
        }
        return buf.toString();
    }

    public void setCachingEnabled(boolean enable)
    {
        this.cachingEnabled = enable;
    }


    private boolean isNoAllocatableSelected()
    {
        for (RaplaObject obj : getSelectedObjects())
        {
            Class<? extends RaplaObject> raplaType = obj.getTypeClass();
            if (raplaType == Allocatable.class)
            {
                return false;
            }
            else if (raplaType == DynamicType.class)
            {
                DynamicType type = (DynamicType) obj;
                String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                if (annotation.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON) || annotation
                        .equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
                {
                    return false;
                }
            }
            else if (obj.equals(ALLOCATABLES_ROOT))
            {
                return false;
            }
        }
        return true;
    }

    @Override public List<Allocatable> getSelectedAllocatablesSorted() throws RaplaException
    {
        List<Allocatable> result = new ArrayList<>(getSelectedAllocatablesAsList());
        long start = 0;
        final boolean debugEnabled = logger.isDebugEnabled();
        if (debugEnabled)
            start = System.currentTimeMillis();
        Collections.sort(result, new SortedClassifiableComparator(locale));
        if (debugEnabled)
        {
            logger.debug("sort allocatables took " + (System.currentTimeMillis() - start) + " ms for " + result.size() + " objects.");
        }

        //List<Allocatable> filled = operator.queryDependent(result);
        return result;
    }

    public Collection<Allocatable> getSelectedAllocatablesAsList() throws RaplaException
    {

        long start = 0;
        final boolean debugEnabled = logger.isDebugEnabled();
        if (debugEnabled)
            start = System.currentTimeMillis();

        Collection<Allocatable> result = new HashSet<>();
        Collection<RaplaObject> selectedObjectsAndChildren = getSelectedObjectsAndChildren();
        boolean conflictsDetected = false;
        for (RaplaObject object : selectedObjectsAndChildren)
        {
            Allocatable alloc = null;
            if (object.getTypeClass() == Conflict.class)
            {
                if (!conflictsDetected)
                {
                    // We ignore the allocatable selection if there are conflicts selected
                    result.clear();
                    conflictsDetected = true;
                }
                alloc = ((Conflict) object).getAllocatable();

            }
            if (!conflictsDetected && object.getTypeClass() == Allocatable.class)
            {
                alloc = (Allocatable) object;
            }
            if (alloc != null && isInFilterAndCanRead(alloc))
            {
                result.add(alloc);
            }
        }
        if (debugEnabled)
        {
            logger.debug("getSelectedAllocatables took " + (System.currentTimeMillis() - start) + " ms for " + result.size() + " objects.");
        }

        return result;
    }

    public Collection<Conflict> getSelectedConflicts()
    {
        return getSelected(Conflict.class);
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

    @Override public boolean isOnlyCurrentUserSelected()
    {
        String option = getOption(CalendarModel.ONLY_MY_EVENTS);
        return option != null && option.equalsIgnoreCase("TRUE");
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
        Promise<Void> result = modifyPromise.thenCompose((toStore) -> operator.storeAndRemoveAsync(toStore, Collections.emptyList(), user));
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

    @Override public void load(final String filename) throws RaplaException, CalendarNotFoundExeption
    {
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
                if (isDefault && (modelConfig.getOptionMap().get(CalendarModel.SAVE_SELECTED_DATE) == null))
                {
                    alternativeOptions.put(CalendarModel.SAVE_SELECTED_DATE, "false");
                }
                // All old calendars are exported
                if (!isDefault && modelConfig.getOptionMap().get(HTML_EXPORT_ENABLED) == null)
                {
                    alternativeOptions.put(HTML_EXPORT_ENABLED, "true");
                }
            }
            setConfiguration(modelConfig, alternativeOptions);
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
                Map<String, CalendarModelConfiguration> exportMap = preferences.getEntry(EXPORT_ENTRY).toMap();
                final CalendarModelConfiguration config;
                if (exportMap != null)
                {
                    config = exportMap.get(filename);
                }
                else
                {
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

    private Promise<Collection<Appointment>> getAppointments(Collection<Conflict> conflicts)
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
        return operator.getFromIdAsync(ids, true).thenApply(values->
                {
                    Stream<Appointment> appointments = values.values().stream().flatMap(Reservation::getAppointmentStream).filter( (app)->appointmentIds.contains(app.getReference()));
                    return  appointments.collect(Collectors.toList());
                }
        );
    }

    @Override public Promise<List<AppointmentBlock>> queryBlocks(final TimeInterval timeInterval)
    {
        List<AppointmentBlock> appointments = new ArrayList<>();
        try
        {
            final Set<Allocatable> selectedAllocatables = isNoAllocatableSelected() ? null : new HashSet<>(getSelectedAllocatablesAsList());
            Collection<Conflict> selectedConflicts = getSelectedConflicts();
            Promise<Collection<Appointment>> reservations = getAppointments(selectedConflicts);
            final Promise<Collection<Appointment>> appointmentPromise = queryAppointments(timeInterval);
            return appointmentPromise.thenCombine(reservations, ( allAppointments, conflictAppointments) -> {

                Map<Appointment, Set<Appointment>> conflictingAppointments = ConflictImpl.getMap(selectedConflicts, conflictAppointments);
                for (Appointment app : allAppointments)
                {
                    Reservation event = app.getReservation();
                    Stream<Allocatable> allocatablesFor = event.getAllocatablesFor(app);
                    if (selectedAllocatables == null || allocatablesFor.anyMatch(selectedAllocatables::contains))
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
                }
                Collections.sort(appointments);
                return appointments;
            });
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
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

    @Override public void markInterval(Date start, Date end)
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
        Promise<Map<Allocatable, Collection<Appointment>>> bindings = queryAppointmentBindings(interval);
        return bindings.thenApply( (bind) -> {
            Set<Appointment> result = new LinkedHashSet<>();
            for (Collection<Appointment> appointments : bind.values())
            {
                result.addAll(appointments);
            }
            return result;
        });
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

}


