package org.rapla.plugin.exchangeconnector.server;

import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ImportExportEntityImpl;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.rapla.facade.RaplaComponent.unlock;

/**
 * This singleton class provides the functionality to save data related to the {@link ExchangeConnectorPlugin}. This includes
 * - the mapping between Rapla {@link Appointment}s and Exchange
 * - the information if the appointment originates from Rapla or from the Exchange Server
 * - a list of all appointments which have been deleted in the Rapla system but for some reason have not been deleted from the Exchange Server (hence they can be deleted later)   
 * 
 * @author Dominik Joder
 * @see {@link SynchronisationManager}
 * @see {@link ExchangeConnectorPlugin}
 */
@Singleton
public class ExchangeAppointmentStorage
{
    private static final String EXCHANGE_ID = "exchange";
    private final Map<String, Set<SynchronizationTask>> tasks = new LinkedHashMap<String, Set<SynchronizationTask>>();
    private final Map<String, ImportExportEntity> importExportEntities = new LinkedHashMap<String, ImportExportEntity>();
    //CachableStorageOperator operator;
    TypedComponentRole<String> LAST_SYNC_ERROR_CHANGE_HASH = new TypedComponentRole<String>("org.rapla.plugin.exchangconnector.last_sync_error_change_hash");
    private final JsonParserWrapper.JsonParser gson = JsonParserWrapper.defaultJson().get();
    //private static String DEFAULT_STORAGE_FILE_PATH = "data/exchangeConnector.dat";
    //	private String storageFilePath = DEFAULT_STORAGE_FILE_PATH;
    protected ReadWriteLock lock = new ReentrantReadWriteLock();
    final private Logger logger;
    final private RaplaFacade facade;
    private final CachableStorageOperator operator;


    /**
     * public constructor of the class to read a particular file

     */
    @Inject
    public ExchangeAppointmentStorage(RaplaFacade facade, Logger logger,CachableStorageOperator operator)
    {
        this.facade = facade;
        this.logger = logger;
        this.operator = operator;
    }

    protected Lock writeLock() throws RaplaException
    {
        return RaplaComponent.lock(lock.writeLock(), 60);
    }

    protected Lock readLock() throws RaplaException
    {
        return RaplaComponent.lock(lock.readLock(), 10);
    }

    public Collection<SynchronizationTask> getAllTasks() throws RaplaException
    {
        List<SynchronizationTask> result = new ArrayList<SynchronizationTask>();
        Lock lock = readLock();
        try
        {
            for (Collection<SynchronizationTask> list : tasks.values())
            {
                if (list != null)
                {
                    result.addAll(list);
                }
            }

            return result;
        }
        finally
        {
            unlock(lock);
        }
    }

    public Collection<SynchronizationTask> getTasksForUser(ReferenceInfo<User> userId) throws RaplaException
    {
        // TODO add another index (userId to Collection<SynchronizationTask>) so we can do this faster
        List<SynchronizationTask> result = new ArrayList<SynchronizationTask>();
        Lock lock = readLock();
        try
        {
            for (Collection<SynchronizationTask> list : tasks.values())
            {
                if (list != null)
                {
                    for (SynchronizationTask task : list)
                    {
                        if (task.matchesUserId( userId ))
                        {
                            result.add(task);
                        }
                    }
                }
            }
            return result;
        }
        finally
        {
            unlock(lock);
        }
    }
    
    public void removeTasksForUser(ReferenceInfo<User> userId) throws RaplaException
    {
        Collection<SynchronizationTask> toRemove = getTasksForUser(userId);
        Collection<SynchronizationTask> toStore = Collections.emptyList();
        storeAndRemove(toStore, toRemove);
    }

    synchronized public SynchronizationTask getTask(Appointment appointment, ReferenceInfo<User> userId) throws RaplaException
    {
        String appointmentId = appointment.getId();
        Lock lock = readLock();
        try
        {
            Set<SynchronizationTask> set = tasks.get(appointmentId);
            if (set != null)
            {
                for (SynchronizationTask task : set)
                {
                    if (task.matchesUserId(userId))
                    {
                        return task;
                    }
                }
            }
        }
        finally
        {
            unlock(lock);
        }
        return null;
    }

    public Collection<SynchronizationTask> getTasks(ReferenceInfo appointment) throws RaplaException
    {
        String appointmentId = appointment.getId();
        Lock lock = readLock();
        try
        {
            Set<SynchronizationTask> set = tasks.get(appointmentId);
            if (set == null)
            {
                return Collections.emptyList();
            }
            return new ArrayList<SynchronizationTask>(set);
        }
        finally
        {
            unlock(lock);
        }
    }

    synchronized public SynchronizationTask createTask(Appointment appointment, ReferenceInfo<User> userId)
    {
        int retries = 0;
        Date date = null;
        String lastError = null;
        return new SynchronizationTask(appointment.getReference(), userId, retries, date, lastError);
    }

    //	public void remove(SynchronizationTask appointmentTask) throws RaplaException {
    //		String appointmentId = appointmentTask.getAppointmentId();
    //		boolean remove = false;
    //		Lock lock = writeLock();
    //		try
    //		{
    //			Set<SynchronizationTask> set = tasks.get(appointmentId);
    //			if ( set != null)
    //			{
    //				remove = set.remove( appointmentTask);
    //			}
    //		} 
    //		finally
    //		{
    //			unlock( lock);
    //		}
    //		if ( remove)
    //		{
    //			Collection<SynchronizationTask> toStore = Collections.emptyList();
    //			Collection<SynchronizationTask> toRemove = Collections.singletonList(appointmentTask);
    //			storeAndRemove( toStore,toRemove);
    //		}
    //	}
    //	
    public void storeAndRemove(Collection<SynchronizationTask> toStore, Collection<SynchronizationTask> toRemove) throws RaplaException
    {
        Lock lock = writeLock();
        try
        {
            for (SynchronizationTask task : toStore)
            {
                String appointmentId = task.getAppointmentId();
                Set<SynchronizationTask> set = tasks.get(appointmentId);
                if (set != null)
                {
                    set.remove(task);
                }
                else
                {
                    set = new HashSet<SynchronizationTask>();
                    tasks.put(appointmentId, set);
                }
                set.add(task);
            }
            for (SynchronizationTask task : toRemove)
            {
                String appointmentId = task.getAppointmentId();
                Set<SynchronizationTask> set = tasks.get(appointmentId);
                if (set != null)
                {
                    set.remove(task);
                    if (set.isEmpty())
                    {
                        tasks.remove(appointmentId);
                    }
                }
            }

        }
        finally
        {
            unlock(lock);
        }

        Collection<Entity> storeObjects = new HashSet<>();
        Collection<ReferenceInfo<Entity>> removeObjects = new HashSet<>();
        for (SynchronizationTask task : toRemove)
        {
            // remove task from memory 
            String appointmentId = task.getAppointmentId();
            if (appointmentId != null)
            {
                Lock writeLock = writeLock();
                try
                {
                    //remove tasks from appointmenttask 
                    final Set<SynchronizationTask> set = tasks.get(appointmentId);
                    if (set != null)
                        set.remove(task);
                }
                finally
                {
                    unlock(writeLock);
                }
            }

            // remove task from database
            String persistantId = task.getPersistantId();
            if (persistantId != null)
            {
                ImportExportEntity persistant = importExportEntities.get(persistantId);
                if (persistant != null)
                {
                    removeObjects.add(((Entity)persistant).getReference());
                }
            }

        }
        Map<ReferenceInfo<User>, Set<String>> hashMap = new HashMap<ReferenceInfo<User>, Set<String>>();

        for (SynchronizationTask task : toStore)
        {
            final String persistantId = task.getPersistantId();
            if (persistantId != null)
            {
                final Entity persistant = importExportEntities.get(persistantId);
                if (persistant != null)
                {
                    final Entity edit = facade.edit(persistant);
                    ((ImportExportEntityImpl)edit).setData(gson.toJson(task));
                    storeObjects.add(edit);
                }
                else
                {
                    final String lastError = task.getLastError();
                    if (lastError != null)
                    {
                        addHash(hashMap, task, lastError);
                    }
                    continue;
                }
            }
            else
            {
                final ImportExportEntityImpl importExportEntityImpl = new ImportExportEntityImpl();
                final char[] charArray = UUID.randomUUID().toString().toCharArray();
                charArray[0] = 's';
                importExportEntityImpl.setId(new String(charArray));
                importExportEntityImpl.setExternalSystem(EXCHANGE_ID);
                importExportEntityImpl.setDirection(ImportExportDirections.EXPORT);
                importExportEntityImpl.setRaplaId(task.getAppointmentId());
                task.setPersistantId(importExportEntityImpl.getId());
                importExportEntityImpl.setData(gson.toJson(task));
                final ReferenceInfo<User> userRef = task.getUserRef();
                final User owner = facade.tryResolve(userRef);
                if (owner == null)
                {
                    getLogger().error("User for id " + userRef + " not found. Ignoring appointmentTask for appointment " + task.getAppointmentId());
                    continue;
                }
                else
                {
                    importExportEntityImpl.setOwner(owner);
                    storeObjects.add(importExportEntityImpl);
                }
            }
            final String lastError = task.getLastError();
            if (lastError != null)
            {
                addHash(hashMap, task, lastError);
            }
        }
        // error handling. update LAST_SYNC_ERROR_CHANGE on new error
        // check if there is a new error. Use hashing to check for new errors
        for (Entry<ReferenceInfo<User>, Set<String>> entry : hashMap.entrySet())
        {
            final ReferenceInfo<User> userid = entry.getKey();
            final Set<String> hashKeys = entry.getValue();
            final User user = facade.tryResolve(userid);
            if (user == null)
            {
                // User is deleted we don't have to update his preferences
                continue;
            }

            final StringBuilder hashableString = new StringBuilder();
            for (String hashEntry : hashKeys)
            {
                hashableString.append(hashEntry);
            }
            final Preferences userPreferences = facade.getPreferences(user, true);
            final String newHash = LocalAbstractCachableOperator.encrypt("sha-1", hashableString.toString());
            final String hash = userPreferences.getEntryAsString(LAST_SYNC_ERROR_CHANGE_HASH, null);
            if (hash == null || !newHash.equals(hash))
            {
                Preferences edit = facade.edit(userPreferences);
                String timestampOfFailure = new SerializableDateTimeFormat().formatTimestamp(facade.getOperator().getCurrentTimestamp());
                edit.putEntry(ExchangeConnectorRemote.LAST_SYNC_ERROR_CHANGE, timestampOfFailure);
                // store hash of errors to check changes in with future errors
                edit.putEntry(LAST_SYNC_ERROR_CHANGE_HASH, newHash);
                storeObjects.add(edit);
            }
        }
        final User user = null;
        operator.storeAndRemove(storeObjects, removeObjects, user);
    }

    private void addHash(Map<ReferenceInfo<User>, Set<String>> hashMap, SynchronizationTask task, String useInHashCalc)
    {
        ReferenceInfo<User> userId = task.getUserRef();
        Set<String> set = hashMap.get(userId);
        if (set == null)
        {
            set = new HashSet<String>();
            hashMap.put(userId, set);
        }
        set.add(useInHashCalc);
    }

    protected Logger getLogger()
    {
        return  logger;
    }

    public void refresh() throws RaplaException
    {
        final Collection<ImportExportEntity> exportEntities = operator.getImportExportEntities(EXCHANGE_ID, ImportExportDirections.EXPORT);
        tasks.clear();
        importExportEntities.clear();
        for (ImportExportEntity persistant : exportEntities)
        {
            SynchronizationTask synchronizationTask = gson.fromJson(persistant.getData(), SynchronizationTask.class);
            if (synchronizationTask.getUserId() == null)
            {
                getLogger().error("Synchronization task " + persistant.getId() + " has no userId. Ignoring.");
                continue;
            }
            if (synchronizationTask.getRetries() < 0)
            {
                getLogger().error("Synchronization task " + persistant.getId() + " has invalid retriesString. Ignoring.");
                continue;
            }
            if (synchronizationTask.getStatus() == null)
            {
                getLogger().error("Synchronization task " + persistant.getId() + " has no status. Ignoring.");
                continue;
            }
            final String appointmentId = synchronizationTask.getAppointmentId();
            if(appointmentId == null)
            {
                getLogger().error("Synchronization task " + persistant.getId() + " has no appointmentId. Ignoring.");
                continue;
            }
            Set<SynchronizationTask> taskList = tasks.get(appointmentId);
            if (taskList == null)
            {
                taskList = new HashSet<SynchronizationTask>();
                tasks.put(appointmentId, taskList);
            }
            taskList.add(synchronizationTask);
            importExportEntities.put(persistant.getId(), persistant);
        }
    }
}