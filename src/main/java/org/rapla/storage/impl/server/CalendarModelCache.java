package org.rapla.storage.impl.server;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.rapla.RaplaResources;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.server.PromiseSynchroniser;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;

public class CalendarModelCache
{
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Map<ReferenceInfo<User>, List<CalendarModelImpl>> calendarModels = new HashMap<ReferenceInfo<User>, List<CalendarModelImpl>>();
    final CachableStorageOperator operator;
    final RaplaResources i18n;
    final Logger logger;

    public CalendarModelCache(CachableStorageOperator operator, RaplaResources i18n, Logger logger)
    {
        this.operator = operator;
        this.i18n = i18n;
        this.logger = logger;
    }

    private void removeCalendarModelFor(ReferenceInfo<User> userId) throws RaplaException
    {
        Lock lock = writeLock();
        try
        {
            this.calendarModels.remove(userId);
        }
        finally
        {
            RaplaComponent.unlock(lock);
        }
    }

    private boolean hasExchangeExport(CalendarModelConfiguration modelConfig)
    {
        String option = modelConfig.getOptionMap().get(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
        return option != null && option.equals("true");
    }

    /** this method does not update the appointments only create new or remove existing appointments.
     New exchange appointments are added if the rapla appointment is in an exported calendar view but not in exchange.
     Exchange appointments are removed if the rapla appointment is not in an exported calendar view anymore.
     */
    private void updateCalendarMap(User user) throws RaplaException
    {
        final List<CalendarModelImpl> calendarModelList = new ArrayList<CalendarModelImpl>();
        final boolean createIfNotNull = false;
        final ReferenceInfo<User> userId = user.getReference();
        final Preferences preferences = operator.getPreferences(user, createIfNotNull);
        if (preferences == null)
        {
            final Lock lock = writeLock();
            try
            {
                this.calendarModels.remove(userId);
            }
            finally
            {
                RaplaComponent.unlock(lock);
            }
            return; //calendarModelList;
        }
        final CalendarModelConfiguration modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
        final Map<String, CalendarModelConfiguration> exportMap = preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
        if (modelConfig == null && exportMap == null)
        {
            final Lock lock = writeLock();
            try
            {
                this.calendarModels.remove(userId);
            }
            finally
            {
                RaplaComponent.unlock(lock);
            }
            return;// calendarModelList;
        }
        final List<CalendarModelConfiguration> configList = new ArrayList<CalendarModelConfiguration>();
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

        final Lock lock = writeLock();
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
            RaplaComponent.unlock(lock);
        }
        //return calendarModelList;
    }

    // checks all exports if appointment is still in on of the exported calendars (check eslected resources)
    public Collection<ReferenceInfo<User>> findMatchingUser(Appointment appointment) throws RaplaException
    {
        Set<ReferenceInfo<User>> result = new HashSet<ReferenceInfo<User>>();
        Lock lock = readLock();
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
            RaplaComponent.unlock(lock);
        }
        return result;
    }

    // checks all exports if appointment is still in on of the exported calendars (check eslected resources)
    public Collection<ReferenceInfo<User>> findMatchingUsers(Allocatable allocatable) throws RaplaException
    {
        Set<ReferenceInfo<User>> result = new HashSet<ReferenceInfo<User>>();
        Lock lock = readLock();
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
            RaplaComponent.unlock(lock);
        }
        return result;
    }

    // TODO change to Promise
    public Collection<Appointment> getAppointments(ReferenceInfo<User> userId, TimeInterval syncRange) throws RaplaException
    {
        final Lock lock = readLock();
        List<CalendarModelImpl> calendarModelList;
        try
        {
            calendarModelList = calendarModels.get(userId);
        }
        finally
        {
            RaplaComponent.unlock(lock);
        }
        if (calendarModelList == null || calendarModelList.isEmpty())
        {
            return Collections.emptySet();
        }
        Collection<Appointment> appointments = new LinkedHashSet<Appointment>();
        for (CalendarModelImpl calendarModelImpl : calendarModelList)
        {
            // check if filter or calendar selection changes so that we need to add or remove events from the exchange calendar
            appointments.addAll(PromiseSynchroniser.waitForWithRaplaException(calendarModelImpl.queryAppointments(syncRange), 10000));
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

    protected Lock writeLock() throws RaplaException
    {
        return RaplaComponent.lock(lock.writeLock(), 60);
    }

    protected Lock readLock() throws RaplaException
    {
        return RaplaComponent.lock(lock.readLock(), 10);
    }
}
