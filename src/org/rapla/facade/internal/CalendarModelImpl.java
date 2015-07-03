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

import static org.rapla.entities.configuration.CalendarModelConfiguration.EXPORT_ENTRY;

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

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ParsedText;
import org.rapla.entities.dynamictype.internal.ParsedText.EvalContext;
import org.rapla.entities.dynamictype.internal.ParsedText.Function;
import org.rapla.entities.dynamictype.internal.ParsedText.ParseContext;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.storage.UpdateResult;

public class CalendarModelImpl implements CalendarSelectionModel
{
    private static final String DEFAULT_VIEW = "week";//WeekViewFactory.WEEK_VIEW;
    private static final String ICAL_EXPORT_ENABLED = "org.rapla.plugin.export2ical"+ ".selected";
	private static final String HTML_EXPORT_ENABLED = EXPORT_ENTRY + ".selected";
	Date startDate;
    Date endDate;
    Date selectedDate;
    Collection<RaplaObject> selectedObjects = new LinkedHashSet<RaplaObject>();
    String title;
    ClientFacade m_facade;
    String selectedView;
    //RaplaContext context;
    //RaplaLocale raplaLocale;
    User user;
    Map<String,String> optionMap = new HashMap<String,String>();
    //Map<String,String> viewOptionMap = new HashMap<String,String>();

    boolean defaultEventTypes = true;
    boolean defaultResourceTypes = true;
    Collection<TimeInterval> timeIntervals = Collections.emptyList();
    Collection<Allocatable> markedAllocatables = Collections.emptyList();
    Locale locale;
    boolean markedIntervalTimeEnabled = false;
    Map<DynamicType,ClassificationFilter> reservationFilter = new LinkedHashMap<DynamicType, ClassificationFilter>();
    Map<DynamicType,ClassificationFilter> allocatableFilter = new LinkedHashMap<DynamicType, ClassificationFilter>();
    public static final RaplaConfiguration ALLOCATABLES_ROOT = new RaplaConfiguration("rootnode", "allocatables");

    public CalendarModelImpl(RaplaContext context, User user, ClientFacade facade) throws RaplaException 
    {
        this( context.lookup(RaplaLocale.class).getLocale(), user, facade);
    }
    
    public CalendarModelImpl(Locale locale, User user, ClientFacade facade) throws RaplaException {
        this.locale = locale;
        m_facade = facade;
        if ( user == null && m_facade.isSessionActive()) {
            user = m_facade.getUser();
        }
        Date today = m_facade.today();
        setSelectedDate( today);
        setStartDate( today);
        setEndDate( DateTools.addYear(getStartDate()));
        DynamicType[] types = m_facade.getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        if ( types.length == 0 ) {
            types = m_facade.getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
        }
        setSelectedObjects( types.length > 0 ? Collections.singletonList( types[0]) : Collections.emptyList());
        setViewId( DEFAULT_VIEW);
        this.user = user;
        if ( user != null && !user.isAdmin()) {
        	boolean selected = m_facade.getSystemPreferences().getEntryAsBoolean( CalendarModel.ONLY_MY_EVENTS_DEFAULT, true);
        	optionMap.put( CalendarModel.ONLY_MY_EVENTS, selected ? "true" : "false");
        }
        optionMap.put( CalendarModel.SAVE_SELECTED_DATE, "false");
        resetExports();
    }
    
    public void resetExports() 
    {
        setTitle(null);
        setOption( CalendarModel.SHOW_NAVIGATION_ENTRY, "true");
        setOption(HTML_EXPORT_ENABLED, "false");
        setOption(ICAL_EXPORT_ENABLED, "false");                       
    }
    
    public boolean isMatchingSelectionAndFilter( Appointment appointment) throws RaplaException
    {
    	Reservation reservation = appointment.getReservation();
    	if ( reservation == null)
    	{
    		return false;
    	}
		return isMatchingSelectionAndFilter(reservation, appointment);
    }

    public boolean isMatchingSelectionAndFilter(Reservation reservation, Appointment appointment) throws RaplaException
    {
        Allocatable[] allocatables = appointment == null ? reservation.getAllocatables() : reservation.getAllocatablesFor(appointment);
		HashSet<RaplaObject> hashSet = new HashSet<RaplaObject>( Arrays.asList(allocatables));
		hashSet.add( reservation.getClassification().getType());
		final User owner = reservation.getOwner();
		if ( owner != null)
		{
		    hashSet.add( owner);
		}
		Collection<RaplaObject> selectedObjectsAndChildren = getSelectedObjectsAndChildren();
		hashSet.retainAll( selectedObjectsAndChildren);
		boolean matchesEventObjects =  hashSet.size() != 0 || selectedObjectsAndChildren.size() == 0;
		if ( !matchesEventObjects)
		{
			return false;
		}
		
		Classification classification = reservation.getClassification();
		if ( isDefaultEventTypes())
		{
			return true;
		}
		
		ClassificationFilter[] reservationFilter = getReservationFilter();
		for ( ClassificationFilter filter:reservationFilter)
		{
			if (filter.matches(classification))
			{
				return true;
			}
		}
		return false;
    }
    

    public boolean setConfiguration(CalendarModelConfiguration config, final Map<String,String> alternativOptions) throws RaplaException {
    	ArrayList<RaplaObject> selectedObjects = new ArrayList<RaplaObject>();
        allocatableFilter.clear();
        reservationFilter.clear();
        if ( config == null)
        {
            defaultEventTypes = true;
            defaultResourceTypes = true;
            DynamicType type =null;
            {
            	DynamicType[] dynamicTypes = m_facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
            	if ( dynamicTypes.length > 0)
            	{
            		type = dynamicTypes[0];
            	}
            }
            if ( type == null)
            {
            	DynamicType[] dynamicTypes = m_facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
            	if ( dynamicTypes.length > 0)
            	{
            		type = dynamicTypes[0];
            	}
            }
            if ( type != null)
            {
            	setSelectedObjects( Collections.singletonList(type));
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
        optionMap = new TreeMap<String,String>();
      //  viewOptionMap = new TreeMap<String,String>();
        if ( config.getOptionMap() != null)
        {
            Map<String,String> configOptions = config.getOptionMap();
            addOptions(configOptions);
        }
        if (alternativOptions != null )
        {
            addOptions(alternativOptions);
        } 
        final String saveDate = optionMap.get( CalendarModel.SAVE_SELECTED_DATE);
        if ( config.getSelectedDate() != null && (saveDate == null || saveDate.equals("true"))) {
            setSelectedDate( config.getSelectedDate() );
        }
        else
        {
            setSelectedDate( m_facade.today());
        }
        if ( config.getStartDate() != null) {
            setStartDate( config.getStartDate() );
        }
        else
        {
            setStartDate( m_facade.today());
        }
        if ( config.getEndDate() != null && (saveDate == null || saveDate.equals("true"))) {
            setEndDate( config.getEndDate() );
        }
        else
        {
            setEndDate(  DateTools.addYear(getStartDate()));
        }
        selectedObjects.addAll( config.getSelected());
        if ( config.isResourceRootSelected())
        {
        	selectedObjects.add( ALLOCATABLES_ROOT);
        }
        
        Set<User> selectedUsers = getSelected(User.TYPE);
        User currentUser = getUser();
        if (currentUser != null &&  selectedUsers.size() == 1 && selectedUsers.iterator().next().equals( currentUser))
        {
        	if ( getOption( CalendarModel.ONLY_MY_EVENTS) == null && currentUser.isAdmin())
        	{
        	    boolean selected = m_facade.getSystemPreferences().getEntryAsBoolean( CalendarModel.ONLY_MY_EVENTS_DEFAULT, true);
                setOption( CalendarModel.ONLY_MY_EVENTS, selected ? "true" : "false");
                selectedObjects.remove( currentUser);
        	}
        }
        
        setSelectedObjects( selectedObjects );
        for ( ClassificationFilter f:config.getFilter())
        {
            final DynamicType type = f.getType();
            final String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            boolean eventType = annotation != null &&annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            Map<DynamicType,ClassificationFilter> map = eventType ? reservationFilter : allocatableFilter;
            map.put(type, f);
        }
        return couldResolveAllEntities;
    }

    protected void addOptions(Map<String,String> configOptions) {
        for (Map.Entry<String, String> entry:configOptions.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();
            optionMap.put( key, value);
        }
    }

    public User getUser() {
        return user;
    }

    public CalendarModelConfigurationImpl createConfiguration() throws RaplaException {
        ClassificationFilter[] allocatableFilter = isDefaultResourceTypes() ? null : getAllocatableFilter();
        ClassificationFilter[] eventFilter = isDefaultEventTypes() ? null : getReservationFilter();
        return createConfiguration(allocatableFilter, eventFilter);
    }
    
    CalendarModelConfigurationImpl beforeTemplateConf;
    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
    	Collection<RaplaObject> selectedObjects = getSelectedObjects();
    	if ( evt == null)
    	{
    		return;
    	}
    	boolean switchTemplate =  ((UpdateResult)evt).isSwitchTemplateMode();
    	if  (switchTemplate)
    	{
    	    boolean changeToTemplate= m_facade.getTemplate() != null;
    	    if (changeToTemplate)
    	    {
    	        beforeTemplateConf = createConfiguration();
    	        setSelectedObjects(Collections.emptyList());
    	        setAllocatableFilter( null );
    	        setReservationFilter( null );
    	    }
    	    else if ( beforeTemplateConf != null)
    	    {
    	        setConfiguration( beforeTemplateConf, null);
    	        beforeTemplateConf = null;
    	    }
    	    
    	}
    	{
	    	Collection<RaplaObject> newSelection = new ArrayList<RaplaObject>();
	    	boolean changed = false;
	    	for ( RaplaObject obj: selectedObjects)
	    	{
	    		if ( obj instanceof Entity)
	    		{
		    		if (!evt.isRemoved((Entity) obj))
		    		{
		    			newSelection.add( obj);
		    		}
		    		else
		    		{
		    			changed = true;
		    		}
		    	}
	    	}
	    	if ( changed)
	    	{
	    		setSelectedObjects( newSelection);
	    	}
    	}
    	{
			if (evt.isModified( DynamicType.TYPE) || evt.isModified( Category.TYPE) || evt.isModified( User.TYPE))
			{
				CalendarModelConfigurationImpl config = (CalendarModelConfigurationImpl)createConfiguration();
				updateConfig(evt, config);
				if  ( beforeTemplateConf != null)
				{
				    updateConfig(evt, beforeTemplateConf);
				}
				setConfiguration( config, null);
			}
    	}
    }

    public void updateConfig(ModificationEvent evt, CalendarModelConfigurationImpl config) throws CannotExistWithoutTypeException {
        User user2 = getUser();
        if ( user2 != null && evt.isModified( user2))
        {
        	Set<User> changed = RaplaType.retainObjects(evt.getChanged(), Collections.singleton(user2));
        	if ( changed.size() > 0)
        	{
        		User newUser = changed.iterator().next();
        		user = newUser;
        	}
        }
        for ( RaplaObject obj:evt.getChanged())
        {
        	if ( obj.getRaplaType() == DynamicType.TYPE)
        	{
        		DynamicType type = (DynamicType) obj;
        		if ( config.needsChange(type))
        		{
        			config.commitChange( type);
        		}
        	}
        }
        for ( RaplaObject obj:evt.getRemoved())
        {
        	if ( obj.getRaplaType() == DynamicType.TYPE)
        	{
        		DynamicType type = (DynamicType) obj;
        		config.commitRemove( type);
        	}
        }
    }
    
    private CalendarModelConfigurationImpl createConfiguration(ClassificationFilter[] allocatableFilter, ClassificationFilter[] eventFilter) throws RaplaException {
        String viewName = selectedView;
        Set<Entity> selected = new HashSet<Entity>( );
        
        Collection<RaplaObject> selectedObjects = getSelectedObjects();
		for (RaplaObject object:selectedObjects) {
			if ( !(object instanceof Conflict) && (object instanceof Entity)) 
            {
				//  throw new RaplaException("Storing the conflict view is not possible with Rapla.");
				selected.add( (Entity) object );
            }
        }

        final Date selectedDate = getSelectedDate();
        final Date startDate = getStartDate();
        final Date endDate = getEndDate();
		boolean resourceRootSelected = selectedObjects.contains( ALLOCATABLES_ROOT);
		return newRaplaCalendarModel( selected,resourceRootSelected, allocatableFilter,eventFilter, title, startDate, endDate, selectedDate, viewName, optionMap);
    }
    
    public CalendarModelConfigurationImpl newRaplaCalendarModel(Collection<Entity> selected,
    		boolean resourceRootSelected,
            ClassificationFilter[] allocatableFilter,
            ClassificationFilter[] eventFilter, String title, Date startDate,
            Date endDate, Date selectedDate, String view, Map<String,String> optionMap) throws RaplaException
    {
        boolean defaultResourceTypes;
        boolean defaultEventTypes;

        int eventTypes = 0;
        int resourceTypes = 0;
        defaultResourceTypes = true;
        defaultEventTypes = true;
        List<ClassificationFilter> filter = new ArrayList<ClassificationFilter>();
        if (allocatableFilter != null) {
            for (ClassificationFilter entry : allocatableFilter) {
                ClassificationFilter clone = entry.clone();
                filter.add(clone);
                resourceTypes++;
                if (entry.ruleSize() > 0) {
                    defaultResourceTypes = false;
                }
            }
        }
        if (eventFilter != null) {
            for (ClassificationFilter entry : eventFilter) {
                ClassificationFilter clone = entry.clone();
                filter.add(clone);
                eventTypes++;
                if (entry.ruleSize() > 0) {
                    defaultEventTypes = false;
                }
            }
        }

        DynamicType[] allEventTypes;
        allEventTypes = m_facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        if (allEventTypes.length > eventTypes && eventFilter != null) {
            defaultEventTypes = false;
        }
        final DynamicType[] allTypes = m_facade.getDynamicTypes(null);
        final int allResourceTypes = allTypes.length - allEventTypes.length;
        if (allResourceTypes > resourceTypes && allocatableFilter != null) {
            defaultResourceTypes = false;
        }
    
        final ClassificationFilter[] filterArray = filter.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
        List<String> selectedIds = new ArrayList<String>();
        Collection<RaplaType> idTypeList = new ArrayList<RaplaType>();
        for (Entity obj:selected)
        {
            RaplaType raplaType = obj.getRaplaType();
            if (CalendarModelConfigurationImpl.canReference( raplaType))
            {
                selectedIds.add( obj.getId());
                idTypeList.add( raplaType);
            }
        }
        
		CalendarModelConfigurationImpl calendarModelConfigurationImpl = new CalendarModelConfigurationImpl(selectedIds, idTypeList,resourceRootSelected,filterArray, defaultResourceTypes, defaultEventTypes, title, startDate, endDate, selectedDate, view, optionMap);
        calendarModelConfigurationImpl.setResolver( m_facade.getOperator());
		return calendarModelConfigurationImpl;
    }


    public void setReservationFilter(ClassificationFilter[] array) {
    	reservationFilter.clear();
    	if ( array == null)
    	{
    		defaultEventTypes = true;
    		return;
    	}
        try {
            defaultEventTypes = createConfiguration(null,array).isDefaultEventTypes();
        } catch (RaplaException e) {
            // DO Not set the types
        }
        for (ClassificationFilter entry: array)
        {   
            final DynamicType type = entry.getType();
            reservationFilter.put( type, entry);
        }
    }

    public void setAllocatableFilter(ClassificationFilter[] array) {
        allocatableFilter.clear();
      	if ( array == null)
    	{
    		defaultResourceTypes = true;
    		return;
    	}
        try {
            defaultResourceTypes = createConfiguration(array,null).isDefaultResourceTypes();
        } catch (RaplaException e) {
            // DO Not set the types
        }
        for (ClassificationFilter entry: array)
        {   
            final DynamicType type = entry.getType();
            allocatableFilter.put( type, entry);
        }
    }
   
    @Override
    public Date getSelectedDate() {
        return selectedDate;
    }

    @Override
    public void setSelectedDate(Date date) {
        if ( date == null)
            throw new IllegalStateException("Date can't be null");
        if ( selectedDate != null && !date.equals( selectedDate))
        {
            Collection<TimeInterval> empty = Collections.emptyList();
            setMarkedIntervals( empty, false);
        }
        this.selectedDate = date;
        
    }

    @Override
    public Date getStartDate() {
        return startDate;
    }

    @Override
    public void setStartDate(Date date) {
        if ( date == null)
            throw new IllegalStateException("Date can't be null");
        this.startDate = date;
    }

    @Override
    public Date getEndDate() {
        return endDate;
    }

    @Override
    public void setEndDate(Date date) {
        if ( date == null)
            throw new IllegalStateException("Date can't be null");
        this.endDate = date;
    }

    @Override
    public String getTitle() 
    {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public void setViewId(String view) {
        this.selectedView = view;
    }

    @Override
    public String getViewId() {
        return this.selectedView;
    }

    class CalendarModelParseContext implements ParseContext
    {
		public Function resolveVariableFunction(String variableName) throws IllegalAnnotationException {
			if ( variableName.equals("allocatables"))
			{
				List<Function> emptyList = Collections.emptyList();
				return new Function(variableName, emptyList)
				{
					@Override
					public Object eval(EvalContext context) {
						try {
							return Arrays.asList(getSelectedAllocatables());
						} catch (RaplaException e) {
							return Collections.emptyList();
						}
					}
					
				};
			}
			else if ( variableName.equals("timeIntervall"))
			{
				List<Function> emptyList = Collections.emptyList();
				return new Function(variableName, emptyList)
				{
					@Override
					public Object eval(EvalContext context) {
						return getTimeIntervall();
					}
					
				};
			}
			else if ( variableName.equals("selectedDate"))
			{
				List<Function> emptyList = Collections.emptyList();
				return new Function(variableName, emptyList)
				{
					@Override
					public Object eval(EvalContext context) {
						return getSelectedDate();
					}
					
				};
			}
			return null;
		}
    	
    }

    public TimeInterval getTimeIntervall()
    {
    	return new TimeInterval(getStartDate(), getEndDate());
    }

    @Override
    public String getNonEmptyTitle() {
        String title = getTitle();
        if (title != null && title.trim().length()>0)
        {        
        	ParseContext parseContext = new CalendarModelParseContext();
			ParsedText parsedTitle;
			try {
				parsedTitle = new ParsedText( title);
				parsedTitle.init(parseContext);
			} catch (IllegalAnnotationException e) {
				return e.getMessage();
			}
        	EvalContext evalContext = new EvalContext( locale);
			String result = parsedTitle.formatName( evalContext);
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
                        types = getI18n().format("allocation_view",getName( obj ),dateString);
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

    public String getName(Object object) {
        if (object == null)
            return "";
        if (object instanceof Named) {
            String name = ((Named) object).getName(locale);
            return (name != null) ? name : "";
        }
        return object.toString();
    }

    private Collection<Allocatable> getFilteredAllocatables() throws RaplaException {
        Collection<Allocatable> list = new LinkedHashSet<Allocatable>();
        // TODO should be replaced with getAllocatables(allocatableFilter.values();
        for ( Allocatable allocatable :m_facade.getAllocatables())
        {
            if ( isInFilterAndCanRead(allocatable)) {
                list.add( allocatable);
            }
        }
        return list;
    }

    private boolean isInFilterAndCanRead(Allocatable allocatable)
    {
        return isInFilter( allocatable) && (user == null || allocatable.canRead(user));
    }
    
    private boolean isInFilter( Allocatable classifiable) {
//        if (isTemplateModus())
//        {
//            return true;
//        }
        final Classification classification = classifiable.getClassification();
        final DynamicType type = classification.getType();
        final ClassificationFilter classificationFilter = allocatableFilter.get( type);
        if ( classificationFilter != null)
        {
            final boolean matches = classificationFilter.matches(classification);
            return matches;
        }
        else
        {
            return defaultResourceTypes;
        }
    }

    @Override
    public Collection<RaplaObject> getSelectedObjectsAndChildren() throws RaplaException
    {
    	Assert.notNull(selectedObjects);

        ArrayList<DynamicType> dynamicTypes = new ArrayList<DynamicType>();
        for (Iterator<RaplaObject> it = selectedObjects.iterator();it.hasNext();)
        {
            Object obj = it.next();
            if (obj instanceof DynamicType) {
                dynamicTypes.add ((DynamicType)obj);
            }
        }

        HashSet<RaplaObject> result = new HashSet<RaplaObject>();
        result.addAll( selectedObjects );
        
        boolean allAllocatablesSelected = selectedObjects.contains( CalendarModelImpl.ALLOCATABLES_ROOT);
        
        if ( dynamicTypes.size() > 0 || allAllocatablesSelected)
        {
            Collection<Allocatable> filteredList = getFilteredAllocatables();
            for (Allocatable oneSelectedItem:filteredList)
            {
            	if ( selectedObjects.contains(oneSelectedItem)) {
                    continue;
                }
                Classification classification = oneSelectedItem.getClassification();
                if ( classification == null)
                {
                	continue;
                }
                if ( allAllocatablesSelected || dynamicTypes.contains(classification.getType()))
                {
                    result.add( oneSelectedItem );
                    continue;
                }
            }
        }

        return result;
    }

    @Override
    public void setSelectedObjects(Collection<? extends Object> selectedObjects)  {
        this.selectedObjects = retainRaplaObjects(selectedObjects);
        if (markedAllocatables != null && !markedAllocatables.isEmpty())
        {
        	markedAllocatables = new  LinkedHashSet<Allocatable>(markedAllocatables);
        	try {
				markedAllocatables.retainAll( Arrays.asList(getSelectedAllocatables()));
			} catch (RaplaException e) {
				markedAllocatables = Collections.emptyList();
			}
        }
    }

    private List<RaplaObject> retainRaplaObjects(Collection<? extends Object> list ){
        List<RaplaObject> result = new ArrayList<RaplaObject>();
        for ( Iterator<? extends Object> it = list.iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof RaplaObject) {
                result.add( (RaplaObject)obj );
            }
        }
        return result;
    }

    @Override
    public Collection<RaplaObject> getSelectedObjects()
    {
        return selectedObjects;
    }

    @Override
    public ClassificationFilter[] getReservationFilter() throws RaplaException 
    {
        Collection<ClassificationFilter> filter ;
        if ( isDefaultEventTypes() /*|| isTemplateModus()*/)
        {
            filter = new ArrayList<ClassificationFilter>();
            for (DynamicType type :m_facade.getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION))
            {
                filter.add( type.newClassificationFilter());
            }
        }
        else
        {
            filter = reservationFilter.values();
        }
        return filter.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
    }

    protected boolean isTemplateModus() {
        return m_facade.getTemplate() != null;
    }

    @Override
    public ClassificationFilter[] getAllocatableFilter() throws RaplaException {
        Collection<ClassificationFilter> filter ;
        if ( isDefaultResourceTypes() /*|| isTemplateModus()*/)
        {
            filter = new ArrayList<ClassificationFilter>();
            for (DynamicType type :m_facade.getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
            {
                filter.add( type.newClassificationFilter());
            }
            for (DynamicType type :m_facade.getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON))
            {
                filter.add( type.newClassificationFilter());
            }
            
        }
        else
        {
            filter = allocatableFilter.values();
        }
        return filter.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
    }

    public CalendarSelectionModel clone()  {
        CalendarModelImpl clone;
        try
        {
            clone = new CalendarModelImpl(locale, user, m_facade);
            CalendarModelConfiguration config = createConfiguration();
            Map<String, String> alternativOptions = null;
			clone.setConfiguration( config, alternativOptions);
        }
        catch ( RaplaException e )
        {
            throw new IllegalStateException( e.getMessage() );
        }
        return clone;
    }

    @Override
    public Reservation[] getReservations() throws RaplaException {
        return getReservations( getStartDate(), getEndDate() );
    }

    @Override
    public Reservation[] getReservations(Date startDate, Date endDate) throws RaplaException 
    {
        return getReservationsAsList( startDate, endDate ).toArray( Reservation.RESERVATION_ARRAY);
    }

    private List<Reservation> getReservationsAsList(Date start, Date end) throws RaplaException 
    {
    	Allocatable[] allocatables = getSelectedAllocatables();
    	if ( isNoAllocatableSelected())
    	{
    		allocatables = null;
    	}
		Collection<Conflict> conflicts = getSelectedConflicts();
		if ( conflicts.size() > 0)
		{
			return m_facade.getReservations(conflicts);
		}
    	
    	Reservation[] reservationArray =m_facade.getReservations(allocatables, start, end);
    	List<Reservation> asList = Arrays.asList( reservationArray );
		return restrictReservations(asList);
    }
    
	public List<Reservation> restrictReservations(Collection<Reservation> reservationsToRestrict)		throws RaplaException {
		
		List<Reservation> reservations = new ArrayList<Reservation>(reservationsToRestrict);
		// Don't restrict templates
//		if ( isTemplateModus())
//		{
//			return reservations;
//		}
		ClassificationFilter[] reservationFilter = getReservationFilter();
    	if ( isDefaultEventTypes())
    	{
    		reservationFilter = null;
    	}
    	Set<User> users = getUserRestrictions();
	    for ( Iterator<Reservation> it = reservations.iterator();it.hasNext();) 
	    {
            Reservation event = it.next();
            if ( !users.isEmpty()  && !users.contains( event.getOwner() )) {
                it.remove();
            }
            else if (reservationFilter != null && !ClassificationFilter.Util.matches( reservationFilter,event))
            {
                it.remove();
            }
        }
        return reservations;
	}
    
	private Set<User> getUserRestrictions() {
    	User currentUser = getUser();
    	if (  currentUser != null &&  isOnlyCurrentUserSelected() ) 
    	{
    		return Collections.singleton( currentUser );
    	}
    	else if ( currentUser != null && currentUser.isAdmin())
    	{
    		return getSelected(User.TYPE);
    	}
    	else
    	{
    		return Collections.emptySet();
    	}
	}
    
	private boolean isNoAllocatableSelected() 
    {
		for (RaplaObject obj :getSelectedObjects())
		{
			RaplaType raplaType = obj.getRaplaType();
			if ( raplaType == Allocatable.TYPE)
			{
				return false;
			}
			else if (raplaType == DynamicType.TYPE)
			{
				DynamicType type = (DynamicType) obj;
				String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
				if ( annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON ) || annotation.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
				{
					return false;
				}
			}
			else if ( obj.equals(ALLOCATABLES_ROOT) )
			{
				return  false;
			}
		}
    	return true;
	}

	@Override
    public Allocatable[] getSelectedAllocatables() throws RaplaException {
        Collection<Allocatable> result = getSelectedAllocatablesAsList();
         return result.toArray(Allocatable.ALLOCATABLE_ARRAY);
   }

	protected Collection<Allocatable> getSelectedAllocatablesAsList()
			throws RaplaException {
        
		Collection<Allocatable> result = new HashSet<Allocatable>();
        Collection<RaplaObject> selectedObjectsAndChildren = getSelectedObjectsAndChildren();
        boolean conflictsDetected = false;
        for(RaplaObject object:selectedObjectsAndChildren) {
            Allocatable alloc = null;
            if ( object.getRaplaType() ==  Conflict.TYPE ) {
                if ( !conflictsDetected)
                {
                    // We ignore the allocatable selection if there are conflicts selected
                    result.clear();
                    conflictsDetected = true;
                }
                alloc = ((Conflict)object).getAllocatable();
                
                
            }
            if ( !conflictsDetected && object.getRaplaType() ==Allocatable.TYPE ) {
                alloc = (Allocatable)object ;
            }
            if ( alloc != null && isInFilterAndCanRead( alloc))
            {
                result.add( alloc );
            }
        }
        return result;
	}

    public Collection<Conflict> getSelectedConflicts()  {
        return getSelected(Conflict.TYPE);
   }

    public Set<DynamicType> getSelectedTypes(String classificationType) throws RaplaException {
        Set<DynamicType> result = new HashSet<DynamicType>();
        Iterator<RaplaObject> it = getSelectedObjectsAndChildren().iterator();
        while (it.hasNext()) {
            RaplaObject object  =  it.next();
            if ( object.getRaplaType() == DynamicType.TYPE ) {
                if (classificationType == null || (( DynamicType) object).getAnnotation( DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE).equals( classificationType))
                {
                    result.add((DynamicType) object  );
                }
            }
        }
         return result;
   }
    
    private <T extends RaplaObject<T>> Set<T> getSelected(RaplaType<T> type)  {
        Set<T> result = new HashSet<T>();
        Iterator<RaplaObject> it = getSelectedObjects().iterator();
        while (it.hasNext()) {
            RaplaObject object  =  it.next();
            if ( object.getRaplaType() ==  type ) {
                @SuppressWarnings("unchecked")
				T casted = (T)object;
				result.add( casted  );
            }
        }
        return result;
   }

    @Override
    public boolean isOnlyCurrentUserSelected() {
    	String option = getOption(CalendarModel.ONLY_MY_EVENTS );
    	if ( option != null && option.equalsIgnoreCase("TRUE"))
    	{
    		return true;
    	}
    	return false;
    }

    @Override
    public void selectUser(User user) {
        List<RaplaObject> selectedObjects = new ArrayList<RaplaObject>(getSelectedObjects()); 
    	for (Iterator<RaplaObject> it = selectedObjects.iterator();it.hasNext();) {
            RaplaObject obj = it.next();
            if (obj.getRaplaType() ==  User.TYPE ) {
                it.remove();
            }
        }
        if ( user != null)
        {
            selectedObjects.add( user );
        }
        setSelectedObjects(selectedObjects);
    }

    @Override
    public String getOption( String name )
    {
        return optionMap.get(  name );
    }

    @Override
    public void setOption( String name, String string )
    {
        if ( string == null)
        {
            optionMap.remove( name);
        }
        else
        {
            optionMap.put( name, string);
        }
    }
    
    @Override
    public boolean isDefaultEventTypes() 
    {
        return defaultEventTypes;
    }

    @Override
    public boolean isDefaultResourceTypes() 
    {
        return defaultResourceTypes;
    }

    @Override
    public void save(final String filename) throws RaplaException,
            EntityNotFoundException {
        Preferences clone = createStorablePreferences(filename);
        m_facade.store(clone);
    }

    public Preferences createStorablePreferences(final String filename) throws RaplaException, EntityNotFoundException {
        final CalendarModelConfiguration conf = createConfiguration();
        
        Preferences clone = m_facade.edit(m_facade.getPreferences(user));

        if ( filename == null)
        {
            clone.putEntry( CalendarModelConfiguration.CONFIG_ENTRY, conf);
        }
        else
        {
            Map<String,CalendarModelConfiguration> exportMap= clone.getEntry(EXPORT_ENTRY);
            Map<String,CalendarModelConfiguration> newMap;
            if ( exportMap == null)
                newMap = new TreeMap<String,CalendarModelConfiguration>();
            else
                newMap = new TreeMap<String,CalendarModelConfiguration>( exportMap);
            newMap.put(filename, conf);
            clone.putEntry( EXPORT_ENTRY, m_facade.newRaplaMap( newMap ));
        }
        return clone;
    }
    
    // Old defaultname behaviour. Duplication of language resource names. But the system has to be replaced anyway in the future, because it doesnt allow for multiple language outputs on the server.
    private boolean isOldDefaultNameBehavoir(final String filename) 
	{
		List<String> translations = new ArrayList<String>();
		translations.add( "default" );
		translations.add( "Default" );
	    translations.add( "Standard" );
		translations.add( "Standaard");
		// special for polnish
		if (filename.startsWith( "Domy") && filename.endsWith("lne"))
		{
			return true;
		}
		if (filename.startsWith( "D") && filename.endsWith("faut"))
		{
		    return true;
		}
		
		if (filename.startsWith( "Est") && filename.endsWith("ndar"))
		{
			return true;
		}
		return translations.contains(filename);
	}

    @Override
    public void load(final String filename)  throws RaplaException, EntityNotFoundException, CalendarNotFoundExeption {
        final CalendarModelConfiguration modelConfig;
        boolean createIfNotNull =false;
		
		{
			final Preferences preferences = m_facade.getPreferences(user, createIfNotNull);
			modelConfig = getModelConfig(filename, preferences);
	    }
        if ( modelConfig == null && filename != null )
        {
            throw new CalendarNotFoundExeption("Calendar with name " +  filename + " not found.");
        }
        else
        {
        	final boolean isDefault = filename == null ;
            Map<String,String> alternativeOptions = new HashMap<String,String>();
            if (modelConfig != null && modelConfig.getOptionMap() != null)
            {
                // All old default calendars have no selected date
                if (isDefault && (modelConfig.getOptionMap().get( CalendarModel.SAVE_SELECTED_DATE) == null))
                {
                    alternativeOptions.put(CalendarModel.SAVE_SELECTED_DATE , "false");
                }
                // All old calendars are exported
                if ( !isDefault && modelConfig.getOptionMap().get(HTML_EXPORT_ENABLED) == null)
                {
                    alternativeOptions.put(HTML_EXPORT_ENABLED,"true");
                }
            }
            setConfiguration(modelConfig, alternativeOptions);
        }
    }

    public CalendarModelConfiguration getModelConfig(final String filename,final Preferences preferences) {
		final CalendarModelConfiguration modelConfig;
		if (preferences != null)
		{
		    final boolean isDefault = filename == null ;
		    if ( isDefault )
		    {
		        modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
		    }
		    else if ( filename != null && !isDefault)
		    {
		        Map<String,CalendarModelConfiguration> exportMap= preferences.getEntry(EXPORT_ENTRY);
		        final CalendarModelConfiguration config;
		        if ( exportMap != null)
		        {
		        	config = exportMap.get(filename);
		        }
		        else
		        {
		        	config = null;
		        }
		        if ( config == null && isOldDefaultNameBehavoir(filename) )
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
//	if ( selectedConflicts != null)
//	{
//		for (Conflict conflict: selectedConflicts)
//		{
//			if ( conflict.getAppointment1().equals( app.getId()))
//			{
//				conflictList.add(conflict.getAppointment2());
//			}
//			else if ( conflict.getAppointment2().equals( app.getId()))
//			{
//				conflictList.add(conflict.getAppointment1());
//			}
//		}
//	}

    @Override
    public List<AppointmentBlock> getBlocks() throws RaplaException 
	{
		List<AppointmentBlock> appointments = new ArrayList<AppointmentBlock>();
		Set<Allocatable> selectedAllocatables = new HashSet<Allocatable>(Arrays.asList(getSelectedAllocatables()));
		if ( isNoAllocatableSelected())
    	{
    		selectedAllocatables = null;
    	}
		Collection<Conflict> selectedConflicts = getSelectedConflicts();
		List<Reservation> reservations = m_facade.getReservations( selectedConflicts);
		Map<Appointment,Set<Appointment>> conflictingAppointments = ConflictImpl.getMap(selectedConflicts,reservations);
		for ( Reservation event:getReservations())
        {
        	for (Appointment  app: event.getAppointments())
        	{
//        		
        		Allocatable[] allocatablesFor = event.getAllocatablesFor(app);
        		if ( selectedAllocatables == null || containsOne(selectedAllocatables, allocatablesFor))
        		{
        			Collection<Appointment> conflictList = conflictingAppointments.get( app ); 
        			if ( conflictList == null || conflictList.isEmpty())
        			{
            			app.createBlocks(getStartDate(), getEndDate(), appointments);
        			}
        			else
        			{
        				List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        				app.createBlocks(getStartDate(), getEndDate(), blocks);
        				Iterator<AppointmentBlock> it = blocks.iterator();
        				while ( it.hasNext())
        				{
        					AppointmentBlock block = it.next();
    						boolean found = false;
        					for ( Appointment conflictingApp:conflictList)
        					{
        						if (conflictingApp.overlaps( block ))
        						{	
        							found = true;
        							break;
        						}
        					}
        					if ( !found)
        					{
        						it.remove();
        					}
        				}
        				appointments.addAll( blocks);
        			}
        		}
        	}
        }
        Collections.sort(appointments);
        return appointments;
	}

	private boolean containsOne(Set<Allocatable> allocatableSet,
			Allocatable[] listOfAllocatablesToMatch) {
		for ( Allocatable alloc: listOfAllocatablesToMatch)
		{
			if (allocatableSet.contains(alloc))
			{
				return true;
			}
		}
		return false;
	}

	@Override
    public DynamicType guessNewEventType() throws RaplaException  {
		Set<DynamicType> selectedTypes = getSelectedTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
		DynamicType guessedType;
        if (selectedTypes.size()>0)
        {
        	guessedType = selectedTypes.iterator().next();
        }
        else
        {
        	guessedType = m_facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        }
		ClassificationFilter[] reservationFilter = getReservationFilter();
        DynamicType firstType = null;
        boolean found = false;
        // assure that the guessed type is in the filter selection list
        for (ClassificationFilter filter : reservationFilter)
        {
        	DynamicType type = filter.getType();
			if ( firstType == null)
			{
				firstType = type;
			}
        	if ( type.equals( guessedType))
        	{
        		found = true;
        		break;
        	}
        }
        if  (!found  && firstType != null)
        {
        	guessedType = firstType;
        }
        return guessedType;
	}
	@Override
	public Collection<TimeInterval> getMarkedIntervals() 
	{
		return timeIntervals;
	}

	@Override
	public void setMarkedIntervals(Collection<TimeInterval> timeIntervals, boolean timeEnabled)
	{
		if ( timeIntervals != null)
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

	@Override
    public void markInterval(Date start, Date end) {
		TimeInterval timeInterval = new TimeInterval( start, end);
		setMarkedIntervals( Collections.singletonList( timeInterval), false);
	}

	@Override
    public Collection<Allocatable> getMarkedAllocatables() {
		return markedAllocatables;
	}

	@Override
	public void setMarkedAllocatables(Collection<Allocatable> allocatables) {
		this.markedAllocatables = allocatables;
	}
	
	@Override
	public boolean isMarkedIntervalTimeEnabled() 
	{
	    return markedIntervalTimeEnabled;
	}

	public Collection<Appointment> getAppointments(TimeInterval interval) throws RaplaException 
	{
		Date startDate = interval.getStart();
		Date endDate = interval.getEnd();
		List<Reservation> reservations = getReservationsAsList(startDate, endDate);
		Collection<Allocatable> allocatables =getSelectedAllocatablesAsList();
		List<Appointment> result = AppointmentImpl.getAppointments(reservations, allocatables);
		return result;
	}

	
}


