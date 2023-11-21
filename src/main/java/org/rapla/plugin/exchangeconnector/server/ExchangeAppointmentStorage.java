package org.rapla.plugin.exchangeconnector.server;

import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ExternalSyncEntityImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.ShowExchangeForUser;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.impl.DefaultRaplaLock;
import org.rapla.storage.impl.RaplaLock;
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
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String, Set<SynchronizationTask>> tasks = new ConcurrentHashMap<>();
    private Map<String, ExternalSyncEntity> importExportEntities = new ConcurrentHashMap<>();
    //CachableStorageOperator operator;
    TypedComponentRole<String> LAST_SYNC_ERROR_CHANGE_HASH = new TypedComponentRole<>("org.rapla.plugin.exchangconnector.last_sync_error_change_hash");
    private final JsonParserWrapper.JsonParser gson = JsonParserWrapper.defaultJson().get();
    //private static String DEFAULT_STORAGE_FILE_PATH = "data/exchangeConnector.dat";
    //	private String storageFilePath = DEFAULT_STORAGE_FILE_PATH;
    final private Logger logger;
    final private RaplaFacade facade;
    private final CachableStorageOperator operator;

    ShowExchangeForUser showExchangeForUser;
    /**
     * public constructor of the class to read a particular file

     */
    @Inject
    public ExchangeAppointmentStorage(RaplaFacade facade, Logger logger,CachableStorageOperator operator, ShowExchangeForUser showExchangeForUser)
    {
        this.facade = facade;
        this.logger = logger;
        this.operator = operator;
        this.showExchangeForUser = showExchangeForUser;
    }

    public Set<SynchronizationTask> getAllTasks() throws RaplaException
    {
        Set<SynchronizationTask> result = new HashSet<>();
        for (Collection<SynchronizationTask> list : tasks.values())
        {
            if (list != null)
            {
                result.addAll(list);
            }
        }
        logger.debug("Found " + result.size() + " existing exchange tasks. ");
        return result;
    }

    public Collection<SynchronizationTask> getTasksForUser(ReferenceInfo<User> userId, String mailbox) throws RaplaException
    {
        // TODO add another index (userId to Collection<SynchronizationTask>) so we can do this faster
        List<SynchronizationTask> result = new ArrayList<>();
        for (Collection<SynchronizationTask> list : tasks.values())
        {
            if (list != null)
            {
                for (SynchronizationTask task : list)
                {
                    if (task.matchesUserId( userId ))
                    {
                        if (mailbox == null || mailbox.equals(task.getMailboxName())) {
                            result.add(task);
                        }
                    }
                }
            }
        }
        return result;
    }
    
    public void removeTasksForUser(ReferenceInfo<User> userId, String mailbox) throws RaplaException
    {
        Collection<SynchronizationTask> toRemove = getTasksForUser(userId, mailbox);
        Collection<SynchronizationTask> toStore = Collections.emptyList();
        storeAndRemove(toStore, toRemove);
    }

    synchronized public SynchronizationTask getTask(Appointment appointment, String mailboxName)
    {
        String appointmentId = appointment.getId();
        Set<SynchronizationTask> set = tasks.get(appointmentId);
        if (set != null)
        {
            for (SynchronizationTask task : set)
            {
                if (task.matchesMailbox(mailboxName))
                {
                    return task;
                }
            }
        }
        return null;
    }

    public Collection<SynchronizationTask> getTasks(ReferenceInfo appointment) throws RaplaException
    {
        String appointmentId = appointment.getId();
        Set<SynchronizationTask> set = tasks.get(appointmentId);
        if (set == null)
        {
            return Collections.emptyList();
        }
        return new ArrayList<>(set);
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
                set = new HashSet<>();
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

        Collection<Entity> storeObjects = new HashSet<>();
        Collection<ReferenceInfo<Entity>> removeObjects = new HashSet<>();
        for (SynchronizationTask task : toRemove)
        {
            // remove task from memory 
            String appointmentId = task.getAppointmentId();
            if (appointmentId != null)
            {
                //remove tasks from appointmenttask
                final Set<SynchronizationTask> set = tasks.get(appointmentId);
                if (set != null)
                    set.remove(task);
            }

            // remove task from database
            String persistentId = task.getPersistantId();
            if (persistentId != null)
            {
                ExternalSyncEntity persistent = importExportEntities.get(persistentId);
                if (persistent != null)
                {
                    removeObjects.add(((Entity)persistent).getReference());
                }
            }

        }
        Map<ReferenceInfo<User>, Set<String>> hashMap = new HashMap<>();

        for (SynchronizationTask task : toStore)
        {
            final String persistentId = task.getPersistantId();
            if (persistentId != null)
            {
                final Entity persistent = importExportEntities.get(persistentId);
                if (persistent != null)
                {
                    final Entity edit = facade.edit(persistent);
                    ((ExternalSyncEntityImpl)edit).setData(gson.toJson(task));
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
                final ExternalSyncEntityImpl importExportEntityImpl = new ExternalSyncEntityImpl();
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
            final Preferences userPreferences = facade.getPreferences(user);
            final String newHash = LocalAbstractCachableOperator.encrypt("sha-1", hashableString.toString());
            final String hash = userPreferences.getEntryAsString(LAST_SYNC_ERROR_CHANGE_HASH, null);
            if (!newHash.equals(hash))
            {
                Preferences edit = facade.edit(userPreferences);
                String timestampOfFailure = new SerializableDateTimeFormat().formatTimestamp(operator.getCurrentTimestamp());
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
            set = new HashSet<>();
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
        importExportEntities = operator.getImportExportEntities(EXCHANGE_ID, ImportExportDirections.EXPORT);
        tasks.clear();
        List<User> removeForUser = new ArrayList<>();
        List<ReferenceInfo<ExternalSyncEntity>> toRemove = new ArrayList<>();
        for (ExternalSyncEntity persistent : importExportEntities.values())
        {
            SynchronizationTask synchronizationTask = gson.fromJson(persistent.getData(), SynchronizationTask.class);
            ReferenceInfo<ExternalSyncEntity> reference = persistent.getReference();
            if (synchronizationTask.getUserId() == null)
            {
                getLogger().debug("Synchronization task " + persistent.getId() + " has no userId. Removing.");
                toRemove.add( reference );
                continue;
            }
            if (synchronizationTask.getResourceId() == null) {
                getLogger().debug("Synchronization task " + persistent.getId() + " has no resourceId. Removing.");
                toRemove.add( reference );
                continue;
            }
            Allocatable allocatable = operator.tryResolve( synchronizationTask.getResourceId(), Allocatable.class);
            if (allocatable == null) {
                getLogger().debug("Synchronization task " + persistent.getId() + " has non existant resource. Removing.");
                toRemove.add( reference );
                continue;
            }

            if (synchronizationTask.getMailboxName() == null) {
                getLogger().debug("Synchronization task " + persistent.getId() + " has no mailboxName. Removing.");
                toRemove.add( reference );
                continue;
            }
            if (synchronizationTask.getRetries() < 0)
            {
                getLogger().debug("Synchronization task " + persistent.getId() + " has invalid retriesString. Removing.");
                toRemove.add( reference );
                continue;
            }
            if (synchronizationTask.getStatus() == null)
            {
                getLogger().debug("Synchronization task " + persistent.getId() + " has no status. Removing.");
                toRemove.add( reference );
                continue;
            }
            final String appointmentId = synchronizationTask.getAppointmentId();
            if(appointmentId == null)
            {
                getLogger().debug("Synchronization task " + persistent.getId() + " has no appointmentId. Removing.");
                toRemove.add( reference );
                continue;
            }
            User user = operator.tryResolve(synchronizationTask.getUserId(), User.class);
            if ( user == null) {
                getLogger().info("Synchronization task " + persistent.getId() + " has no valid user. Removing.");
                toRemove.add( reference );
                continue;
            }
            if (!showExchangeForUser.isExchangeEnabledFor( user )) {
                getLogger().info("Synchronization task " + persistent.getId() + " has no a user that has no exchange group. Removing.");
                toRemove.add( reference );
                removeForUser.add(user);
            }
            Set<SynchronizationTask> taskList = tasks.get(appointmentId);
            if (taskList == null)
            {
                taskList = new HashSet<>();
                tasks.put(appointmentId, taskList);
            }
            taskList.add(synchronizationTask);
        }
        if ( !toRemove.isEmpty() ) {
            for ( User user: removeForUser) {
                getLogger().info("Removing tasks for user " + user);
            }
            getLogger().info("Removing old unused synchronisation tasks " + toRemove.size()) ;
            operator.storeAndRemove(Collections.emptyList(), toRemove, (User) null);
        }

    }
}