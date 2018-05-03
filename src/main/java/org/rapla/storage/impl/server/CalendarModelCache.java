package org.rapla.storage.impl.server;

import org.rapla.RaplaResources;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.DefaultRaplaLock;
import org.rapla.storage.impl.RaplaLock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CalendarModelCache
{
    RaplaLock lockManager;

    private final Map<ReferenceInfo<User>, List<CalendarModelImpl>> calendarModels = new HashMap<>();
    final CachableStorageOperator operator;
    final RaplaResources i18n;
    final Logger logger;
    final CommandScheduler scheduler;

    public CalendarModelCache(CachableStorageOperator operator, RaplaResources i18n, Logger logger, final CommandScheduler scheduler)
    {
        this.operator = operator;
        this.i18n = i18n;
        this.scheduler = scheduler;
        this.logger = logger;
        this.lockManager = new DefaultRaplaLock(logger);
    }

    private void removeCalendarModelFor(ReferenceInfo<User> userId) throws RaplaException
    {
        RaplaLock.WriteLock lock = lockManager.writeLock(getClass(), "removeCalendar for user " + userId,60);
        try
        {
            this.calendarModels.remove(userId);
        }
        finally
        {
            lockManager.unlock( lock);
        }
    }

    private boolean hasExchangeExport(CalendarModelConfiguration modelConfig)
    {
        String option = modelConfig.getOptionMap().get(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
        return option != null && option.equals("true");
    }

    /** this method does not update the appointments only createInfoDialog new or remove existing appointments.
     New exchange appointments are added if the rapla appointment is in an exported calendar view but not in exchange.
     Exchange appointments are removed if the rapla appointment is not in an exported calendar view anymore.
     */
    private void updateCalendarMap(User user) throws RaplaException
    {
        final List<CalendarModelImpl> calendarModelList = new ArrayList<>();
        final boolean createIfNotNull = false;
        final ReferenceInfo<User> userId = user.getReference();
        final Preferences preferences = operator.getPreferences(user, createIfNotNull);
        if (preferences == null)
        {
            final RaplaLock.WriteLock lock = lockManager.writeLock(getClass(), "Update calendar for user no prefe " + userId, 60);
            try
            {
                this.calendarModels.remove(userId);
            }
            finally
            {
                lockManager.unlock(lock);
            }
            return; //calendarModelList;
        }
        final CalendarModelConfiguration modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
        final RaplaMap<CalendarModelConfiguration> exportMap = preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
        if (modelConfig == null && exportMap == null)
        {
            final RaplaLock.WriteLock lock = lockManager.writeLock(getClass(), "Update calendar for user  " + userId, 60);
            try
            {
                this.calendarModels.remove(userId);
            }
            finally
            {
                lockManager.unlock(lock);
            }
            return;// calendarModelList;
        }
        final List<CalendarModelConfiguration> configList = new ArrayList<>();
        if (modelConfig != null)
        {
            configList.add(modelConfig);
        }
        if (exportMap != null)
        {
            configList.addAll(exportMap.values());
        }
        // at this point configList contains all exported calendars for the user
        for (CalendarModelConfiguration config : configList)
        {
            // is exchange export enabled in export config?
            if (hasExchangeExport(config))
            {
                // calculate tasks depending on the current calendarModel and put all exported appointments into appointmentFound set
                final CalendarModelImpl calendarModelImpl;
                {
                    final Locale locale = i18n.getLocale();
                    calendarModelImpl = new CalendarModelImpl(locale, user, operator,logger);
                    Map<String, String> alternativOptions = null;
                    calendarModelImpl.setConfiguration(config, alternativOptions);
                    calendarModelList.add(calendarModelImpl);
                }

            }
        }

        final RaplaLock.WriteLock lock = lockManager.writeLock(getClass(), "Update calendar for user " + userId + " writing new calendar", 60);
        try
        {
            if (calendarModelList.size() > 0)
            {
                this.calendarModels.put(userId, calendarModelList);
            }
            else
            {
                this.calendarModels.remove(userId);
            }
        }
        finally
        {
            lockManager.unlock(lock);
        }
        //return calendarModelList;
    }

    // checks all exports if appointment is still in on of the exported calendars (check eslected resources)
    public Collection<ReferenceInfo<User>> findMatchingUser(Appointment appointment) throws RaplaException
    {
        Set<ReferenceInfo<User>> result = new HashSet<>();
        RaplaLock.ReadLock readLock = lockManager.readLock(getClass(), "findMatchingUserForAppointment");
        try
        {
            for (ReferenceInfo<User> userId : calendarModels.keySet())
            {
                // TODO check wether the user can see the appointment or no
                //
                List<CalendarModelImpl> list = calendarModels.get(userId);
                for (CalendarModelImpl conf : list)
                {
                    if (conf.isMatchingSelectionAndFilter(appointment))
                    {
                        result.add(userId);
                        break;
                    }
                }
            }

        }
        finally
        {
            lockManager.unlock(readLock);
        }
        return result;
    }

    // checks all exports if appointment is still in on of the exported calendars (check eslected resources)
    public Collection<ReferenceInfo<User>> findMatchingUsers(Allocatable allocatable) throws RaplaException
    {
        Set<ReferenceInfo<User>> result = new HashSet<>();
        RaplaLock.ReadLock lock = lockManager.readLock(getClass(), "findMatchingUserForAppointment");
        try
        {
            for (ReferenceInfo<User> userId : calendarModels.keySet())
            {
                List<CalendarModelImpl> list = calendarModels.get(userId);
                for (CalendarModelImpl model : list)
                {
                    if (model.getAllAllocatables().contains(allocatable))
                    {
                        result.add(userId);
                        break;
                    }
                }
            }

        }
        finally
        {
            lockManager.unlock(lock);
        }
        return result;
    }

    // TODO change to Promise
    public Collection<Appointment> getAppointments(ReferenceInfo<User> userId, TimeInterval syncRange) throws RaplaException
    {
        final RaplaLock.ReadLock lock = lockManager.readLock(getClass(), "getAppointments for " + userId);
        List<CalendarModelImpl> calendarModelList;
        try
        {
            calendarModelList = calendarModels.get(userId);
        }
        finally
        {
            lockManager.unlock(lock);
        }
        if (calendarModelList == null || calendarModelList.isEmpty())
        {
            return Collections.emptySet();
        }
        Collection<Appointment> appointments = new LinkedHashSet<>();
        for (CalendarModelImpl calendarModelImpl : calendarModelList)
        {
            // check if filter or calendar selection changes so that we need to add or remove events from the exchange calendar
            final Collection<Appointment> c = operator.waitForWithRaplaException(calendarModelImpl.queryAppointments(syncRange), 10000);
            appointments.addAll(c);
        }
        return appointments;
    }

    void initCalendarMap() throws RaplaException
    {
        for (User user : operator.getUsers())
        {
            updateCalendarMap(user);
        }
    }

    public  void synchronizeCalendars(UpdateResult evt) throws RaplaException
    {
        for (UpdateOperation operation : evt.getOperations())
        {
            final Class<? extends Entity> raplaType = operation.getType();

            // the exported calendars could have changed
            if (raplaType == Preferences.class)
            {
                final Preferences preferences;
                UpdateOperation<Preferences> op = operation;
                if (operation instanceof UpdateResult.Add)
                {
                    preferences = evt.getLastKnown(op.getReference());
                }
                else if (operation instanceof UpdateResult.Change)
                {
                    preferences = evt.getLastKnown(op.getReference());
                }
                else
                {
                    preferences = null;
                }
                if (preferences != null)
                {
                    ReferenceInfo<User> ownerId = preferences.getOwnerRef();
                    if (ownerId != null)
                    {
                        User owner = operator.resolve(ownerId);
                        updateCalendarMap(owner);
                    }
                    // FIXME if export is removed from a calendar we can remove calendar model from cache
                    // removeCalendarModelFor(ownerId);
                }
            }
            else if (raplaType == User.class)
            {
                if (operation instanceof UpdateResult.Remove)
                {
                    ReferenceInfo<User> userId = operation.getReference();
                    removeCalendarModelFor(userId);
                }
            }
        }
    }

}
