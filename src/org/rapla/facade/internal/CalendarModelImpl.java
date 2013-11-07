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
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentBlockStartComparator;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ParsedText;
import org.rapla.entities.dynamictype.internal.ParsedText.EvalContext;
import org.rapla.entities.dynamictype.internal.ParsedText.Function;
import org.rapla.entities.dynamictype.internal.ParsedText.ParseContext;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.Conflict.Util;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;

public class CalendarModelImpl implements CalendarSelectionModel
{
    private static final String DEFAULT_VIEW = "week";//WeekViewFactory.WEEK_VIEW;
    public static final TypedComponentRole<RaplaMap<CalendarModelConfiguration>> EXPORT_ENTRY = new TypedComponentRole<RaplaMap<CalendarModelConfiguration>>("org.rapla.plugin.autoexport");
	private static final String ICAL_EXPORT_ENABLED = "org.rapla.plugin.export2ical"+ ".selected";
	private static final String HTML_EXPORT_ENABLED = EXPORT_ENTRY + ".selected";
	Date startDate;
    Date endDate;
    Date selectedDate;
    List<RaplaObject> selectedObjects = new ArrayList<RaplaObject>();
    String title;
    ClientFacade m_facade;
    String selectedView;
    I18nBundle i18n;
    RaplaContext context;
    RaplaLocale raplaLocale;
    User user;
    Map<String,String> optionMap = new HashMap<String,String>();
    //Map<String,String> viewOptionMap = new HashMap<String,String>();

    boolean defaultEventTypes = true;
    boolean defaultResourceTypes = true;
    Collection<TimeInterval> timeIntervals = Collections.emptyList();
    Collection<Allocatable> markedAllocatables = Collections.emptyList();
    
    Map<DynamicType,ClassificationFilter> reservationFilter = new LinkedHashMap<DynamicType, ClassificationFilter>();
    Map<DynamicType,ClassificationFilter> allocatableFilter = new LinkedHashMap<DynamicType, ClassificationFilter>();
    public static final RaplaConfiguration ALLOCATABLES_ROOT = new RaplaConfiguration("rootnode", "allocatables");

    public CalendarModelImpl(RaplaContext context, User user, ClientFacade facade) throws RaplaException {
        this.context = context;
        this.raplaLocale =context.lookup(RaplaLocale.class);
        i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);
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
        setSelectedObjects( Collections.singletonList( types[0]) );
        setViewId( DEFAULT_VIEW);
        this.user = user;
        if ( user != null && !user.isAdmin()) {
        	boolean selected = m_facade.getPreferences(null).getEntryAsBoolean( CalendarModel.ONLY_MY_EVENTS_DEFAULT, true);
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


    private boolean setConfiguration(CalendarModelConfiguration config, final Map<String,String> alternativOptions) throws RaplaException {
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
            RaplaMap<String> configOptions = config.getOptionMap();
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
        
        Set<User> selectedUsers = getSelected(User.TYPE);
        User currentUser = getUser();
        if (currentUser != null &&  selectedUsers.size() == 1 && selectedUsers.iterator().next().equals( currentUser))
        {
        	if ( getOption( CalendarModel.ONLY_MY_EVENTS) == null)
        	{
        		setOption( CalendarModel.ONLY_MY_EVENTS, "true");
            	selectedObjects.remove( currentUser);
        	}
        }
        
        setSelectedObjects( selectedObjects );
        ClassificationFilter[] filter = config.getFilter();
        for ( ClassificationFilter f:filter)
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

    public CalendarModelConfiguration createConfiguration() throws RaplaException {
        ClassificationFilter[] allocatableFilter = isDefaultResourceTypes() ? null : getAllocatableFilter();
        ClassificationFilter[] eventFilter = isDefaultEventTypes() ? null : getReservationFilter();
        return createConfiguration(allocatableFilter, eventFilter);
    }
    
    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
    	Collection<RaplaObject> selectedObjects = getSelectedObjects();
    	if ( evt == null)
    	{
    		return;
    	}
    	Set<RaplaObject> removed = evt.getRemoved();
    	if ( removed != null)
    	{
	    	Collection<RaplaObject> newSelection = new ArrayList<RaplaObject>();
	    	boolean changed = false;
	    	for ( RaplaObject obj: selectedObjects)
	    	{
	    		if ( !removed.contains(obj))
	    		{
	    			newSelection.add( obj);
	    		}
	    		else
	    		{
	    			changed = true;
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
						if ( config.needsChange(type))
						{
							config.commitRemove( type);
						}
					}
				}
						
				setConfiguration( config, null);
			}
    	}
    }
    
    private CalendarModelConfiguration createConfiguration(ClassificationFilter[] allocatableFilter, ClassificationFilter[] eventFilter) throws RaplaException {
        String viewName = selectedView;
        Set<RaplaObject> selected = new HashSet<RaplaObject>( );
        
        Collection<RaplaObject> selectedObjects = getSelectedObjects();
		for (RaplaObject object:selectedObjects) {
			if ( !(object instanceof Conflict)) 
            {
				//  throw new RaplaException("Storing the conflict view is not possible with Rapla.");
				selected.add( object );
            }
        }

        final Date selectedDate = getSelectedDate();
        final Date startDate = getStartDate();
        final Date endDate = getEndDate();
        RaplaMap<RaplaObject> selectedMap = m_facade.newRaplaMap(selected);
        RaplaMap<String> optionMap_ = m_facade.newRaplaMap(optionMap);
        return newRaplaCalendarModel( selectedMap, allocatableFilter,eventFilter, title, startDate, endDate, selectedDate, viewName, optionMap_);
    }
    
    public CalendarModelConfiguration newRaplaCalendarModel(RaplaMap<? extends RaplaObject> selected,
            ClassificationFilter[] allocatableFilter,
            ClassificationFilter[] eventFilter, String title, Date startDate,
            Date endDate, Date selectedDate, String view, RaplaMap<String> optionMap) throws RaplaException
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
        return new CalendarModelConfigurationImpl(selected, filterArray, defaultResourceTypes, defaultEventTypes, title, startDate, endDate, selectedDate, view, optionMap);
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
   
    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getSelectedDate()
	 */
    public Date getSelectedDate() {
        return selectedDate;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#setSelectedDate(java.util.Date)
	 */
    public void setSelectedDate(Date date) {
        if ( date == null)
            throw new IllegalStateException("Date can't be null");
        this.selectedDate = date;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getStartDate()
	 */
    public Date getStartDate() {
        return startDate;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#setStartDate(java.util.Date)
	 */
    public void setStartDate(Date date) {
        if ( date == null)
            throw new IllegalStateException("Date can't be null");
        this.startDate = date;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getEndDate()
	 */
    public Date getEndDate() {
        return endDate;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#setEndDate(java.util.Date)
	 */
    public void setEndDate(Date date) {
        if ( date == null)
            throw new IllegalStateException("Date can't be null");
        this.endDate = date;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getTitle()
	 */
    public String getTitle() 
    {
        return title;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#setTitle(java.lang.String)
	 */
    public void setTitle(String title) {
        this.title = title;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#setView(java.lang.String)
	 */
    public void setViewId(String view) {
        this.selectedView = view;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getView()
	 */
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
    
    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getNonEmptyTitle()
	 */
    public String getNonEmptyTitle() {
        String title = getTitle();
        if (title != null && title.trim().length()>0)
        {        
        	ParseContext parseContext = new CalendarModelParseContext();
			ParsedText parsedTitle;
			try {
				parsedTitle = new ParsedText( title, parseContext);
			} catch (IllegalAnnotationException e) {
				return e.getMessage();
			}
        	Locale locale = raplaLocale.getLocale();
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
            String name = ((Named) object).getName(getI18n().getLocale());
            return (name != null) ? name : "";
        }
        return object.toString();
    }

    private Collection<Allocatable> getFilteredAllocatables() throws RaplaException {
        List<Allocatable> list = new ArrayList<Allocatable>();
        for ( Allocatable allocatable :m_facade.getAllocatables())
        {
            if ( isInFilter( allocatable) && (user == null || allocatable.canRead(user))) {
                list.add( allocatable);
            }
        }
        return list;
    }
    
    

    private boolean isInFilter( Allocatable classifiable) {
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
    
    public Collection<RaplaObject> getSelectedObjectsAndChildren() throws RaplaException
    {
    	if ( m_facade.getTemplateName() != null )
        {
    		return Collections.emptyList();
        }
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
        
        Collection<Allocatable> filteredList = getFilteredAllocatables();
        for (Iterator<Allocatable> it = filteredList.iterator();it.hasNext();)
        {
        	Allocatable oneSelectedItem =  it.next();
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

        return result;
    }


    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#setSelectedObjects(java.util.List)
	 */
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



    public Collection<RaplaObject> getSelectedObjects()
    {
    	if ( m_facade.getTemplateName() != null )
        {
    		return Collections.emptyList();
        }	
        return selectedObjects;
    }

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getReservationFilter()
	 */
    public ClassificationFilter[] getReservationFilter() throws RaplaException 
    {
        Collection<ClassificationFilter> filter ;
        if ( isDefaultEventTypes())
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

    /* (non-Javadoc)
     * @see org.rapla.calendarview.CalendarModel#getAllocatableFilter()
     */
    public ClassificationFilter[] getAllocatableFilter() throws RaplaException {
        Collection<ClassificationFilter> filter ;
        if ( isDefaultResourceTypes())
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
            clone = new CalendarModelImpl(context, user, m_facade);
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

    /* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getReservations(java.util.Date, java.util.Date)
	 */
    public Reservation[] getReservations() throws RaplaException {
        return getReservations( getStartDate(), getEndDate() );
    }

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
			return Util.getReservations(conflicts);
		}
    	
    	Reservation[] reservationArray =m_facade.getReservations(allocatables, start, end);
    	List<Reservation> asList = Arrays.asList( reservationArray );
		return restrictReservations(asList);
    }

	public List<Reservation> restrictReservations(Collection<Reservation> reservationsToRestrict)
			throws RaplaException {
		
		List<Reservation> reservations = new ArrayList<Reservation>(reservationsToRestrict);
		// Don't restrict templates
		if ( m_facade.getTemplateName() != null)
		{
			return reservations;
		}
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
    	if (  currentUser != null &&  isOnlyCurrentUserSelected() || !m_facade.canReadReservationsFromOthers( currentUser)) 
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

	/* (non-Javadoc)
	 * @see org.rapla.calendarview.CalendarModel#getAllocatables()
	 */
    public Allocatable[] getSelectedAllocatables() throws RaplaException {
        Collection<Allocatable> result = new HashSet<Allocatable>();
        for(RaplaObject object:getSelectedObjectsAndChildren()) {
            if ( object.getRaplaType() ==  Conflict.TYPE ) {
                result.add( ((Conflict)object).getAllocatable() );
            }
        }
        // We ignore the allocatable selection if there are conflicts selected
        if ( result.isEmpty())
        {
            for(RaplaObject object:getSelectedObjectsAndChildren()) {
                if ( object.getRaplaType() ==Allocatable.TYPE ) {
                    result.add( (Allocatable)object  );
                }
            }
        }
        Collection<Allocatable> filteredAllocatables = getFilteredAllocatables();
        result.retainAll( filteredAllocatables);
         return result.toArray(Allocatable.ALLOCATABLE_ARRAY);
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

    protected I18nBundle getI18n() {
        return i18n;
    }

    protected RaplaLocale getRaplaLocale() {
        return raplaLocale;
    }

    public boolean isOnlyCurrentUserSelected() {
    	String option = getOption(CalendarModel.ONLY_MY_EVENTS );
    	if ( option != null && option.equalsIgnoreCase("TRUE"))
    	{
    		return true;
    	}
    	return false;
    }

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

    public String getOption( String name )
    {
        return optionMap.get(  name );
    }


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
    
//    public void setViewOption( String name, String string )
//    {
//        if ( string == null)
//        {
//            viewOptionMap.remove( name);
//        }
//        else
//        {
//            viewOptionMap.put( name, string);
//        }
//    }
//    
//    public String getViewOption(String name)
//    {
//        return viewOptionMap.get(name);
//    }

    public boolean isDefaultEventTypes() 
    {
        return defaultEventTypes;
    }

    public boolean isDefaultResourceTypes() 
    {
        return defaultResourceTypes;
    }

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
		translations.add( getI18n().getString("default") );
		translations.add( "default" );
		translations.add( "Default" );
		translations.add( "Standard" );
		translations.add( "Standaard");
		// special for polnish
		if (filename.startsWith( "Domy") && filename.endsWith("lne"))
		{
			return true;
		}
		if (filename.startsWith( "Est") && filename.endsWith("ndar"))
		{
			return true;
		}
		return translations.contains(filename);
	}

    public void load(final String filename)  throws RaplaException, EntityNotFoundException, CalendarNotFoundExeption {
        final CalendarModelConfiguration modelConfig;
        final Preferences preferences = m_facade.getPreferences(user);
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
        if ( modelConfig == null && filename != null && !isDefault)
        {
            throw new CalendarNotFoundExeption("Calendar with name " +  filename + " not found.");
        }
        else
        {
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

	public List<AppointmentBlock> getBlocks() throws RaplaException 
	{
		List<AppointmentBlock> appointments = new ArrayList<AppointmentBlock>();
		Set<Allocatable> selectedAllocatables = new HashSet<Allocatable>(Arrays.asList(getSelectedAllocatables()));
		if ( isNoAllocatableSelected())
    	{
    		selectedAllocatables = null;
    	}
		Collection<Conflict> selectedConflicts = getSelectedConflicts();
		for ( Reservation event:getReservations())
        {
        	for (Appointment  app: event.getAppointments())
        	{
        		Set<Appointment> conflictList = new HashSet<Appointment>();
        		if ( selectedConflicts != null)
        		{
        			for (Conflict conflict: selectedConflicts)
        			{
        				if ( conflict.getAppointment1().equals( app))
        				{
        					conflictList.add(conflict.getAppointment2());
        				}
        				else if ( conflict.getAppointment2().equals( app))
        				{
        					conflictList.add(conflict.getAppointment1());
        				}
        			}
        		}
        		Allocatable[] allocatablesFor = event.getAllocatablesFor(app);
        		if ( selectedAllocatables == null || containsOne(selectedAllocatables, allocatablesFor))
        		{
        			if ( conflictList.isEmpty())
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
        Collections.sort(appointments, new AppointmentBlockStartComparator());
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

	public Collection<TimeInterval> getMarkedIntervals() 
	{
		return timeIntervals;
	}
	
	public void setMarkedIntervals(Collection<TimeInterval> timeIntervals)
	{
		if ( timeIntervals != null)
		{
			this.timeIntervals = Collections.unmodifiableCollection(timeIntervals);
		}
		else
		{
			this.timeIntervals = Collections.emptyList();
		}
	}

	
	public void markInterval(Date start, Date end) {
		TimeInterval timeInterval = new TimeInterval( start, end);
		setMarkedIntervals( Collections.singletonList( timeInterval));
	}

	public Collection<Allocatable> getMarkedAllocatables() {
		return markedAllocatables;
	}

	public void setMarkedAllocatables(Collection<Allocatable> allocatables) {
		this.markedAllocatables = allocatables;
	}

	
}


