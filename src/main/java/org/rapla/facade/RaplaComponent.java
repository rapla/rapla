/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
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
package org.rapla.facade;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.*;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.*;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.*;
import org.rapla.framework.logger.Logger;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
    Base class for most components. Eases
    access to frequently used services, e.g. {@link I18nBundle}.
 */
public class RaplaComponent 
{
	public static final TypedComponentRole<RaplaConfiguration> PLUGIN_CONFIG= new TypedComponentRole<RaplaConfiguration>("org.rapla.plugin");
	//private final ClientServiceManager serviceManager;
    private TypedComponentRole<I18nBundle> childBundleName;
    private Logger logger;
    RaplaLocale raplaLocale;
    RaplaResources i18n;
    ClientFacade facade;
    private RaplaContext context;
    
    protected RaplaComponent(RaplaContext context) 
    {
        this.context = context;
        try
        {
            this.facade = context.lookup(ClientFacade.class);
        }
        catch (RaplaContextException ex)
        {
            serviceExcption(ClientFacade.class, ex);
        }
        try
        {
            this.i18n =context.lookup(RaplaResources.class);
        }
        catch (RaplaContextException ex)
        {
            serviceExcption(RaplaResources.class.getName(), ex);
        }
        try
        {
            this.raplaLocale = context.lookup( RaplaLocale.class);
        }
        catch (RaplaContextException ex)
        {
            serviceExcption(RaplaLocale.class, ex);
        }
        try{
            logger = context.lookup(Logger.class);
        }
        catch (RaplaContextException ex)
        {
            serviceExcption(Logger.class, ex);
        }
    }
    
    public RaplaComponent(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
//        try {
//            logger = context.lookupDeprecated(Logger.class );
//        } catch (RaplaContextException e) {
//            logger = new ConsoleLogger();
//        }
        this.facade = facade;
        this.i18n = i18n;
        this.logger = logger;
        this.raplaLocale = raplaLocale;
    }
    
    public EntityResolver getEntityResolver() 
    {
        return getClientFacade().getOperator();
    }
    
    protected void setLogger(Logger logger) 
    {
    	this.logger = logger;
	}


    /** returns if the session user is admin */
    final public boolean isAdmin() {
        try {
            return getUser().isAdmin();
        } catch (RaplaException ex) {
        }
        return false;
    }

    /** returns if the session user is a registerer */
    final public boolean isRegisterer(DynamicType type) {
        if (isAdmin())
        {
            return true;
        }
        User user;
        try {
            //Category registererGroup = getQuery().getUserGroupsCategory().getCategory(Permission.GROUP_REGISTERER_KEY);
            user = getUser();
            if ( type == null)
            {
                for (DynamicType type1: getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON))
                {
                    if (PermissionContainer.Util.canCreate( type1, user))
                    {
                        return true;
                    }
                }
                for (DynamicType type1: getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
                {
                    if (PermissionContainer.Util.canCreate( type1, user))
                    {
                        return true;
                    }
                }
                return false;
            }
            else
            {
                boolean result = PermissionContainer.Util.canCreate( type, user);
                return result;
            }
        } catch (RaplaException ex) {
            return false;
        }

    }
    
    /** @deprecated please request {@link AppointmentFormater} with {@link Inject}*/
    @Deprecated
    public AppointmentFormater getAppointmentFormater()
    {
        return getService( AppointmentFormater.class);
    }

    final public boolean isModifyPreferencesAllowed() {
        try {
            User user = getUser();
            return isModifyPreferencesAllowed( user);
        } catch (RaplaException ex) {
        }
        return false;
    }
    
    @SuppressWarnings("deprecation")
    final public boolean isModifyPreferencesAllowed(User user) 
    {
        return isAllowed(user, Permission.GROUP_MODIFY_PREFERENCES_KEY, Permission.GROUP_CAN_CREATE_EVENTS);
    }
    
    @SuppressWarnings("deprecation")
    final public boolean isTemplateEditAllowed(User user) 
    {
        return isAllowed(user, Permission.GROUP_CAN_EDIT_TEMPLATES, Permission.GROUP_CAN_CREATE_EVENTS);
    }

    private boolean isAllowed(User user, String group, String alternativeGroup) {
        if (user.isAdmin())
        {
            return true;
        }
        try {
            Category userGroupsCategory = getQuery().getUserGroupsCategory();
            if ( userGroupsCategory == null)
            {
                return false;
            }
            
            Category firstGroup = userGroupsCategory.getCategory(group);
            if ( firstGroup == null ) {
                Category secondGroup = userGroupsCategory.getCategory(alternativeGroup);
                if ( secondGroup == null)
                {
                    return true;
                }
                return user.belongsTo(secondGroup); 
            }
            return user.belongsTo(firstGroup);
        } catch (RaplaException ex) {
        }
        return false;
    }

    /** returns if the user has allocation rights for one or more resource */
    final public boolean canUserAllocateSomething(User user) throws RaplaException {
        Allocatable[] allocatables =getQuery().getAllocatables();
        if ( user.isAdmin() )
            return true;
        if (!canCreateReservation(user))
        {
        	return false;
        }
        for ( Allocatable a:allocatables) {
            if (PermissionContainer.Util.hasPermissionToAllocate(user, a))
            {
                return true;
            }
        }
        return false;
    }
    
    final public boolean canCreateReservation(User user) 
    {
        try {
            for (DynamicType type1: getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION))
            {
                if (PermissionContainer.Util.canCreate( type1, user))
                {
                    return true;
                }
            }
        } catch (RaplaException e) {
            return false;
        }
		return false;
    }
    
    
    final public boolean canCreateReservation()  {
    	try {
            User user = getUser();
            return canCreateReservation( user);
        } catch (RaplaException ex) {
            return false;
        }
    }

    protected boolean canAllocate() 
	{
		CalendarSelectionModel model = getService( CalendarSelectionModel.class);
    	
		//Date start, Date end,
		Collection<Allocatable> allocatables = getService(CalendarSelectionModel.class).getMarkedAllocatables();
		boolean canAllocate = true;
		Date start = getStartDate( model); 
		Date end = getEndDate( model, start); 
        for (Allocatable allo:allocatables)
        {
        	if (!canAllocate( start, end, allo))
        	{
        		canAllocate = false;
        	}
        }
		return canAllocate;
	}
    
    protected Date getStartDate(CalendarModel model) {
		Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
		Date startDate = null;
    	if ( markedIntervals.size() > 0)
    	{
    		TimeInterval first = markedIntervals.iterator().next();
    		startDate = first.getStart();
    	}
    	if ( startDate != null)
    	{
    		return startDate;
    	}
    	
		Date selectedDate = model.getSelectedDate();
        if ( selectedDate == null)
        {
            selectedDate = model.getStartDate();
        }
		if ( selectedDate == null)
		{
			selectedDate = getQuery().today();
		}
		Date time = new Date (DateTools.MILLISECONDS_PER_MINUTE * getCalendarOptions().getWorktimeStartMinutes());
		startDate = getRaplaLocale().toDate(selectedDate,time);
		return startDate;
	}
	
	 protected Date getEndDate( CalendarModel model,Date startDate) {
		Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
		Date endDate = null;
    	if ( markedIntervals.size() > 0)
    	{
    		TimeInterval first = markedIntervals.iterator().next();
    		endDate = first.getEnd();
    	}
    	if ( endDate != null)
    	{
    		return endDate;
    	}
		return new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
	}
    
    protected boolean canAllocate(Date start, Date end, Allocatable allocatables) {
        if ( allocatables == null) {
            return true;
        }
        try {
            User user = getUser();
			Date today = getQuery().today();
			return PermissionContainer.Util.canAllocate(allocatables, user, start, end, today );
        } catch (RaplaException ex) {
            return false;
        }
    }
    
    /** returns if the current user is allowed to modify the object. */
    final public boolean canModify(Object object) {
        try {
            User user = getUser();
            EntityResolver entityResolver = getEntityResolver();
            return canModify(object, user, entityResolver);
        } catch (RaplaException ex) {
            return false;
        }
    }
    
    final public boolean canRead(User user, Reservation reservation) {
        EntityResolver entityResolver = getEntityResolver();
        return canRead(reservation, user, entityResolver);
    }
    
    static public boolean canModify(Conflict conflict,User user, EntityResolver resolver) {
        Allocatable allocatable = conflict.getAllocatable();
        if (user == null || user.isAdmin())
        {
            return true;
        }
        if (PermissionContainer.Util.canRead(allocatable, user))
        {
            if (canModifyEvent(conflict.getReservation1(),user, resolver))
            {
                return true;
            }
            if (canModifyEvent(conflict.getReservation2(),user, resolver))
            {
                return true;
            }
        }
        return false;
    }

    static public boolean canModifyEvent(String reservationId,User user, EntityResolver resolver) 
    {
        Reservation reservation =  resolver.tryResolve(reservationId, Reservation.class);;
        boolean canModify = reservation != null && canModify(reservation, user, resolver);
        return canModify;
    }

    final public boolean canAdmin(Object object) {
        try {
            User user = getUser();
            EntityResolver entityResolver = getEntityResolver();
            return canAdmin(object, user , entityResolver);
        } catch (RaplaException ex) {
            return false;
        }
    }

    static public boolean canModify(Object object, User user, EntityResolver resolver) {
        if (object == null || !(object instanceof RaplaObject))
        {
            return false;
        }
        if ( user == null)
        {
            return false;
        }
        if (user.isAdmin())
            return true;
        if (object instanceof Ownable) {
            Ownable ownable = (Ownable) object;
            User owner = ownable.getOwner();
			if  ( owner != null && user.equals(owner))
            {
                return true;
            }
			if ( owner == null && object instanceof Classifiable)
			{
			    if (PermissionContainer.Util.canCreate( (Classifiable)object, user))
			    {
			        return true;
			    }
			}
        }
        if (object instanceof PermissionContainer) {
            PermissionContainer permissionContainer = (PermissionContainer) object;
            if (PermissionContainer.Util.canModify(permissionContainer, user))
            {
                return true;
            }
        }
        if ( object instanceof Annotatable && canWriteTemplate((Annotatable) object, user, resolver))
        {
            return true;
        }
        return false;
    }
    
    public static boolean canWriteTemplate(Annotatable entity, User user, EntityResolver resolver) {
        String templateId = ((Annotatable)entity).getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, null);
        if ( templateId != null)
        {
            Allocatable template = resolver.tryResolve( templateId, Allocatable.class);
            if ( template != null)
            {
                if (canModify( template, user, resolver));
                {
                    return true;
                }
            }
        }
        return false;
    }
    
    public static boolean canReadTemplate(Annotatable entity, User user, EntityResolver resolver) {
        String templateId = ((Annotatable)entity).getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, null);
        if ( templateId != null)
        {
            Allocatable template = resolver.tryResolve( templateId, Allocatable.class);
            if ( template != null)
            {
                if (canRead( template, user, resolver));
                {
                    return true;
                }
            }
        }
        return false;
    }

    static public boolean canAdmin(Object object, User user, EntityResolver resolver) {
        if ( !canModify(object, user,resolver))
        {
            return false;
        }
        if (user.isAdmin())
            return true;
        if (object instanceof Ownable) {
            Ownable ownable = (Ownable) object;
            User owner = ownable.getOwner();
            if  ( owner != null && user.equals(owner))
            {
                return true;
            }
            if ( owner == null && object instanceof Allocatable)
            {
                if (PermissionContainer.Util.canCreate( (Allocatable)object, user))
                {
                    return true;
                }
            }
        }
        if (object instanceof PermissionContainer) {
            PermissionContainer permissionContainer = (PermissionContainer) object;
            if (PermissionContainer.Util.canAdmin(permissionContainer, user))
            {
                return true;
            }
        }
        if ( object instanceof Annotatable && canWriteTemplate((Annotatable)object, user, resolver))
        {
            return true;
        }
        return false;
    }
    
    static public boolean canRead(Appointment appointment,User user, EntityResolver resolver)
    {
    	Reservation  reservation = appointment.getReservation();
    	boolean result = canRead(reservation, user,resolver);
		return result;
    }
    
    static public boolean canRead(Reservation reservation,User user, EntityResolver resolver)
    {
    	if ( user == null)
    	{
    		return true;
    	}
    	if ( canModify(reservation, user,resolver))
        {
     	   return true;
        }
    	if ( canReadTemplate(reservation, user, resolver))
    	{
    	    return true;
    	}
	    if (PermissionContainer.Util.canRead(reservation, user))
    	{
    		return true;
    	}
    	return false;
    }
    
    static public boolean canRead(Allocatable allocatable, User user, EntityResolver resolver) {
       if ( canModify(allocatable, user, resolver))
       {
    	   return true;
       }
       if (PermissionContainer.Util.canRead(allocatable, user))
       {
           return true;
       }
       return false;
    }

    /** We check if an attribute with the permission_modify exists and look if the permission is set either globally (if boolean type is used) or for a specific user group (if category type is used)*/
//	public static boolean checkClassifiableModifyPermissions(PermissionContainer object,	User user) {
//	    return checkClassifiablePermissions(object, user, AttributeAnnotations.KEY_PERMISSION_MODIFY, false);
//	}
//	
//    
// 	static Category dummyCategory = new CategoryImpl(new Date(), new Date());
//    // The dummy category is used if no permission attribute is found. Use permissionNotFoundReturns to set whether this means permssion granted or not
//    private static boolean checkClassifiablePermissions(Object object, User user, String permissionKey, boolean permssionNotFoundReturnsYesCategory) {
//    	Collection<Category> cat = getPermissionGroups(object, dummyCategory,permissionKey, permssionNotFoundReturnsYesCategory);
//    	if ( cat == null)
//    	{
//    		return false;
//    	}
//    	if ( cat.size() == 1 && cat.iterator().next() == dummyCategory)
//    	{
//    		return true;
//    	}
//    	for (Category c: cat)
//    	{
//    		if (user.belongsTo( c) )
//    		{
//    			return true;
//    		}
//    	}
//    	return false;
//    }
//    
//    /** returns the group that is has the requested permission on the object
//     **/
//    public static Collection<Category> getPermissionGroups(Object object, Category yesCategory, String permissionKey, boolean permssionNotFoundReturnsYesCategory) 
//	{
//    	if (object instanceof Classifiable ) {
//            final Classifiable classifiable = (Classifiable) object;
//            
//            Classification classification = classifiable.getClassification();
//            if ( classification != null)
//            {
//                final DynamicTypeImpl type = (DynamicTypeImpl) classification.getType();
//                final Attribute attribute = type.getFirstAttributeWithAnnotation(permissionKey);
//                if ( attribute != null)
//                {
//                    return getPermissionGroups(classification, yesCategory, attribute);
//                }
//                else
//                {
//                	if ( permssionNotFoundReturnsYesCategory  && yesCategory != null)
//                	{
//                		return Collections.singleton(yesCategory);
//                	}
//                	else
//                	{
//                		return null;
//                	}
//                	
//                }
//            }
//        }
//        return null;
//	}
//
//	private static Collection<Category> getPermissionGroups(Classification classification,
//			Category yesCategory, final Attribute attribute) {
//		final AttributeType type2 = attribute.getType();
//		if (type2 == AttributeType.BOOLEAN)
//		{
//		    final Object value = classification.getValue( attribute);
//		    if  (Boolean.TRUE.equals(value))
//		    {
//		    	return Collections.singleton(yesCategory);
//		    }
//		    else
//		    {
//		    	return null;
//		    }
//		}
//		if ( type2 == AttributeType.CATEGORY)
//		{
//			 Collection<?> values = classification.getValues( attribute);
//			 @SuppressWarnings("unchecked")
//			Collection<Category> cat = (Collection<Category>) values;
//             if ( cat == null || cat.size() == 0)
//             {
//                 Category rootCat = (Category)attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
//                 if ( rootCat.getCategories().length == 0)
//                 {
//                     cat = Collections.singleton(rootCat);
//                 }
//             }
//		    return cat;
//		}
//		return null;
//	}


    public CalendarOptions getCalendarOptions() {
    	User user;
    	try
    	{
    		user = getUser();
    	} 
    	catch (RaplaException ex) {
    		// Use system settings if an error occurs
    		user = null;
        }
    	return getCalendarOptions( user);
    }
    
    protected CalendarOptions getCalendarOptions(final User user) {
        return getCalendarOptions(user, getClientFacade());
    }

    static public CalendarOptions getCalendarOptions(final User user, final ClientFacade clientFacade) {
        RaplaConfiguration conf = null;
        try {
            // check if user has calendar options
            if ( user != null)
            {
                conf = clientFacade.getPreferences( user ).getEntry(CalendarOptionsImpl.CALENDAR_OPTIONS);
            }
            // check if system has calendar options
            if ( conf == null)
            {
                conf = clientFacade.getPreferences( null ).getEntry(CalendarOptionsImpl.CALENDAR_OPTIONS);
            }
            if ( conf != null)
            {
                return new CalendarOptionsImpl( conf );
            }
            
        } catch (RaplaException ex) {

        }
        try {
            return new CalendarOptionsImpl( new RaplaConfiguration() );
        } catch (RaplaException e) {
            throw new IllegalStateException( e);
        }
    }

    protected User getUser() throws RaplaException {
    	return getUserModule().getUser();
    }

    protected Logger getLogger() {
        return logger;
    }

    /** lookupDeprecated the service in the serviceManager under the specified key:
        serviceManager.lookupDeprecated(role).
        @throws IllegalStateException if GUIComponent wasn't serviced. No service method called
        @throws UnsupportedOperationException if service not available.
     */
	protected <T> T getService(Class<T> role) {
        try {
            return getContext().lookup( role);
        } catch (RaplaContextException e) {
             throw serviceExcption(role, e); 
        }
    }

    protected UnsupportedOperationException serviceExcption(Object role, RaplaContextException e) {
        return new UnsupportedOperationException("Service not supported in this context: " + role, e);
    }
//   
    protected <T> T getService(TypedComponentRole<T> role) {
        try {
            return getContext().lookup(role);
        } catch (RaplaContextException e) {
            throw serviceExcption(role, e); 
        }
    }
    
    @Deprecated
    public RaplaContext getContext() {
        return context;
    }

    /** lookupDeprecated RaplaLocale from the context */
    protected RaplaLocale getRaplaLocale() {
        return raplaLocale;
//        if (serviceManager.raplaLocale == null)
//            serviceManager.raplaLocale = getService(RaplaLocale.class);
//        return serviceManager.raplaLocale;
    }


    public Locale getLocale() {
        return getRaplaLocale().getLocale();
    }

    /** lookupDeprecated I18nBundle from the serviceManager */
    protected RaplaResources getI18n() {
        return i18n;
    }

//    private I18nBundle getI18nDefault() {
//        if (serviceManager.i18n == null)
//            serviceManager.i18n = getService(RaplaComponent.RAPLA_RESOURCES);
//        return serviceManager.i18n;
//    }
//
    /** lookupDeprecated AppointmentFormater from the serviceManager */
//    protected AppointmentFormater getAppointmentFormater() {
//        if (serviceManager.appointmentFormater == null)
//            serviceManager.appointmentFormater = getService(AppointmentFormater.class);
//        return serviceManager.appointmentFormater;
//    }

    /** lookupDeprecated PeriodModel from the serviceManager */
    protected PeriodModel getPeriodModel() {
    	try {
    		return getQuery().getPeriodModel();
    	} catch (RaplaException ex) {
    		throw new UnsupportedOperationException("Service not supported in this context: " );
    	}
    }

    /** lookupDeprecated QueryModule from the serviceManager */
    protected QueryModule getQuery() {
        return getClientFacade();
    }

    final protected ClientFacade getClientFacade() {
        return facade;
    }

    /** lookupDeprecated ModificationModule from the serviceManager */
    protected ModificationModule getModification() {
        return getClientFacade();
    }

    /** lookupDeprecated UpdateModule from the serviceManager */
    protected UpdateModule getUpdateModule() {
        return getClientFacade();
    }

    /** lookupDeprecated UserModule from the serviceManager */
   protected UserModule getUserModule() {
        return getClientFacade();
    }

    /** returns a translation for the object name into the selected language. If
     a translation into the selected language is not possible an english translation will be tried next.
     If theres no translation for the default language, the first available translation will be used. */
    public String getName(Object object) {
        if (object == null)
            return "";
        if (object instanceof Named) {
            String name = ((Named) object).getName(getI18n().getLocale());
            return (name != null) ? name : "";
        }
        return object.toString();
    }

    /** calls getI18n().getString(key) */
    final public String getString(@PropertyKey(resourceBundle = RaplaResources.BUNDLENAME)String key) {
        return getI18n().getString(key);
    }


    private static class ClientServiceManager  {
        I18nBundle i18n;
        ClientFacade facade;
        RaplaLocale raplaLocale;
        AppointmentFormater appointmentFormater;
    }

    final public Preferences newEditablePreferences() throws RaplaException {
        Preferences preferences = getQuery().getPreferences();
		ModificationModule modification = getModification();
		return  modification.edit(preferences);
    }
    
	public static boolean isTemplate(RaplaObject<?> obj) 
	{
		if ( obj instanceof Appointment)
		{
			obj = ((Appointment) obj).getReservation();
		}
		if ( obj instanceof Annotatable)
		{
			String template = ((Annotatable)obj).getAnnotation( RaplaObjectAnnotations.KEY_TEMPLATE);
			return template != null;
		}
		return false;
	}
	
	public static void unlock(Lock lock)
	{
		if ( lock != null)
		{
			lock.unlock();
		}
	}

	public static Lock lock(Lock lock, int seconds) throws RaplaException {
		try
		{
			if ( lock.tryLock())
			{
				return lock;
			}
			if (lock.tryLock(seconds, TimeUnit.SECONDS))
			{
				return lock;
			}
			else
			{
				throw new RaplaSynchronizationException("Someone is currently writing. Please try again! Can't acquire lock " + lock );
			}
		}
		catch (InterruptedException ex)
		{
			throw new RaplaSynchronizationException( ex);
		}
	}


	
}
