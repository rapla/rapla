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
import org.rapla.components.i18n.I18nBundle;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
    Base class for most components. Eases
    access to frequently used services, e.g. {@link I18nBundle}.
 */
public class RaplaComponent
{
	public static final TypedComponentRole<RaplaConfiguration> PLUGIN_CONFIG= new TypedComponentRole<>("org.rapla.plugin");
    private Logger logger;
    RaplaLocale raplaLocale;
    protected RaplaResources i18n;
    RaplaFacade facade;

    public RaplaComponent(RaplaFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        this.facade = facade;
        this.i18n = i18n;
        this.logger = logger;
        this.raplaLocale = raplaLocale;
    }

    public static List<Reservation> addAllocatables(CalendarModel model, Collection<Reservation> newReservations, User user) throws RaplaException
    {
        Collection<Allocatable> markedAllocatables = model.getMarkedAllocatables();
        if (markedAllocatables == null || markedAllocatables.size() == 0)
        {
            Collection<Allocatable> allocatables = model.getSelectedAllocatablesAsList();
            if (allocatables.size() == 1)
            {
                addAlloctables(newReservations, allocatables);
            }
        }
        else
        {
            Collection<Allocatable> allocatables = markedAllocatables;
            addAlloctables(newReservations, allocatables);
        }
        List<Reservation> list = new ArrayList<>();
        for (Reservation reservation : newReservations)
        {
            Reservation cast = reservation;
            ReferenceInfo<User> lastChangedBy = cast.getLastChangedBy();
            if (lastChangedBy != null && !lastChangedBy.equals(user.getReference()))
            {
                throw new RaplaException("Reservation " + cast + " has wrong user " + lastChangedBy);
            }
            list.add(cast);
        }
        return list;
    }

    static private void addAlloctables(Collection<Reservation> events, Collection<Allocatable> allocatables)
    {
        for (Reservation event : events)
        {
            for (Allocatable alloc : allocatables)
            {
                event.addAllocatable(alloc);
            }
        }
    }

    public static Promise<Reservation> newReservation(DynamicType type, User user, RaplaFacade raplaFacade, CalendarSelectionModel model)
    {
        return raplaFacade.newReservationAsync(type.newClassification()).thenCombine(
                raplaFacade.newAppointmentAsync(getMarkedInterval(model, raplaFacade, user)),
                (r, a) -> {
                    r.addAppointment(a);
                    return r;
                });
    }

    protected void setLogger(Logger logger) 
    {
    	this.logger = logger;
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

    
    protected CalendarOptions getCalendarOptions(final User user) {
        return getCalendarOptions(user, getFacade());
    }

    static public CalendarOptions getCalendarOptions(final User user, final RaplaFacade raplaFacade) {
        RaplaConfiguration conf = null;
        try {
            // check if user has calendar options
            if ( user != null)
            {
                conf = raplaFacade.getPreferences( user).getEntry(CalendarOptionsImpl.CALENDAR_OPTIONS);
            }
            // check if system has calendar options
            if ( conf == null)
            {
                conf = raplaFacade.getPreferences( null).getEntry(CalendarOptionsImpl.CALENDAR_OPTIONS);
            }
            if ( conf != null)
            {
                return new CalendarOptionsImpl( conf );
            }

        } catch (RaplaException ex) {

        }
        try {
            return new CalendarOptionsImpl( new RaplaConfiguration() );
        } catch (RaplaInitializationException e) {
            throw new IllegalStateException( e);
        }
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
    		return facade.getPeriodModel();
    	} catch (RaplaException ex) {
    		throw new UnsupportedOperationException("Service not supported in this context: " );
    	}
    }

    /** lookupDeprecated QueryModule from the serviceManager */
    protected RaplaFacade getQuery() {
        return getFacade();
    }

    final protected RaplaFacade getFacade() {
        return facade;
    }


    /** returns a translation for the object name into the selected language. If
     a translation into the selected language is not possible an english translation will be tried next.
     If theres no translation for the default language, the first available translation will be used. */
    public String getName(Object object) {
        if (object == null)
            return "";
        if (object instanceof Named) {
            final Locale locale = getI18n().getLocale();
            String name = ((Named) object).getName(locale);
            return (name != null) ? name : "";
        }
        return object.toString();
    }

    static public String getName(Object object, Locale locale) {
        if (object == null)
            return "";
        if (object instanceof Named) {
            String name = ((Named) object).getName(locale);
            return (name != null) ? name : "";
        }
        return object.toString();
    }

    /** calls getI18n().getString(key) */
    final public String getString(@PropertyKey(resourceBundle = RaplaResources.BUNDLENAME)String key) {
        return getI18n().getString(key);
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

    public static Date getStartDate(CalendarModel model, RaplaFacade raplaFacade, User user) {

        return getMarkedInterval( model,raplaFacade, user).getStart();
    }

    public static TimeInterval getMarkedInterval(CalendarModel model,RaplaFacade facade,User user)
    {
        Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
        if ( markedIntervals.size() > 0)
        {
            TimeInterval first = markedIntervals.iterator().next();
            return new TimeInterval( first.getStart(), first.getEnd());
        }
        Date selectedDate = model.getSelectedDate();
        if ( selectedDate == null)
        {
            selectedDate = model.getStartDate();
        }
        if ( selectedDate == null)
        {
            selectedDate = facade.today();
        }
        final CalendarOptions calendarOptions = RaplaComponent.getCalendarOptions(user, facade);
        Date time = new Date (DateTools.MILLISECONDS_PER_MINUTE * calendarOptions.getWorktimeStartMinutes());
        Date startDate = DateTools.toDateTime(selectedDate,time);
        Date endDate = new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
        return new TimeInterval( startDate, endDate);

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
