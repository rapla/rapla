package org.rapla.plugin.exchangeconnector.server;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask.SyncStatus;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

/**
 * This singleton class provides the functionality to save data related to the {@link ExchangeConnectorPlugin}. This includes
 * - the mapping between Rapla {@link Appointment}s and Exchange {@link microsoft.exchange.webservices.data.Appointment}s
 * - the information if the appointment originates from Rapla or from the Exchange Server
 * - a list of all appointments which have been deleted in the Rapla system but for some reason have not been deleted from the Exchange Server (hence they can be deleted later)   
 * 
 * @author Dominik Joder
 * @see {@link ExchangeAppointmentStorageObject}
 * @see {@link SynchronisationManager}
 * @see {@link ExchangeConnectorPlugin}
 */
@Singleton
public class ExchangeAppointmentStorage extends RaplaComponent
{
    Map<String, Set<SynchronizationTask>> tasks = new LinkedHashMap<String, Set<SynchronizationTask>>();
    StorageOperator operator;
    TypedComponentRole<String> LAST_SYNC_ERROR_CHANGE_HASH = new TypedComponentRole<String>("org.rapla.plugin.exchangconnector.last_sync_error_change_hash");

    //private static String DEFAULT_STORAGE_FILE_PATH = "data/exchangeConnector.dat";
    //	private String storageFilePath = DEFAULT_STORAGE_FILE_PATH;
    protected ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * public constructor of the class to read a particular file

     * @param filePath : {@link String} absolute path to the file which saves both the mapping table and the "to delete" list
     */
    @Inject
    public ExchangeAppointmentStorage(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, StorageOperator storageOperator) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        operator = storageOperator;
        DynamicType dynamicType = operator.getDynamicType(StorageOperator.SYNCHRONIZATIONTASK_TYPE);
        Attribute appointmentIdAtt = dynamicType.getAttribute("objectId");
        Attribute statusAtt = dynamicType.getAttribute("status");
        Attribute retriesAtt = dynamicType.getAttribute("retries");
        Attribute lastRetryAtt = dynamicType.getAttribute("lastRetry");
        Attribute lastErrorAtt = dynamicType.getAttribute("lastError");
        ClassificationFilter newClassificationFilter = dynamicType.newClassificationFilter();
        Collection<Allocatable> store = operator.getAllocatables(newClassificationFilter.toArray());
        for (Allocatable persistant : store)
        {
            String userId = persistant.getOwnerId();
            String appointmentId = (String) persistant.getClassification().getValue(appointmentIdAtt);
            String status = (String) persistant.getClassification().getValue(statusAtt);
            String retriesString = (String) persistant.getClassification().getValue(retriesAtt);
            Date lastRetry = (Date) persistant.getClassification().getValue(lastRetryAtt);
            String lastError = (String) persistant.getClassification().getValue(lastErrorAtt);
            if (userId == null)
            {
                getLogger().error("Synchronization task " + persistant.getId() + " has no userId. Ignoring.");
                continue;
            }
            int retries = 0;
            if (retriesString != null)
            {
                try
                {
                    retries = Integer.parseInt(retriesString);
                }
                catch (Exception ex)
                {
                    getLogger().error("Synchronization task " + persistant.getId() + " has invalid retriesString. Ignoring.");
                    continue;
                }
            }
            SynchronizationTask synchronizationTask = new SynchronizationTask(appointmentId, userId, retries, lastRetry, lastError);
            if (status == null)
            {
                getLogger().error("Synchronization task " + persistant.getId() + " has no status. Ignoring.");
                continue;
            }
            synchronizationTask.setPersistantId(persistant.getId());
            SyncStatus valueOf = SyncStatus.valueOf(status);
            if (valueOf == null)
            {
                getLogger().error("Synchronization task " + persistant.getId() + " has unsupported status '" + status + "'. Ignoring.");
                continue;
            }
            synchronizationTask.setStatus(valueOf);
            Set<SynchronizationTask> taskList = tasks.get(appointmentId);
            if (taskList == null)
            {
                taskList = new HashSet<SynchronizationTask>();
                tasks.put(appointmentId, taskList);
            }
            taskList.add(synchronizationTask);
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

    public Collection<SynchronizationTask> getTasksForUser(String userId) throws RaplaException
    {
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
    
    public void removeTasksForUser(String userId) throws RaplaException
    {
        Collection<SynchronizationTask> toRemove = getTasksForUser(userId);
        Collection<SynchronizationTask> toStore = Collections.emptyList();
        storeAndRemove(toStore, toRemove);
    }

    synchronized public SynchronizationTask getTask(Appointment appointment, String userId) throws RaplaException
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

    public Collection<SynchronizationTask> getTasks(Appointment appointment) throws RaplaException
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

    synchronized public SynchronizationTask createTask(Appointment appointment, String userId)
    {
        int retries = 0;
        Date date = null;
        String lastError = null;
        return new SynchronizationTask(appointment.getId(), userId, retries, date, lastError);
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

        Map<String, Set<String>> hashMap = new HashMap<String, Set<String>>();
        Collection<Entity> storeObjects = new HashSet<Entity>();
        Collection<Entity> removeObjects = new HashSet<Entity>();
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
                    RaplaComponent.unlock(writeLock);
                }
            }

            // remove task from database
            String persistantId = task.getPersistantId();
            if (persistantId != null)
            {
                Allocatable persistant = operator.tryResolve(persistantId, Allocatable.class);
                if (persistant != null)
                {
                    removeObjects.add(persistant);
                }
            }

        }
        for (SynchronizationTask task : toStore)
        {
            final String persistantId = task.getPersistantId();
            final Classification newClassification;
            if (persistantId != null)
            {
                final Entity persistant = operator.tryResolve(persistantId, Allocatable.class);
                if (persistant != null)
                {
                    Set<Entity> singleton = Collections.singleton(persistant);
                    Collection<Entity> editObjects = operator.editObjects(singleton, null);
                    Allocatable editable = (Allocatable) editObjects.iterator().next();
                    newClassification = editable.getClassification();
                    storeObjects.add(editable);
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
                final DynamicType dynamicType = operator.getDynamicType(StorageOperator.SYNCHRONIZATIONTASK_TYPE);
                newClassification = dynamicType.newClassification();
                final String userId = task.getUserId();
                final User owner = operator.tryResolve(userId, User.class);
                if (owner == null)
                {
                    getLogger().error("User for id " + userId + " not found. Ignoring appointmentTask for appointment " + task.getAppointmentId());
                    continue;
                }
                else
                {
                    final Allocatable newObject = getModification().newAllocatable(newClassification, owner);
                    task.setPersistantId(newObject.getId());
                    storeObjects.add(newObject);
                }
            }
            final String lastError = task.getLastError();
            newClassification.setValue("objectId", task.getAppointmentId());
            newClassification.setValue("status", task.getStatus().name());
            newClassification.setValue("retries", task.getRetries());
            newClassification.setValue("lastRetry", task.getLastRetry());
            newClassification.setValue("lastError", lastError);

            if (lastError != null)
            {
                addHash(hashMap, task, lastError);
            }
        }
        // error handling. update LAST_SYNC_ERROR_CHANGE on new error
        // check if there is a new error. Use hashing to check for new errors
        for (Entry<String, Set<String>> entry : hashMap.entrySet())
        {
            final String userid = entry.getKey();
            final Set<String> hashKeys = entry.getValue();
            final User user = operator.tryResolve(userid, User.class);
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
            final Preferences userPreferences = operator.getPreferences(user, true);
            final String newHash = LocalAbstractCachableOperator.encrypt("sha-1", hashableString.toString());
            final String hash = userPreferences.getEntryAsString(LAST_SYNC_ERROR_CHANGE_HASH, null);
            if (hash == null || !newHash.equals(hash))
            {
                Preferences edit = getModification().edit(userPreferences);
                String timestampOfFailure = getRaplaLocale().getSerializableFormat().formatTimestamp(getClientFacade().getOperator().getCurrentTimestamp());
                edit.putEntry(ExchangeConnectorRemote.LAST_SYNC_ERROR_CHANGE, timestampOfFailure);
                // store hash of errors to check changes in with future errors
                edit.putEntry(LAST_SYNC_ERROR_CHANGE_HASH, newHash);
                storeObjects.add(edit);
            }
        }
        final User user = null;
        operator.storeAndRemove(storeObjects, removeObjects, user);
    }

    private void addHash(Map<String, Set<String>> hashMap, SynchronizationTask task, String useInHashCalc)
    {
        String userId = task.getUserId();
        Set<String> set = hashMap.get(userId);
        if (set == null)
        {
            set = new HashSet<String>();
            hashMap.put(userId, set);
        }
        set.add(useInHashCalc);
    }

}