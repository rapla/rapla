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
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.StorageOperator;

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

    public RaplaComponent(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        this.facade = facade;
        this.i18n = i18n;
        this.logger = logger;
        this.raplaLocale = raplaLocale;
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
                conf = clientFacade.getPreferences( user, true ).getEntry(CalendarOptionsImpl.CALENDAR_OPTIONS);
            }
            // check if system has calendar options
            if ( conf == null)
            {
                conf = clientFacade.getPreferences( null, true ).getEntry(CalendarOptionsImpl.CALENDAR_OPTIONS);
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

	protected Date getStartDate(CalendarModel model)
	{
	    final ClientFacade clientFacade = getClientFacade();
        return getStartDate(model, clientFacade, clientFacade.getUser());
	}
    
    public static Date getStartDate(CalendarModel model, ClientFacade clientFacade, User user) {
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
            selectedDate = clientFacade.today();
        }
        final CalendarOptions calendarOptions = RaplaComponent.getCalendarOptions(user, clientFacade);
        Date time = new Date (DateTools.MILLISECONDS_PER_MINUTE * calendarOptions.getWorktimeStartMinutes());
        startDate = DateTools.toDateTime(selectedDate,time);
        return startDate;
    }
    
    protected Date getEndDate(CalendarModel model, Date startDate)
    {
        return calcEndDate(model, startDate);
    }

    public static Date calcEndDate(CalendarModel model, Date startDate)
    {
        Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
        Date endDate = null;
        if (markedIntervals.size() > 0)
        {
            TimeInterval first = markedIntervals.iterator().next();
            endDate = first.getEnd();
        }
        if (endDate != null)
        {
            return endDate;
        }
        return new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);

    }
}
