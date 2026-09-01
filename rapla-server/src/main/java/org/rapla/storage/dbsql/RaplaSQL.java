/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, of which license fullfill the Open Source    |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbsql;

import org.jetbrains.annotations.NotNull;
import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.iterator.IterableChain;
import org.rapla.components.util.xml.RaplaNonValidatedInput;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.dynamictype.internal.KeyAndPathResolver;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.entities.storage.internal.ExternalSyncEntityImpl;
import org.rapla.entities.storage.internal.StoredArtifactImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.impl.server.EntityHistory;
import org.rapla.storage.impl.server.EntityHistory.HistoryEntry;
import org.rapla.storage.xml.CategoryReader;
import org.rapla.storage.xml.DynamicTypeReader;
import org.rapla.storage.xml.PreferenceReader;
import org.rapla.storage.xml.PreferenceWriter;
import org.rapla.storage.xml.RaplaXMLContext;
import org.rapla.storage.xml.RaplaXMLReader;
import org.rapla.storage.xml.RaplaXMLWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import java.time.LocalDateTime;
class RaplaSQL
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RaplaSQL.class);
    private final Map<Class,RaplaTypeStorage> stores = new LinkedHashMap<>();
    private final HistoryStorage history;
    RaplaXMLContext context;
    PreferenceStorage preferencesStorage;
    LockStorage lockStorage;
    private final ImportExportStorage syncEntitiesStorage;
    private final ArtifactStorage artifactStorage;

    RaplaSQL(RaplaXMLContext context) throws RaplaException
    {
        this.context = context;
        lockStorage = new LockStorage();
        // The order is important. e.g. appointments can only be loaded if the reservation they are refering to are already loaded.
        add(Category.class,new CategoryStorage(context));
        add(User.class,new UserStorage(context));
        add(DynamicType.class,new DynamicTypeStorage(context));
        add(Allocatable.class,new AllocatableStorage(context));
        preferencesStorage = new PreferenceStorage(context);
        add(Preferences.class,preferencesStorage);
        ReservationStorage reservationStorage = new ReservationStorage(context);
        add(Reservation.class,reservationStorage);
        AppointmentStorage appointmentStorage = new AppointmentStorage(context);
        add(Appointment.class,appointmentStorage);
        add(Conflict.class,new ConflictStorage(context));
        //stores.add(new DeleteStorage( context));
        history = new HistoryStorage(context);
        stores.put(HistoryEntry.class,history);

        syncEntitiesStorage = new ImportExportStorage(context);
        stores.put(ExternalSyncEntity.class, syncEntitiesStorage);
        artifactStorage = new ArtifactStorage(context);
        stores.put(StoredArtifact.class, artifactStorage);
        // now set delegate because reservation storage should also use appointment storage
        reservationStorage.setAppointmentStorage(appointmentStorage);
    }

    private <T extends Entity<T>> void  add(Class<T> entityClass, RaplaTypeStorage<T> storage)
    {
        stores.put( entityClass, storage);
    }

    public Map<String, String> getIdColumns()
    {
        Map<String, String> idColumns = new LinkedHashMap<>();
        for (TableStorage storage : getStoresWithChildren())
        {
            String tableName = storage.getTableName();
            String idColumn = storage.getIdColumn();
            if (idColumn != null)
            {
                idColumns.put(tableName, idColumn);
            }
        }
        return idColumns;
    }

    private List<Storage<?>> getStoresWithChildren()
    {
        List<Storage<?>> storages = new ArrayList<>();
        for (RaplaTypeStorage store : stores.values())
        {
            storages.add(store);
            @SuppressWarnings("unchecked")
            Collection<Storage<?>> subStores = store.getSubStores();
            storages.addAll(subStores);
        }
        storages.add(history);
        return storages;
    }

    /***************************************************
     *   Create everything                             *
     ***************************************************/
    synchronized public void createAll(Connection con) throws SQLException, RaplaException
    {
        LocalDateTime connectionTimestamp = getDatabaseTimestamp(con);
        lockStorage.setConnection(con, connectionTimestamp);
        for (RaplaTypeStorage storage : stores.values())
        {
            storage.setConnection(con, connectionTimestamp);
            try
            {
                storage.insertAll();
            }
            finally
            {
                storage.removeConnection();
            }
        }
    }

    synchronized public void removeAll(Connection con) throws SQLException, RaplaException
    {
        LocalDateTime connectionTimestamp = getDatabaseTimestamp(con);
        final Collection<TableStorage> storeIt = (Collection) stores.values();
        final Collection<TableStorage> lockStorages = Collections.singletonList(lockStorage);
        for (TableStorage storage : new IterableChain<>(storeIt, lockStorages))
        {
            storage.setConnection(con, connectionTimestamp);
            try
            {
                storage.deleteAll();
            }
            finally
            {
                storage.removeConnection();
            }
        }

    }

    synchronized public void loadAll(Connection con) throws SQLException, RaplaException
    {
        LocalDateTime connectionTimestamp = getDatabaseTimestamp(con);
        for (Storage storage : stores.values())
        {
            storage.setConnection(con, connectionTimestamp);
            try
            {
                storage.loadAll();
            }
            finally
            {
                storage.removeConnection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    synchronized public void remove(Connection con, ReferenceInfo referenceInfo, LocalDateTime connectionTimestamp) throws SQLException, RaplaException
    {
        final Class<? extends Entity> typeClass = referenceInfo.getType();
        if (Attribute.class == typeClass)
            return;
        boolean couldDelete = false;
        {
            RaplaTypeStorage storage = stores.get( typeClass);
            if ( storage != null)
            {
                couldDelete = delete(con, referenceInfo, connectionTimestamp, storage);
            }
        }
        if (!couldDelete)
        {
            throw new RaplaException("No Storage-Sublass matches this object: " + referenceInfo.getType());
        }
    }

    private boolean delete(Connection con, ReferenceInfo referenceInfo, LocalDateTime connectionTimestamp, RaplaTypeStorage storage) throws SQLException, RaplaException
    {
        storage.setConnection(con, connectionTimestamp);
        history.setConnection( con, connectionTimestamp);
        try
        {
            List<ReferenceInfo> list = Collections.singletonList( referenceInfo);
            storage.deleteEntities(list);
            final Class<? extends Entity> typeClass = referenceInfo.getType();
            if ( history.canDelete( typeClass))
            {
                history.deleteEntities( list);
            }
            return true;
        }
        finally
        {
            storage.removeConnection();
            history.removeConnection();
        }

    }

    @SuppressWarnings("unchecked")
    synchronized public void store(Connection con, Map<Entity,Entity> entities, LocalDateTime connectionTimestamp) throws SQLException, RaplaException
    {

        Map<Storage, List<Entity>> store = new LinkedHashMap<>();
        Map<Entity,Entity> historyList = new LinkedHashMap<>();
        boolean updateHistory = false;
        for (Entity entity : entities.keySet())
        {
            final Class typeClass = entity.getTypeClass();
            if (Attribute.class == typeClass)
                continue;
            boolean found = false;
            {
                RaplaTypeStorage storage = stores.get(typeClass);
                if (history.canStore( typeClass))
                {
                    updateHistory = true;
                    historyList.put(entity, entities.get( entity));
                }
                if ( storage != null)
                {
                    List<Entity> list = store.get(storage);
                    if (list == null)
                    {
                        list = new ArrayList<>();
                        store.put(storage, list);
                    }
                    list.add(entity);
                    found = true;
                }
            }
            if (!found)
            {
                throw new RaplaException("No Storage-Sublass matches this object: " + entity.getClass());
            }
        }
        // always update history at the end

        for (Storage storage : store.keySet())
        {
            List<Entity> list = store.get(storage);
            store(con, connectionTimestamp, list, storage);
        }
        if (updateHistory)
        {
            history.setConnection(con, connectionTimestamp);
            try
            {
                history.save(historyList);
            }
            finally
            {
                history.removeConnection();
            }
        }
    }

    private void store(Connection con, LocalDateTime connectionTimestamp, List<Entity> list, Storage storage) throws SQLException, RaplaException
    {

        storage.setConnection(con, connectionTimestamp);
        try
        {
            storage.save(list);
        }
        finally
        {
            storage.removeConnection();
        }
    }

    public void createOrUpdateIfNecessary(Connection con, Map<String, TableDef> schema) throws SQLException, RaplaException
    {

        // We dont't need a timestamp for createOrUpdate
        LocalDateTime connectionTimestamp = null;
        // Upgrade db if necessary
        final List<TableStorage> storesWithChildren = getTableStorages();
        for (TableStorage storage : storesWithChildren)
        {
            storage.setConnection(con, connectionTimestamp);
            try
            {
                storage.createOrUpdateIfNecessary(schema);
            }
            finally
            {
                storage.removeConnection();
            }
        }
    }

    private List<TableStorage> getTableStorages()
    {
        final List<TableStorage> storesWithChildren = new ArrayList<>();
        storesWithChildren.addAll(getStoresWithChildren());
        storesWithChildren.add(lockStorage);
        return storesWithChildren;
    }

    public void storePatches(Connection connection, List<PreferencePatch> preferencePatches, LocalDateTime connectionTimestamp) throws SQLException, RaplaException
    {
        PreferenceStorage storage = preferencesStorage;
        storage.setConnection(connection, connectionTimestamp);
        try
        {
            preferencesStorage.storePatches(preferencePatches);
        }
        finally
        {
            storage.removeConnection();
        }
    }

    public Collection<ReferenceInfo> update(Connection c, LocalDateTime lastUpdated, LocalDateTime connectionTimestamp) throws SQLException, RaplaException
    {
        history.setConnection(c, connectionTimestamp);
        try
        {
            return history.update(lastUpdated);
        }
        finally
        {
            history.removeConnection();
        }
    }

    public List<PreferencePatch> getPatches(Connection c, LocalDateTime lastUpdated) throws SQLException, RaplaException
    {
        try
        {
            preferencesStorage.setConnection(c, null);
            return preferencesStorage.getPatches(lastUpdated);
        }
        finally
        {
            preferencesStorage.setConnection(null, null);
        }
    }

    public Map<String, ExternalSyncEntity> getImportExportEntities(String id, int importExportDirection, Connection con) throws RaplaException
    {
        try
        {
            syncEntitiesStorage.setConnection(con, null);
            return syncEntitiesStorage.load(id, importExportDirection);
        }
        catch (SQLException e)
        {
            throw new RaplaException("Error reading ImportExportEntries for " + id + " with direction " + importExportDirection);
        }
        finally
        {
            syncEntitiesStorage.removeConnection();
        }
    }

    public Collection<ExternalSyncEntity> getAllSyncEntities( Connection con) throws RaplaException
    {
        try
        {
            syncEntitiesStorage.setConnection(con, null);
            return syncEntitiesStorage.loadAllIntoList();
        }
        catch (SQLException e)
        {
            throw new RaplaException("Error reading ImportExportEntries.");
        }
        finally
        {
            syncEntitiesStorage.removeConnection();
        }
    }

    public Collection<StoredArtifact> getAllArtifacts(Connection con) throws RaplaException
    {
        try
        {
            // pass the connection timestamp: the timestamp read helper dereferences it even without checkCurrent
            artifactStorage.setConnection(con, getDatabaseTimestamp(con));
            return artifactStorage.loadAllIntoList();
        }
        catch (SQLException e)
        {
            throw new RaplaException("Error reading artifacts.", e);
        }
        finally
        {
            artifactStorage.removeConnection();
        }
    }

    public StoredArtifact getArtifact(Connection con, String id) throws RaplaException
    {
        try
        {
            artifactStorage.setConnection(con, getDatabaseTimestamp(con));
            return artifactStorage.loadById(id);
        }
        catch (SQLException e)
        {
            throw new RaplaException("Error reading artifact " + id, e);
        }
        finally
        {
            artifactStorage.removeConnection();
        }
    }

    public Collection<StoredArtifact> getAllArtifactsMetadata(Connection con) throws RaplaException
    {
        try
        {
            artifactStorage.setConnection(con, getDatabaseTimestamp(con));
            return artifactStorage.loadMetadataIntoList();
        }
        catch (SQLException e)
        {
            throw new RaplaException("Error reading artifact metadata.", e);
        }
        finally
        {
            artifactStorage.removeConnection();
        }
    }

    public void saveAllArtifacts(Connection con, Collection<StoredArtifact> artifacts) throws RaplaException
    {
        try
        {
            artifactStorage.setConnection(con, null);
            artifactStorage.deleteAll();
            artifactStorage.save(artifacts);
        }
        catch (SQLException e)
        {
            throw new RaplaException("Error saving artifacts.", e);
        }
        finally
        {
            artifactStorage.removeConnection();
        }
    }

    public void saveAllSyncEntities(Connection con, Collection<ExternalSyncEntity> externalSyncEntityList) throws RaplaException {
        try
        {
            syncEntitiesStorage.setConnection(con, null);
            syncEntitiesStorage.deleteAll();
            syncEntitiesStorage.save(externalSyncEntityList);
        }
        catch (SQLException e)
        {
            throw new RaplaException("Error reading ImportExportEntries.");
        }
        finally
        {
            syncEntitiesStorage.removeConnection();
        }
    }


    public LocalDateTime getLastUpdated(Connection c) throws SQLException, RaplaException
    {
        if (lockStorage.disableLocks())
        {
            return LocalDateTime.now();
        }
        try
        {
            lockStorage.setConnection(c, null);
            return lockStorage.readLockTimestamp();
        }
        finally
        {
            lockStorage.removeConnection();
        }
    }

    public LocalDateTime getLastRequested(Connection c, String id) throws SQLException, RaplaException
    {
        try
        {
            lockStorage.setConnection(c, null);
            return lockStorage.readLastRequested(id);
        }
        finally
        {
            lockStorage.removeConnection();
        }
    }

    synchronized public LocalDateTime getDatabaseTimestamp(Connection con) throws SQLException, RaplaException
    {
        try
        {
            lockStorage.setConnection(con, null);
            return lockStorage.getDatabaseTimestamp();
        }
        finally
        {
            lockStorage.removeConnection();
        }
    }

    public void requestLocks(Connection connection, LocalDateTime connectionTimestamp, Collection<String> ids, Long validMilliseconds, boolean deleteLocksOnFailure)
            throws SQLException, RaplaException
    {
        try
        {
            lockStorage.setConnection(connection, connectionTimestamp);
            if (ids.contains(LockStorage.GLOBAL_LOCK))
            {
                if (ids.size() > 1)
                {
                    LOGGER.warn("More then one lock requested when using a global lock.");
                }
                lockStorage.getGlobalLock();
            }
            else
            {
                lockStorage.getLocks(ids, validMilliseconds, deleteLocksOnFailure);
            }
        }
        finally
        {
            lockStorage.removeConnection();
        }
    }

    public void cleanupOldLocks(Connection c) throws SQLException, RaplaException
    {
        try
        {
            lockStorage.setConnection(c, null);
            lockStorage.cleanupOldLocks();
        }
        finally
        {
            lockStorage.removeConnection();
        }
    }

    public void removeLocks(Connection connection, Collection<String> ids, LocalDateTime updatedUntil, boolean deleteLocks) throws SQLException, RaplaException
    {
        try
        {
            lockStorage.setConnection(connection, null);
            lockStorage.removeLocks(ids, updatedUntil, deleteLocks);
        }
        finally
        {
            lockStorage.removeConnection();
        }
    }

    public void cleanupHistory(Connection con, LocalDateTime date) throws SQLException
    {
        try
        {
            history.setConnection(con, null);
            history.cleanupHistory(date);
        }
        finally
        {
            history.removeConnection();
        }
    }

}

// TODO Think about canDelete and remove of locks when entities are deleted (not updated)
class LockStorage extends AbstractTableStorage
{
    private static final Logger LOCK_LOGGER = LoggerFactory.getLogger(LockStorage.class);
    static final String GLOBAL_LOCK = "GLOBAL_LOCK";
    private final String countLocksSql = "SELECT COUNT(LOCKID) FROM WRITE_LOCK WHERE LOCKID <> '" + GLOBAL_LOCK + "' AND ACTIVE = 1";
    private final String cleanupSql = "UPDATE WRITE_LOCK SET ACTIVE = 2 WHERE ACTIVE = 1 and VALID_UNTIL < CURRENT_TIMESTAMP";
    private final String activateSql = "UPDATE WRITE_LOCK SET ACTIVE = 1, LAST_CHANGED = CURRENT_TIMESTAMP, VALID_UNTIL = ? WHERE LOCKID = ? AND ACTIVE <> 1";
    private final String deactivateWithLastRequestedUpdateSql = "UPDATE WRITE_LOCK SET ACTIVE = 2, LAST_REQUESTED = ? WHERE LOCKID = ?";
    private final String deactivateWithoutLastRequestedUpdateSql = "UPDATE WRITE_LOCK SET ACTIVE = 2 WHERE LOCKID = ?";
    private final String deleteLocksSql = "DELETE FROM WRITE_LOCK WHERE LOCKID = ?";
    private String readTimestampInclusiveLockedSql;
    private String requestTimestampSql;

    public LockStorage()
    {
        super("WRITE_LOCK",
                new String[] { "LOCKID VARCHAR(255) NOT NULL PRIMARY KEY", "LAST_CHANGED TIMESTAMP", "LAST_REQUESTED TIMESTAMP", "VALID_UNTIL TIMESTAMP",
                        "ACTIVE INTEGER NOT NULL" }, false);
        insertSql = "insert into WRITE_LOCK (LOCKID, LAST_CHANGED, LAST_REQUESTED, VALID_UNTIL, ACTIVE) values (?, CURRENT_TIMESTAMP, ?, ?, 1)";
        deleteSql = null;//"delete from WRITE_LOCK WHERE LOCKID = ?";
        selectSql += " WHERE LOCKID = ?";
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndAdd(schema, "LAST_REQUESTED");
        checkAndAdd(schema, "ACTIVE");
        checkAndAdd(schema, "VALID_UNTIL");
    }

    @Override
    public void setConnection(Connection con, LocalDateTime connectionTimestamp) throws SQLException
    {
        super.setConnection(con, connectionTimestamp);
        if (isHsqldb())
        {
            requestTimestampSql = "VALUES(CURRENT_TIMESTAMP)";
        }
        else
        {
            requestTimestampSql = "SELECT CURRENT_TIMESTAMP";
        }
        readTimestampInclusiveLockedSql =
                "SELECT LAST_CHANGED FROM WRITE_LOCK WHERE ACTIVE = 1 UNION " + requestTimestampSql + " ORDER BY LAST_CHANGED ASC LIMIT 1";
    }

    public void removeLocks(Collection<String> ids, LocalDateTime updatedUntil, boolean deleteLocks) throws RaplaException
    {
        if ( disableLocks())
        {
            return;
        }

        if (ids == null || ids.isEmpty())
        {
            return;
        }
        boolean updateTimestamp = updatedUntil != null;
        try (final PreparedStatement stmt = con.prepareStatement(
                deleteLocks ? deleteLocksSql : updateTimestamp ? deactivateWithLastRequestedUpdateSql : deactivateWithoutLastRequestedUpdateSql))
        {
            for (String id : ids)
            {
                int i = 1;
                if (!deleteLocks && updateTimestamp)
                {
                    stmt.setTimestamp(i, new java.sql.Timestamp(DateTools.toMilli(updatedUntil)));
                    i++;
                }
                stmt.setString(i, id);
                stmt.addBatch();
            }

            final int[] result = stmt.executeBatch();
            LOCK_LOGGER.debug("deactivated logs: {}", Arrays.toString(result));
        }
        catch (Exception e)
        {
            throw new RaplaException("Could not free locks");
        }
    }

    void cleanupOldLocks() throws RaplaException
    {
        if ( disableLocks())
            return;
        try (final PreparedStatement deleteStmt = con.prepareStatement(cleanupSql))
        {
            deleteStmt.setQueryTimeout(10);
            final int executeBatch = deleteStmt.executeUpdate();
            LOCK_LOGGER.debug("cleanuped logs: {}", executeBatch);
        }
        catch (Exception e)
        {
            throw new RaplaException("could not delete old locks: " + e.getMessage(), e);
        }
    }

    LocalDateTime getDatabaseTimestamp() throws RaplaException
    {
        if ( disableLocks())
        {
            return LocalDateTime.now();
        }
        final LocalDateTime now;

        {
            try (final PreparedStatement stmt = con.prepareStatement(requestTimestampSql))
            {
                final ResultSet result = stmt.executeQuery();
                result.next();
                now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(result.getTimestamp(1).getTime()), java.time.ZoneOffset.UTC);
            }
            catch (Exception e)
            {
                throw new RaplaException("Could not get current Timestamp from DB");
            }
        }
        return now;
    }

    private void checkGlobalLockThrowException() throws RaplaException
    {
        try (final PreparedStatement stmt = con.prepareStatement(selectSql + " AND ACTIVE = 1"))
        {
            stmt.setString(1, GLOBAL_LOCK);
            final ResultSet result = stmt.executeQuery();
            if (result.next())
            {
                // Surface enough context to distinguish stale-from-SIGTERM vs
                // genuine contention. VALID_UNTIL column drives the periodic
                // cleanup query (line 575); a past timestamp here means a
                // previous holder died before releasing — admin can clear it
                // manually by setting ACTIVE=2 in the WRITE_LOCK table.
                final java.sql.Timestamp lastChanged  = result.getTimestamp("LAST_CHANGED");
                final java.sql.Timestamp validUntil   = result.getTimestamp("VALID_UNTIL");
                final boolean expired = validUntil != null
                        && validUntil.toInstant().isBefore(java.time.Instant.now());
                final String suffix = expired
                        ? " (already expired " + validUntil
                          + " — previous holder likely died without releasing; wait for the periodic cleanup or set ACTIVE=2 manually)"
                        : " (held since " + lastChanged
                          + ", valid until " + validUntil
                          + " — another process is currently writing; wait for release)";
                throw new RaplaException("Global lock already taken" + suffix);
            }
        }
        catch (SQLException e)
        {
            throw new RaplaException("Global lock check failed", e);
        }
    }

    public LocalDateTime readLastRequested(String id) throws RaplaException
    {
        try (final PreparedStatement stmt = con.prepareStatement(selectSql))
        {
            stmt.setString(1, id);
            final ResultSet dbResult = stmt.executeQuery();
            if (dbResult.next())
            {
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(dbResult.getTimestamp(3).getTime()), java.time.ZoneOffset.UTC);
            }
            throw new IllegalStateException();
        }
        catch (SQLException e)
        {
            throw new RaplaException("Could not read last_requested from db for id: " + id);
        }
    }

    private void activateLocks(Collection<String> ids, Long validMilliseconds) throws RaplaException
    {
        try (final PreparedStatement insertStmt = con.prepareStatement(insertSql);
                final PreparedStatement updateStatement = con.prepareStatement(activateSql);
                final PreparedStatement containsStmt = con.prepareStatement("SELECT COUNT(LOCKID) FROM WRITE_LOCK WHERE LOCKID = ?"))
        {
            final LocalDateTime databaseTimestamp = getConnectionTimestamp();
            BitSet existingIds = new BitSet(ids.size());
            int index = 0;
            for (String id : ids)
            {// extract existing ids from DB
                containsStmt.setString(1, id);
                final ResultSet result = containsStmt.executeQuery();
                if (result != null && result.next() && result.getInt(1) != 0)
                {
                    existingIds.set(index);
                }
                index++;
            }
            boolean executeUpdate = false;
            boolean executeInsert = false;
            index = 0;
            for (String id : ids)
            {
                final long validOffset = calcValidOffset(validMilliseconds, id);
                final java.sql.Timestamp validUntil = new java.sql.Timestamp(DateTools.toMilli(databaseTimestamp) + validOffset);
                if (existingIds.get(index))
                {//update
                    updateStatement.setTimestamp(1, validUntil);
                    updateStatement.setString(2, id);
                    updateStatement.addBatch();
                    executeUpdate = true;
                }
                else
                {// insert
                    insertStmt.setString(1, id);
                    insertStmt.setTimestamp(2, new java.sql.Timestamp(DateTools.toMilli(databaseTimestamp) - (4 * DateTools.MILLISECONDS_PER_WEEK)));
                    insertStmt.setTimestamp(3, validUntil);
                    insertStmt.addBatch();
                    executeInsert = true;
                }
                index++;
            }
            if (executeInsert)
            {
                insertStmt.executeBatch();
            }
            if (executeUpdate)
            {
                final int[] updateResult = updateStatement.executeBatch();
                for (int columnsUpdated : updateResult)
                {
                    if (columnsUpdated != 1)
                    {
                        throw new IllegalStateException("could not activate lock for " + ids);
                    }
                }
            }
            if (con.getMetaData().supportsTransactions())
            {
                con.commit();
            }
        }
        catch (Exception e)
        {
            throw new RaplaException("Could not get Locks", e);
        }
    }

    private long calcValidOffset(Long validMilliseconds, String id)
    {
        final long validOffset;
        if (validMilliseconds == null || validMilliseconds.longValue() <= 0)
        {
            final long millisecondsPerMinute = DateTools.MILLISECONDS_PER_MINUTE;
            validOffset = id.startsWith(GLOBAL_LOCK) ? millisecondsPerMinute * 5 : millisecondsPerMinute;
        }
        else
        {
            validOffset = validMilliseconds;
        }
        return validOffset;
    }

    public void getLocks(Collection<String> ids, Long validMilliseconds, boolean deleteLocksOnFailure) throws RaplaException
    {
        if ( disableLocks())
        {
            return;
        }
        if (ids == null || ids.isEmpty())
        {
            return;
        }
        checkGlobalLockThrowException();
        activateLocks(ids, validMilliseconds);
        try
        {
            checkGlobalLockThrowException();
        }
        catch (RaplaException e)
        {
            removeLocks(ids, null, deleteLocksOnFailure);
            try
            {
                if (con.getMetaData().supportsTransactions())
                {
                    con.commit();
                }
            }
            catch (SQLException re)
            {
                LOCK_LOGGER.error("Could not commit remove of locks ({}) caused by: {}", ids, re.getMessage(), re);
            }
            throw e;
        }
    }

    public boolean disableLocks()
    {
        return false;
    }

    public void getGlobalLock() throws RaplaException
    {
        if ( disableLocks())
        {
            return;
        }
        final LocalDateTime lastLocked = readLockTimestamp();
        try
        {
            activateLocks(Collections.singleton(GLOBAL_LOCK), null);
            final LocalDateTime newLockTimestamp = readLockTimestamp();
            if (!newLockTimestamp.isAfter(lastLocked))
            {
                Thread.sleep(1);
                // remove it so we can request a new
                removeLocks(Collections.singleton(GLOBAL_LOCK), null, false);
            }
            final long startWaitingTime = System.currentTimeMillis();
            // wait
            while (true) {
                try (PreparedStatement cstmt = con.prepareStatement(countLocksSql)) {
                    final ResultSet result = cstmt.executeQuery();
                    result.next();
                    if (result.getInt(1) < 1) {
                        // no other locks, we can continue
                        break;
                    }
                    else {
                        // wait for max 30 seconds
                        final long actualTime = System.currentTimeMillis();
                        if ((actualTime - startWaitingTime) > 30000L) {
                            removeLocks(Collections.singleton(GLOBAL_LOCK), null, false);
                            if (con.getMetaData().supportsTransactions()) {// Commit so others do not see the global lock any more
                                con.commit();
                            }
                            throw new RaplaException("Global lock timed out");
                        }
                        // wait
                        Thread.sleep(1000);
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RaplaException("Error receiving lock for " + GLOBAL_LOCK, e);
        }
    }

    LocalDateTime readLockTimestamp() throws RaplaException
    {
        try (PreparedStatement stmt = con.prepareStatement(readTimestampInclusiveLockedSql))
        {
            final ResultSet result = stmt.executeQuery();
            while (result.next())
            {
                java.sql.Timestamp now = result.getTimestamp(1);
                // UTC-semantic LDT, consistent with getDatabaseTimestamp() above and
                // getTimestampOrNow() in AbstractTableStorage. Timestamp.toLocalDateTime()
                // would re-interpret the epoch millis in the JVM's default timezone,
                // producing an LDT that's offset by the local UTC offset — when that LDT
                // is later round-tripped through DateTools.toMilli() (which treats LDTs
                // as UTC) the boundary millis come out shifted by hours, and PreferenceStorage's
                // "WHERE LAST_CHANGED > ?" readback in DBOperator.dispatch never sees
                // the patches it just wrote. Fallout: patches issued via
                // facade.store(prefsEdit) get persisted to the PREFERENCE row but never
                // applied to the in-memory cache — most visibly, RefreshSessionService's
                // SESSION pref vanishes immediately after persistSession() on DB-backed stores.
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now.getTime()), java.time.ZoneOffset.UTC);
            }
        }
        catch (Exception e)
        {
            throw new RaplaException("Could not read Timestamp from DB", e);
        }
        throw new RaplaException("Could not read Timestamp from DB. No timestamp found.");
    }
}

abstract class RaplaTypeStorage<T extends Entity<T>> extends EntityStorage<T>
{
    Class<? extends Entity> raplaType;

    RaplaTypeStorage(RaplaXMLContext context, Class<? extends Entity> raplaType, String tableName, String[] entries) throws RaplaException
    {
        super(context, tableName, entries);
        this.raplaType = raplaType;
    }

    RaplaTypeStorage(RaplaXMLContext context, Class<? extends Entity> raplaType, String tableName, String[] entries, boolean checkLastChanged)
            throws RaplaException
    {
        super(context, tableName, entries, checkLastChanged);
        this.raplaType = raplaType;
    }

    boolean canStore(Class<Entity> typeClass)
    {
        return typeClass == raplaType;
    }

    boolean canDelete(Class<Entity> typeClass)
    {
        return canStore(typeClass);
    }

    abstract void insertAll() throws SQLException, RaplaException;

    protected String getXML(RaplaXMLWriter writer, RaplaObject raplaObject) throws RaplaException
    {
        StringWriter stringWriter = new StringWriter();
        BufferedWriter bufferedWriter = new BufferedWriter(stringWriter);
        writer.setWriter(bufferedWriter);
        writer.setSQL(true);
        try
        {
            writer.writeObject(raplaObject);
            bufferedWriter.flush();
        }
        catch (IOException ex)
        {
            throw new RaplaException(ex);
        }
        return stringWriter.getBuffer().toString();
    }

    protected void processXML(RaplaXMLReader raplaXMLReader, String xml) throws RaplaException
    {
        if (xml == null || xml.trim().length() <= 10)
        {
            throw new RaplaException("Can't load empty xml");
        }
        String xmlWithNamespaces = RaplaXMLReader.wrapRaplaDataTag(xml);
        RaplaNonValidatedInput parser = context.lookup(RaplaNonValidatedInput.class);
        parser.read(xmlWithNamespaces, raplaXMLReader);
        //return raplaXMLReader;
    }

    //    public void update( LocalDateTime lastUpdated, UpdateResult updateResult) throws SQLException
    //    {
    //        if (!hasLastChangedTimestamp)
    //        {
    //            return;
    //        }
    //        PreparedStatement stmt = null;
    //        try
    //        {
    //            stmt = con.prepareStatement(loadAllUpdatesSql);
    //            setTimestamp(stmt, 1, lastUpdated);
    //            stmt.execute();
    //            final ResultSet resultSet = stmt.getResultSet();
    //            int count =0;
    //            if (resultSet == null)
    //            {
    //                return;
    //            }
    //            while(resultSet.next())
    //            {
    //                count ++;
    //                final String id = resultSet.getString(1);
    //                if(id == null)
    //                {
    //                    continue;
    //                }
    //
    //                // deletion of entities is handled in DeleteStorage
    //                final Entity<?> oldEntity = entityStore.tryResolve(id);
    //                if(oldEntity != null)
    //                {
    //                    // TODO think about do not load if the lastChanged timestamp has not changed
    ////                    int lastChangedColumn = resultSet.;
    ////                    getTimestamp(resultSet, lastChangedColumn);
    //                }
    //                load(resultSet);
    //                updateSubstores(id);
    //                final Entity<?> newEntity = entityStore.tryResolve(id);
    //                if(oldEntity == null)
    //                {// we have a new entity
    //                    updateResult.addOperation(new UpdateResult.Add(newEntity.getId(), newEntity.getRaplaType()));
    //                }
    //                else
    //                {// or a update
    //                    final LocalDateTime lastChangedOld = ((Timestamp)oldEntity).getLastChanged();
    //                    final LocalDateTime lastChangedNew = ((Timestamp)newEntity).getLastChanged();
    //                    if(lastChangedOld.before(lastChangedNew))
    //                    {
    //                        updateResult.addOperation(new UpdateResult.Change(newEntity.getId(), newEntity.getRaplaType()));
    //                    }
    //                }
    //            }
    //            getLogger().debug("Updated " + count);
    //        }
    //        finally
    //        {
    //            if (stmt != null)
    //            {
    //                stmt.close();
    //            }
    //        }
    //    }

    //    protected StringBuilder createQueryString(final Collection<String> ids, final String startQueryString)
    //    {
    //        final StringBuilder sb = new StringBuilder(startQueryString);
    //        boolean first = true;
    //        for (String localId : ids)
    //        {
    //            if(first)
    //            {
    //                first = false;
    //            }
    //            else
    //            {
    //                sb.append(",");
    //            }
    //            sb.append(localId);
    //        }
    //        sb.append(")");
    //        return sb;
    //    }

}

class CategoryStorage extends RaplaTypeStorage<Category>
{
    Map<Category, Integer> orderMap = new HashMap<>();
    KeyAndPathResolver keyAndPathResolver;
    Map<Category, ReferenceInfo<Category>> categoriesWithoutParent = new TreeMap<>((o1, o2) -> {
        if (o1.equals(o2)) {
            return 0;
        }
        int ordering1 = (orderMap.get(o1)).intValue();
        int ordering2 = (orderMap.get(o2)).intValue();
        if (ordering1 < ordering2) {
            return -1;
        }
        if (ordering1 > ordering2) {
            return 1;
        }
        if (o1.hashCode() > o2.hashCode()) {
            return -1;
        } else {
            return 1;
        }
    });

    public CategoryStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, Category.class, "CATEGORY",
                new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "PARENT_ID VARCHAR(255) KEY", "CATEGORY_KEY VARCHAR(255) NOT NULL",
                        "DEFINITION TEXT NOT NULL", "PARENT_ORDER INTEGER", "LAST_CHANGED TIMESTAMP KEY" });
        if (entityStore != null)
        {
            keyAndPathResolver = context.lookup(KeyAndPathResolver.class);
        }
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndAdd(schema, "LAST_CHANGED");
        checkAndDrop(schema, "DELETED");
    }

    /*
    private Collection<String> getTransitiveIds(String parentId) throws SQLException, RaplaException {
		Set<String> childIds = new HashSet<String>();
		String sql = "SELECT ID FROM CATEGORY WHERE PARENT_ID=?";
		PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = con.prepareStatement(sql);
            setString(stmt,1, parentId);
            rset = stmt.executeQuery();
            while (rset.next ()) {
            	String id = readId(rset, 1, Category.class);
            	childIds.add( id);
            }
        } finally {
            if (rset != null)
                rset.close();
            if (stmt!=null)
                stmt.close();
        }
        Set<String> result = new HashSet<String>();
        for (String childId : childIds)
        {
        	result.addAll( getTransitiveIds(childId));
        }
        result.add( parentId);
		return result;
    }
    */

    @Override
    protected int write(PreparedStatement stmt, Category category) throws SQLException, RaplaException
    {
        if (category.getReference().equals(Category.SUPER_CATEGORY_REF))
            return 0;
        setId(stmt, 1, category);
        setId(stmt, 2, category.getParent());
        int order = getOrder(category);
        RaplaXMLWriter categoryWriter = context.lookup(PreferenceWriter.WRITERMAP).get(Category.class);
        String xml = getXML(categoryWriter, category);
        setString(stmt, 3, category.getKey());
        setText(stmt, 4, xml);
        setInt(stmt, 5, order);
        setTimestamp(stmt, 6, category.getLastChanged());
        stmt.addBatch();
        return 1;
    }

    private int getOrder(Category category)
    {
        Category parent = category.getParent();
        if (parent == null)
        {
            return 0;
        }
        Category[] childs = parent.getCategories();
        for (int i = 0; i < childs.length; i++)
        {
            if (childs[i].equals(category))
            {
                return i;
            }
        }
        getLogger().error("Category not found in parent");
        return 0;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        ReferenceInfo<Category> id = readId(rset, 1, Category.class);
        ReferenceInfo<Category> parentId = readId(rset, 2, Category.class, true);

        String xml = getText(rset, 4);
        Integer order = getInt(rset, 5);
        CategoryImpl category;
        if (xml != null && xml.length() > 10)
        {
            CategoryReader categoryReader = (CategoryReader) context.lookup(PreferenceReader.READERMAP).get(Category.class);
            categoryReader.setReadOnlyThisCategory(true);
            processXML(categoryReader, xml);
            category = categoryReader.getCurrentCategory();
            //cache.remove( category );
        }
        else
        {
            getLogger().warn("Category has empty xml field. Ignoring.");
            return;
        }
        final LocalDateTime lastChanged = getTimestampOrNow(rset, 6);
        category.setLastChanged(lastChanged);
        category.setId(id);
        put(category);

        orderMap.put(category, order);
        // parentId can also be null
        categoriesWithoutParent.put(category, parentId);
    }

    @Override
    public void loadAll() throws RaplaException, SQLException
    {
        categoriesWithoutParent.clear();
        super.loadAll();
        CategoryReader categoryReader = (CategoryReader) context.lookup(PreferenceReader.READERMAP).get(Category.class);
        Category superCategory = categoryReader.getSuperCategory();
        // then we rebuild the hierarchy
        Iterator<Map.Entry<Category, ReferenceInfo<Category>>> it = categoriesWithoutParent.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<Category, ReferenceInfo<Category>> entry = it.next();
            ReferenceInfo<Category> parentId = entry.getValue();
            Category category = entry.getKey();
            Category parent;
            Assert.notNull(category);
            if (parentId != null)
            {
                parent = entityStore.resolve(parentId);
            }
            else
            {
                parent = superCategory;
            }
            Assert.notNull(parent);
            parent.addCategory(category);
        }
        for (Category category : categoriesWithoutParent.keySet())
        {
            keyAndPathResolver.addCategory(category);
        }
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        CategoryImpl superCategory = cache.getSuperCategory();
        insert(CategoryImpl.getRecursive(superCategory));
    }

}

class AllocatableStorage extends RaplaTypeStorage<Allocatable>
{
    Map<ReferenceInfo<? extends Entity>, Classification> classificationMap = new HashMap<>();
    Map<ReferenceInfo<? extends Entity>, Allocatable> allocatableMap = new HashMap<>();
    AttributeValueStorage<Allocatable> resourceAttributeStorage;
    PermissionStorage<Allocatable> permissionStorage;

    public AllocatableStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, Allocatable.class, "RAPLA_RESOURCE",
                new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "TYPE_KEY VARCHAR(255) NOT NULL", "OWNER_ID VARCHAR(255)", "CREATION_TIME TIMESTAMP",
                        "LAST_CHANGED TIMESTAMP KEY", "LAST_CHANGED_BY VARCHAR(255) DEFAULT NULL" });
        resourceAttributeStorage = new AttributeValueStorage<>(context, "RESOURCE_ATTRIBUTE_VALUE", "RESOURCE_ID", classificationMap,
                allocatableMap);
        permissionStorage = new PermissionStorage<>(context, "RESOURCE", allocatableMap, Allocatable.class);
        addSubStorage(resourceAttributeStorage);
        addSubStorage(permissionStorage);
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndDrop(schema, "DELETED");
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        insert(cache.getAllocatables());
    }

    @Override
    protected int write(PreparedStatement stmt, Allocatable entity) throws SQLException, RaplaException
    {
        AllocatableImpl allocatable = (AllocatableImpl) entity;
        String typeKey = allocatable.getClassification().getType().getKey();
        setId(stmt, 1, entity);
        setString(stmt, 2, typeKey);
        org.rapla.entities.Timestamp timestamp = allocatable;
        setId(stmt, 3, allocatable.getOwnerRef());
        setTimestamp(stmt, 4, timestamp.getCreateDate());
        setTimestamp(stmt, 5, timestamp.getLastChanged());
        setId(stmt, 6, timestamp.getLastChangedBy());
        stmt.addBatch();
        return 1;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        ReferenceInfo<Allocatable> id = readId(rset, 1, Allocatable.class);
        String typeKey = getString(rset, 2, null);
        final LocalDateTime createDate = getTimestampOrNow(rset, 4);
        final LocalDateTime lastChanged = getTimestampOrNow(rset, 5);

        AllocatableImpl allocatable = new AllocatableImpl(createDate, lastChanged);
        allocatable.setLastChangedBy(resolveFromId(rset, 6, User.class, false));
        allocatable.setId(id);
        allocatable.setResolver(entityStore);
        DynamicType type = null;
        if (typeKey != null)
        {
            type = getDynamicType(typeKey);
        }
        if (type == null)
        {
            getLogger().error("Allocatable with id " + id + " has an unknown type " + typeKey + ". Try ignoring it");
            return;
        }
        {
            final User user = resolveFromId(rset, 3, User.class,true);
            allocatable.setOwner(user);
        }
        Classification classification = ((DynamicTypeImpl) type).newClassificationWithoutCheck(false);
        allocatable.setClassification(classification);
        classificationMap.put(id, classification);
        allocatableMap.put(id, allocatable);
        put(allocatable);
    }

    @Override
    public void loadAll() throws RaplaException, SQLException
    {
        classificationMap.clear();
        super.loadAll();
    }
}

class ReservationStorage extends RaplaTypeStorage<Reservation>
{
    Map<ReferenceInfo<? extends Entity>, Classification> classificationMap = new HashMap<>();
    Map<ReferenceInfo<? extends Entity>, Reservation> reservationMap = new HashMap<>();
    AttributeValueStorage<Reservation> attributeValueStorage;
    // appointmentstorage is not a sub store but a delegate
    AppointmentStorage appointmentStorage;
    PermissionStorage<Reservation> permissionStorage;

    public ReservationStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, Reservation.class, "EVENT",
                new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "TYPE_KEY VARCHAR(255) NOT NULL", "OWNER_ID VARCHAR(255) NOT NULL",
                        "CREATION_TIME TIMESTAMP", "LAST_CHANGED TIMESTAMP KEY", "LAST_CHANGED_BY VARCHAR(255) DEFAULT NULL" });
        attributeValueStorage = new AttributeValueStorage<>(context, "EVENT_ATTRIBUTE_VALUE", "EVENT_ID", classificationMap, reservationMap);
        addSubStorage(attributeValueStorage);
        permissionStorage = new PermissionStorage<>(context, "EVENT", reservationMap, Reservation.class);
        addSubStorage(permissionStorage);
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndDrop(schema, "DELETED");
    }

    public void setAppointmentStorage(AppointmentStorage appointmentStorage)
    {
        this.appointmentStorage = appointmentStorage;
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        insert(cache.getReservations());
    }

    @Override
    public void save(Iterable<Reservation> entities) throws RaplaException, SQLException
    {
        super.save(entities);
        Collection<Appointment> appointments = new ArrayList<>();
        for (Reservation r : entities)
        {
            appointments.addAll(Arrays.asList(r.getAppointments()));
        }
        appointmentStorage.insert(appointments);
    }

    @Override
    public void setConnection(Connection con, LocalDateTime connectionTimestamp) throws SQLException
    {
        super.setConnection(con, connectionTimestamp);
        appointmentStorage.setConnection(con, connectionTimestamp);
    }

    @Override
    protected int write(PreparedStatement stmt, Reservation event) throws SQLException, RaplaException
    {
        String typeKey = event.getClassification().getType().getKey();
        setId(stmt, 1, event);
        setString(stmt, 2, typeKey);
        setId(stmt, 3, event.getOwnerRef());
        org.rapla.entities.Timestamp timestamp = event;
        setTimestamp(stmt, 4, timestamp.getCreateDate());
        setTimestamp(stmt, 5, timestamp.getLastChanged());
        setId(stmt, 6, timestamp.getLastChangedBy());
        stmt.addBatch();
        return 1;
    }

    @Override
    protected void updateSubstores(String foreignId) throws SQLException, RaplaException
    {
        super.updateSubstores(foreignId);
        appointmentStorage.updateWithForeignId(foreignId);
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        final LocalDateTime createDate = getTimestampOrNow(rset, 4);
        final LocalDateTime lastChanged = getTimestampOrNow(rset, 5);
        ReservationImpl event = new ReservationImpl(createDate, lastChanged);
        ReferenceInfo<Reservation> id = readId(rset, 1, Reservation.class);
        event.setId(id);
        event.setResolver(entityStore);
        String typeKey = getString(rset, 2, null);
        DynamicType type = null;
        if (typeKey != null)
        {
            type = getDynamicType(typeKey);
        }
        if (type == null)
        {
            getLogger().error("Reservation with id " + id + " has an unknown type " + typeKey + ". Try ignoring it");
            return;
        }
        {
            User user = resolveFromId(rset, 3, User.class, true);
            if (user == null)
            {
                return;
            }
            event.setOwner(user);
        }
        {
            User user = resolveFromId(rset, 6, User.class, false);
            event.setLastChangedBy(user);
        }

        Classification classification = ((DynamicTypeImpl) type).newClassificationWithoutCheck(false);
        event.setClassification(classification);
        classificationMap.put(id, classification);
        reservationMap.put(id, event);
        put(event);
    }

    @Override
    public void loadAll() throws RaplaException, SQLException
    {
        classificationMap.clear();
        super.loadAll();
    }

    @Override
    protected void deleteFromSubStores(Set<String> ids) throws SQLException, RaplaException
    {
        super.deleteFromSubStores(ids);
        appointmentStorage.deleteAppointments(ids);
    }

}

class AttributeValueStorage<T extends Entity<T>> extends EntityStorage<T> implements SubStorage<T>
{
    Map<ReferenceInfo<? extends Entity>, Classification> classificationMap;
    Map<ReferenceInfo<? extends Entity>, ? extends Annotatable> annotableMap;
    final String foreignKeyName;
    // TODO Write conversion script to update all old entries to new entries
    public final static String OLD_ANNOTATION_PREFIX = "annotation:";
    public final static String ANNOTATION_PREFIX = "rapla:";
    private KeyAndPathResolver keyAndPathResolver;

    public AttributeValueStorage(RaplaXMLContext context, String tablename, String foreignKeyName,
            Map<ReferenceInfo<? extends Entity>, Classification> classificationMap, Map<ReferenceInfo<? extends Entity>, ? extends Annotatable> annotableMap)
            throws RaplaException
    {
        super(context, tablename,
                new String[] { foreignKeyName + " VARCHAR(255) NOT NULL KEY", "ATTRIBUTE_KEY VARCHAR(255)", "ATTRIBUTE_VALUE VARCHAR(15000)" });
        this.foreignKeyName = foreignKeyName;
        this.classificationMap = classificationMap;
        this.annotableMap = annotableMap;
        if (this.entityStore != null)
        {
            this.keyAndPathResolver = context.lookup(KeyAndPathResolver.class);
        }
    }

    @Override
    protected int write(PreparedStatement stmt, T classifiable) throws EntityNotFoundException, SQLException
    {
        Classification classification = ((Classifiable) classifiable).getClassification();
        Attribute[] attributes = classification.getAttributes();
        int count = 0;
        for (int i = 0; i < attributes.length; i++)
        {
            Attribute attribute = attributes[i];
            Collection<Object> values = classification.getValues(attribute);
            for (Object value : values)
            {
                String valueAsString;
                if (value instanceof Category || value instanceof Allocatable)
                {
                    Entity casted = (Entity) value;
                    valueAsString = casted.getId();
                }
                else
                {
                    valueAsString = AttributeImpl.attributeValueToString(attribute, value, true);
                }
                setId(stmt, 1, classifiable);
                setString(stmt, 2, attribute.getKey());
                setString(stmt, 3, valueAsString);
                stmt.addBatch();
                count++;
            }
        }
        Annotatable annotatable = (Annotatable) classifiable;
        for (String key : annotatable.getAnnotationKeys())
        {
            String valueAsString = annotatable.getAnnotation(key);
            setId(stmt, 1, classifiable);
            setString(stmt, 2, ANNOTATION_PREFIX + key);
            setString(stmt, 3, valueAsString);
            stmt.addBatch();
            count++;
        }
        return count;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        Class<? extends Entity> idClass = foreignKeyName.contains("RESOURCE") ? Allocatable.class : Reservation.class;
        ReferenceInfo<?> classifiableId = readId(rset, 1, idClass);
        String attributekey = rset.getString(2);
        boolean annotationPrefix = attributekey.startsWith(ANNOTATION_PREFIX);
        boolean oldAnnotationPrefix = attributekey.startsWith(OLD_ANNOTATION_PREFIX);
        if (annotationPrefix || oldAnnotationPrefix)
        {
            String annotationKey = attributekey.substring(annotationPrefix ? ANNOTATION_PREFIX.length() : OLD_ANNOTATION_PREFIX.length());
            Annotatable annotatable = annotableMap.get(classifiableId);
            if (annotatable != null)
            {
                String valueAsString = rset.getString(3);
                if (rset.wasNull() || valueAsString == null)
                {
                    annotatable.setAnnotation(annotationKey, null);
                }
                else
                {
                    annotatable.setAnnotation(annotationKey, valueAsString);
                }
            }
            else
            {
                getLogger().warn("No resource or reservation found for the id " + classifiableId + " ignoring.");
            }
        }
        else
        {
            ClassificationImpl classification = (ClassificationImpl) classificationMap.get(classifiableId);
            if (classification == null)
            {
                getLogger().warn("No resource or reservation found for the id " + classifiableId + " ignoring.");
                return;
            }
            Attribute attribute = classification.getType().getAttribute(attributekey);
            if (attribute == null)
            {
                getLogger().error("DynamicType '" + classification.getType() + "' doesnt have an attribute with the key " + attributekey
                        + " Current allocatable/reservation Id " + classifiableId + ". Ignoring attribute.");
                return;
            }
            String valueAsString = rset.getString(3);
            if (valueAsString != null)
            {
                try
                {
                    final Class<? extends Entity> refType = attribute.getRefType();
                    if (refType != null)
                    {
                        ReferenceInfo id = AttributeImpl.parseRefType(attribute, valueAsString, keyAndPathResolver);
                        if (id != null)
                        {
                            classification.addRefValue(attribute, id);
                        }
                    }
                    else
                    {
                        Object value = AttributeImpl.parseAttributeValueWithoutRef(attribute, valueAsString);
                        if (value != null)
                        {
                            classification.addValue(attribute, value);
                        }
                    }
                }
                catch (RaplaException e)
                {
                    getLogger().error("DynamicType '" + classification.getType() + "' doesnt have a valid attribute with the key " + attributekey
                            + " Current allocatable/reservation Id " + classifiableId + ". Ignoring attribute.");
                }
            }
        }
    }
}

class PermissionStorage<T extends EntityPermissionContainer<T>> extends EntityStorage<T> implements SubStorage<T>
{
    Map<ReferenceInfo<? extends Entity>, T> referenceMap;
    private final Class<? extends Entity> parentStoreClass;

    public PermissionStorage(RaplaXMLContext context, String type, Map<ReferenceInfo<? extends Entity>, T> idMap, Class<? extends Entity> parentStoreClass)
            throws RaplaException
    {
        super(context, type + "_PERMISSION",
                new String[] { type + "_ID VARCHAR(255) NOT NULL KEY", "USER_ID VARCHAR(255)", "GROUP_ID VARCHAR(255)", "ACCESS_LEVEL INTEGER NOT NULL",
                        "MIN_ADVANCE INTEGER", "MAX_ADVANCE INTEGER", "START_DATE DATETIME", "END_DATE DATETIME" });
        this.referenceMap = idMap;
        this.parentStoreClass = parentStoreClass;
    }

    protected int write(PreparedStatement stmt, EntityPermissionContainer container) throws SQLException, RaplaException
    {
        int count = 0;
        Iterable<Permission> permissionList = container.getPermissionList();
        for (Permission s : permissionList)
        {
            setId(stmt, 1, container);
            setId(stmt, 2, s.getUser());
            setId(stmt, 3, s.getGroup());
            @SuppressWarnings("deprecation")
            int numericLevel = s.getAccessLevel().getNumericLevel();
            setInt(stmt, 4, numericLevel);
            setInt(stmt, 5, s.getMinAdvance());
            setInt(stmt, 6, s.getMaxAdvance());
            setDate(stmt, 7, s.getStart());
            setDate(stmt, 8, s.getEnd());
            stmt.addBatch();
            count++;
        }
        return count;
    }

    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        Class<? extends Entity> clazz = parentStoreClass;
        ReferenceInfo<? extends Entity> referenceIdInt = readId(rset, 1, clazz);
        PermissionContainer allocatable = referenceMap.get(referenceIdInt);
        if (allocatable == null)
        {
            getLogger().warn("Could not find resource object with id " + referenceIdInt + " for permission. Maybe the resource was deleted from the database.");
            return;
        }
        PermissionImpl permission = new PermissionImpl();
        permission.setUser(resolveFromId(rset, 2, User.class, true));
        permission.setGroup(resolveFromId(rset, 3, Category.class, true));
        Integer accessLevel = getInt(rset, 4);
        if (accessLevel != null) {
            AccessLevel enumLevel = AccessLevel.find(accessLevel);
            permission.setAccessLevel(enumLevel);
        } else {
            getLogger().warn("Permission with id " + referenceIdInt + " has no access level. Ignoring.");
            return;
        }
        permission.setMinAdvance(getInt(rset, 5));
        permission.setMaxAdvance(getInt(rset, 6));
        permission.setStart(getDate(rset, 7));
        permission.setEnd(getDate(rset, 8));
        // We need to add the permission at the end to ensure its unique. Permissions are stored in a set and duplicates are removed during the add method
        allocatable.addPermission(permission);
    }

}

// TODO is it possible to add this as substorage
class AppointmentStorage extends RaplaTypeStorage<Appointment>
{
    AppointmentExceptionStorage appointmentExceptionStorage;
    AllocationStorage allocationStorage;

    public AppointmentStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, Appointment.class, "APPOINTMENT",
                new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "EVENT_ID VARCHAR(255) NOT NULL KEY", "APPOINTMENT_START DATETIME NOT NULL",
                        "APPOINTMENT_END DATETIME NOT NULL", "REPETITION_TYPE VARCHAR(255)", "REPETITION_NUMBER INTEGER", "REPETITION_END DATETIME",
                        "REPETITION_INTERVAL INTEGER" });
        setForeignId("EVENT_ID");
        appointmentExceptionStorage = new AppointmentExceptionStorage(context);
        allocationStorage = new AllocationStorage(context);
        addSubStorage(appointmentExceptionStorage);
        addSubStorage(allocationStorage);
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
    }

    void deleteAppointments(Collection<String> reservationIds) throws SQLException, RaplaException
    {
        // look for all appointment ids, as the sub storages must be deleted with appointment id
        final Set<String> ids = new HashSet<>();
        final String sql = "SELECT ID FROM APPOINTMENT WHERE EVENT_ID=?";
        for (String eventId : reservationIds)
        {
            ResultSet rset = null;
            try (final PreparedStatement stmt = con.prepareStatement(sql))
            {
                setString(stmt, 1, eventId);
                rset = stmt.executeQuery();
                while (rset.next())
                {
                    String appointmentId = readId(rset, 1, Appointment.class).getId();
                    ids.add(appointmentId);
                }
            }
            finally
            {
                if (rset != null)
                    rset.close();
            }
        }
        // and delete them
        deleteIds(reservationIds);
        deleteFromSubStores(ids);
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        Collection<Reservation> reservations = cache.getReservations();
        Collection<Appointment> appointments = new LinkedHashSet<>();
        for (Reservation r : reservations)
        {
            appointments.addAll(Arrays.asList(r.getAppointments()));
        }
        insert(appointments);
    }

    @Override
    protected int write(PreparedStatement stmt, Appointment appointment) throws SQLException, RaplaException
    {
        setId(stmt, 1, appointment);
        setId(stmt, 2, appointment.getReservation());
        setDate(stmt, 3, appointment.getStart());
        setDate(stmt, 4, appointment.getEnd());
        Repeating repeating = appointment.getRepeating();
        if (repeating == null)
        {
            setString(stmt, 5, null);
            setInt(stmt, 6, null);
            setDate(stmt, 7, null);
            setInt(stmt, 8, null);
        }
        else
        {
            final RepeatingType repeatingType = repeating.getType();
            String repeatingTypeAsString = repeatingType.toString();
            if (repeatingType == RepeatingType.WEEKLY && repeating.hasDifferentWeekdaySelectedInRepeating())
            {
                final Set<Integer> weekdays = repeating.getWeekdays();
                StringBuilder builder = new StringBuilder();
                for (Integer weekday : weekdays)
                {
                    if (builder.length() > 0)
                    {
                        builder.append(",");
                    }
                    builder.append(weekday);
                }
                repeatingTypeAsString += ":" + builder;
            }
            setString(stmt, 5, repeatingTypeAsString);
            int number = repeating.getNumber();
            final boolean fixedNumber = repeating.isFixedNumber();
            setInt(stmt, 6, fixedNumber ? number : null);
            setDate(stmt, 7, fixedNumber ? null : repeating.getEnd());
            setInt(stmt, 8, repeating.getInterval());
        }
        stmt.addBatch();
        return 1;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        ReferenceInfo<Appointment> id = readId(rset, 1, Appointment.class);
        Reservation reservation = resolveFromId(rset, 2, Reservation.class, true);
        if (reservation == null)
        {
            return;
        }
        LocalDateTime start = getDate(rset, 3);
        LocalDateTime end = getDate(rset, 4);
        boolean wholeDayAppointment = start.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) && end.toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
        AppointmentImpl appointment = new AppointmentImpl(start, end);
        appointment.setId(id);
        appointment.setWholeDays(wholeDayAppointment);
        reservation.addAppointment(appointment);
        String repeatingTypeAsString = getString(rset, 5, null);
        if (repeatingTypeAsString != null)
        {
            appointment.setRepeatingEnabled(true);
            Repeating repeating = appointment.getRepeating();
            final String prefix = "weekly:";
            if (repeatingTypeAsString.startsWith(prefix))
            {
                try
                {
                    Set<Integer> weekdays;

                    String[] weekdayStrings = repeatingTypeAsString.substring(prefix.length()).split(",");
                    weekdays = new TreeSet<>();
                    for (String weekday : weekdayStrings)
                    {
                        try
                        {
                            weekdays.add(Integer.parseInt(weekday));
                        }
                        catch (Exception ex)
                        {
                            getLogger().error("Can't parse " + weekday + " for appointment with id " + id);
                        }
                    }
                    repeating.setWeekdays(weekdays);
                }
                catch (Exception ex)
                {
                    getLogger().error("Can't parse " + repeatingTypeAsString  + " for appointment with id " + id);
                }
                repeatingTypeAsString = "weekly";
            }
            final RepeatingType repeatingType = RepeatingType.findForString(repeatingTypeAsString);
            if (repeatingType == null)
            {
                throw new RaplaException("Unknown repeatingType " + repeatingType + " for appointment with id " + id);
            }

            repeating.setType(repeatingType);
            LocalDateTime repeatingEnd = getDate(rset, 7);
            if (repeatingEnd != null)
            {
                repeating.setEnd(repeatingEnd);
            }
            else
            {
                Integer number = getInt(rset, 6);
                if (number != null)
                {
                    repeating.setNumber(number);
                }
                else
                {
                    repeating.setEnd(null);
                }
            }

            Integer interval = getInt(rset, 8);
            if (interval != null)
                repeating.setInterval(interval);
        }
        put(appointment);
    }

}

class AllocationStorage extends EntityStorage<Appointment> implements SubStorage<Appointment>
{

    public AllocationStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, "ALLOCATION", new String[] { "APPOINTMENT_ID VARCHAR(255) NOT NULL KEY", "RESOURCE_ID VARCHAR(255) NOT NULL", "PARENT_ORDER INTEGER","IS_RESTRICTION INTEGER","STATUS INTEGER" });
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndAdd(schema, "IS_RESTRICTION");
        //Allocation status is to be added in future versions
        checkAndAdd(schema, "STATUS");
    }

    @Override
    protected int write(PreparedStatement stmt, Appointment appointment) throws SQLException, RaplaException
    {
        Reservation event = appointment.getReservation();
        int count = 0;
        final List<Allocatable> allocatablesFor = event.getAllocatablesFor(appointment).collect(Collectors.toList());
        for (Allocatable allocatable : allocatablesFor)
        {
            setId(stmt, 1, appointment);
            setId(stmt, 2, allocatable);
            stmt.setObject(3, null);
            final Appointment[] restriction = event.getRestriction(allocatable);
            boolean isRestriction = restriction != null && restriction.length > 0;
            stmt.setInt(4, isRestriction ? 1:0);
            RequestStatus requestStatus = event.getRequestStatus(allocatable);
            stmt.setInt(5, requestStatus != null ? requestStatus.ordinal() +1 : 0 );
            stmt.addBatch();
            count++;
        }
        return count;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        Appointment appointment = resolveFromId(rset, 1, Appointment.class, true);
        if (appointment == null)
        {
            return;
        }
        ReservationImpl event = (ReservationImpl) appointment.getReservation();
        ReferenceInfo<Allocatable> allocatableReferenceInfo = readId(rset, 2, Allocatable.class);
        boolean isRestriction = rset.getInt(4) == 1;
        if (allocatableReferenceInfo == null)
        {
            return;
        }
        if (!event.hasAllocatedRef(allocatableReferenceInfo))
        {
            event.addId("resources", allocatableReferenceInfo.getId());
        }
        List<String> appointments = event.getRestrictionPrivate(allocatableReferenceInfo.getId());
        List<String> newRestrictions = new ArrayList<>( appointments);
        newRestrictions.add(appointment.getId() );
        if (event.getAppointmentList().size() > newRestrictions.size() || isRestriction)
        {
            event.setRestrictionForReference(allocatableReferenceInfo, newRestrictions);
        }
        else
        {
            event.setRestrictionForReference(allocatableReferenceInfo, Collections.emptyList());
        }
        int requestStatusInt = rset.getInt(5);
        if ( requestStatusInt > 0)
        {
            RequestStatus requestStatus = RequestStatus.values()[requestStatusInt -1];
            event.setRequestStatusForId(allocatableReferenceInfo.getId(), requestStatus);
        }
    }

}

class AppointmentExceptionStorage extends EntityStorage<Appointment> implements SubStorage<Appointment>
{
    public AppointmentExceptionStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, "APPOINTMENT_EXCEPTION", new String[] { "APPOINTMENT_ID VARCHAR(255) NOT NULL KEY", "EXCEPTION_DATE DATETIME NOT NULL" });
    }

    @Override
    protected int write(PreparedStatement stmt, Appointment entity) throws SQLException, RaplaException
    {
        Repeating repeating = entity.getRepeating();
        int count = 0;
        if (repeating == null)
        {
            return count;
        }
        for (java.time.LocalDateTime exception : repeating.getExceptions())
        {
            setId(stmt, 1, entity);
            setDate(stmt, 2, exception);
            stmt.addBatch();
            count++;
        }
        return count;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        Appointment appointment = resolveFromId(rset, 1, Appointment.class, true);
        if (appointment == null)
        {
            return;
        }
        Repeating repeating = appointment.getRepeating();
        if (repeating != null)
        {
            LocalDateTime date = getDate(rset, 2);
            repeating.addException(date);
        }
    }

}

class DynamicTypeStorage extends RaplaTypeStorage<DynamicType>
{

    public DynamicTypeStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, DynamicType.class, "DYNAMIC_TYPE",
                new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "TYPE_KEY VARCHAR(255) NOT NULL", "DEFINITION TEXT NOT NULL",
                        "LAST_CHANGED TIMESTAMP KEY",
                        "DELETED TIMESTAMP KEY" });//, "CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY INTEGER DEFAULT NULL"});

    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndAdd(schema, "LAST_CHANGED");
        checkAndAdd(schema, "DELETED");
    }

    @Override
    protected int write(PreparedStatement stmt, DynamicType type) throws SQLException, RaplaException
    {
        if (((DynamicTypeImpl) type).isInternal())
        {
            return 0;
        }
        setId(stmt, 1, type);
        setString(stmt, 2, type.getKey());
        RaplaXMLWriter typeWriter = context.lookup(PreferenceWriter.WRITERMAP).get(DynamicType.class);
        setText(stmt, 3, getXML(typeWriter, type));
        setTimestamp(stmt, 4, type.getLastChanged());
        setTimestamp(stmt, 5, null);
        //    	setDate(stmt, 5,timestamp.getLastChanged() );
        //    	setId( stmt,6,timestamp.getLastChangedBy() );
        stmt.addBatch();
        return 1;
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        Collection<DynamicType> dynamicTypes = new ArrayList<>(cache.getDynamicTypes());
        Iterator<DynamicType> it = dynamicTypes.iterator();
        while (it.hasNext())
        {
            if (((DynamicTypeImpl) it.next()).isInternal())
            {
                it.remove();
            }
        }
        insert(dynamicTypes);
    }

    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        @SuppressWarnings("unused")
        ReferenceInfo<DynamicType> id = readId(rset, 1, DynamicType.class);
        String xml = getText(rset, 3);
        DynamicTypeReader typeReader = (DynamicTypeReader) context.lookup(PreferenceReader.READERMAP).get(DynamicType.class);
        processXML(typeReader, xml);
        //    	final LocalDateTime createDate = getDate( rset, 4);
        final LocalDateTime lastChanged = getTimestampOrNow(rset, 4);
        final Entity entity = typeReader.getStore().resolve(id);
        ((ModifiableTimestamp) entity).setLastChanged(lastChanged);
        //    	AllocatableImpl allocatable = new AllocatableImpl(createDate, lastChanged);
        //    	allocatable.setLastChangedBy( resolveFromId(rset, 6, User.class) );

        //  	DynamicType type = reader.getStore().resolve(id, DynamicType.class);
        //    	idMap.put( id, type);
    }

}

class PreferenceStorage extends RaplaTypeStorage<Preferences>
{
    private final String updateSql;

    public PreferenceStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, Preferences.class, "PREFERENCE",
                new String[] { "USER_ID VARCHAR(255) KEY", "ROLE VARCHAR(255) NOT NULL", "STRING_VALUE VARCHAR(10000)", "XML_VALUE TEXT",
                        "LAST_CHANGED TIMESTAMP KEY" }, false);
        this.updateSql = "SELECT USER_ID, ROLE, STRING_VALUE, XML_VALUE, LAST_CHANGED FROM PREFERENCE WHERE LAST_CHANGED > ?";
    }

    public List<PreferencePatch> getPatches(LocalDateTime lastUpdated) throws SQLException, RaplaException
    {
        Map<String, PreferencePatch> userIdToPatch = new HashMap<>();
        final ArrayList<PreferencePatch> patches = new ArrayList<>();
        try (final PreparedStatement stmt = con.prepareStatement(updateSql))
        {
            stmt.setTimestamp(1, new java.sql.Timestamp(DateTools.toMilli(lastUpdated)));
            final ResultSet result = stmt.executeQuery();
            while (result.next())
            {
                final String userId = result.getString(1);
                final String role = result.getString(2);
                PreferencePatch preferencePatch = userIdToPatch.get(userId);
                if (preferencePatch == null)
                {
                    preferencePatch = new PreferencePatch();
                    patches.add(preferencePatch);
                    preferencePatch.setUserId(userId);
                    userIdToPatch.put(userId, preferencePatch);
                }
                final String entityAsString = getString(result, 3, null);
                if (entityAsString == null)
                {
                    final String entityXml = getText(result, 4);
                    if (entityXml != null && entityXml.length() > 0)
                    {
                        PreferenceReader preferenceReader = (PreferenceReader) context.lookup(PreferenceReader.READERMAP).get(Preferences.class);
                        processXML(preferenceReader, entityXml);
                        RaplaObject type = preferenceReader.getChildType();
                        preferencePatch.putPrivate(role, type);
                    }
                }
                else
                {
                    preferencePatch.putPrivate(role, entityAsString);
                }
                final LocalDateTime lastChanged = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(result.getTimestamp(5).getTime()), java.time.ZoneOffset.UTC);
                preferencePatch.setLastChanged(lastChanged);
            }
        }
        return patches;
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndAdd(schema, "LAST_CHANGED");
        checkAndDrop(schema, "DELETED");
    }

    public void storePatches(List<PreferencePatch> preferencePatches) throws RaplaException, SQLException
    {
        for (PreferencePatch patch : preferencePatches)
        {
            ReferenceInfo<User> userId = patch.getUserRef();
            PreparedStatement stmt = null;
            try
            {

                final String deleteSqlWithRole;
                int count = 0;
                if (userId != null)
                {
                    deleteSqlWithRole = deleteSql + " and role=?";
                    stmt = con.prepareStatement(deleteSqlWithRole);
                    for (String role : patch.getRemovedEntries())
                    {
                        setId(stmt, 1, userId);
                        //setTimestamp(stmt, 2, patch.getLastChanged());
                        setString(stmt, 2, role);
                        stmt.addBatch();
                        count++;
                    }
                    for (String role : patch.keySet())
                    {
                        setId(stmt, 1, userId);
                        //setTimestamp(stmt, 2, patch.getLastChanged());
                        setString(stmt, 2, role);
                        stmt.addBatch();
                        count++;
                    }
                }
                else
                {
                    deleteSqlWithRole = "delete from " + getTableName() + " where user_id IS null and role=?";
                    stmt = con.prepareStatement(deleteSqlWithRole);
                    for (String role : patch.getRemovedEntries())
                    {
                        //setTimestamp( stmt,1, patch.getLastChanged());
                        setString(stmt, 1, role);
                        stmt.addBatch();
                        count++;
                    }
                    for (String role : patch.keySet())
                    {
                        //setTimestamp( stmt,1, patch.getLastChanged());
                        setString(stmt, 1, role);
                        stmt.addBatch();
                        count++;
                    }
                }

                if (count > 0)
                {
                    stmt.executeBatch();
                }
            }
            finally
            {
                if (stmt != null)
                    stmt.close();
            }
        }

        PreparedStatement stmt = null;
        try
        {
            stmt = con.prepareStatement(insertSql);
            int count = 0;
            LocalDateTime lastChanged = getConnectionTimestamp();
            for (PreferencePatch patch : preferencePatches)
            {
                String userId = patch.getUserId();
                patch.setLastChanged(lastChanged);
                for (String role : patch.keySet())
                {
                    Object entry = patch.get(role);
                    insertEntry(stmt, userId, role, entry, lastChanged);
                    count++;
                }
            }

            if (count > 0)
            {
                stmt.executeBatch();
            }
        }
        catch (SQLException ex)
        {
            throw ex;
        }
        finally
        {
            if (stmt != null)
                stmt.close();
        }
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        List<Preferences> preferences = new ArrayList<>();
        {
            PreferencesImpl systemPrefs = cache.getPreferencesForUserId(null);
            if (systemPrefs != null)
            {
                preferences.add(systemPrefs);
            }
        }
        Collection<User> users = cache.getUsers();
        for (User user : users)
        {
            String userId = user.getId();
            PreferencesImpl userPrefs = cache.getPreferencesForUserId(userId);
            if (userPrefs != null)
            {
                preferences.add(userPrefs);
            }
        }
        insert(preferences);
    }

    @Override
    protected int write(PreparedStatement stmt, Preferences entity) throws SQLException, RaplaException
    {
        PreferencesImpl preferences = (PreferencesImpl) entity;
        ReferenceInfo<User> userId = preferences.getOwnerRef();
        int count = 0;
        for (String role : preferences.getPreferenceEntries())
        {
            Object entry = preferences.getEntry(role);
            final LocalDateTime lastChanged = getConnectionTimestamp();
            insertEntry(stmt, userId != null ? userId.getId() : null, role, entry, lastChanged);
            count++;
        }

        return count;
    }

    private void insertEntry(PreparedStatement stmt, String userId, String role, Object entry, LocalDateTime lastChanged) throws SQLException, RaplaException
    {
        setString(stmt, 1, userId);
        setString(stmt, 2, role);
        String xml;
        String entryString;
        if (entry instanceof String)
        {
            entryString = (String) entry;
            xml = null;
        }
        else
        {
            //System.out.println("Role " + role + " CHILDREN " + conf.getChildren().length);
            entryString = null;
            final RaplaObject raplaObject = (RaplaObject) entry;
            final Class<? extends RaplaObject> raplaType = raplaObject.getTypeClass();
            final RaplaXMLWriter raplaXMLWriter = context.lookup(PreferenceWriter.WRITERMAP).get(raplaType);
            xml = getXML(raplaXMLWriter, raplaObject);
        }
        setString(stmt, 3, entryString);
        setText(stmt, 4, xml);
        setTimestamp(stmt, 5, lastChanged);
        stmt.addBatch();
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        //findPreferences
        //check if value set
        //  yes read value
        //  no read xml

        ReferenceInfo<User> userRef = readId(rset, 1, User.class, true);
        User owner;
        String userId = userRef != null ? userRef.getId() : null;
        ReferenceInfo<Preferences> preferenceId = PreferencesImpl.getPreferenceIdFromUser(userId);
        if (preferenceId.isSame(Preferences.SYSTEM_PREFERENCES_ID))
        {
            owner = null;
        }
        else
        {
            User user = entityStore.tryResolve(userRef);
            if (user != null)
            {
                owner = user;
            }
            else
            {
                getLogger().warn("User with id  " + userRef + " not found ingnoring preference entry.");
                return;
            }
        }

        String configRole = getString(rset, 2, null);

        if (configRole == null)
        {
            getLogger().warn("Configuration role for " + preferenceId + " is null. Ignoring preference entry.");
            return;
        }
        String value = getString(rset, 3, null);
        //        if (PreferencesImpl.isServerEntry(configRole))
        //        {
        //        	entityStore.putServerPreferences(owner,configRole, value);
        //        	return;
        //        }
        final LocalDateTime lastUpdateDate = getTimestampOrNow(rset, 5);

        PreferencesImpl preferences = preferenceId != null ? (PreferencesImpl) entityStore.tryResolve(preferenceId) : null;
        if (preferences == null)
        {
            preferences = new PreferencesImpl(lastUpdateDate, lastUpdateDate);
            preferences.setId(preferenceId.getId());
            preferences.setOwner(owner);
            put(preferences);
        }

        if (value != null)
        {
            preferences.putEntryPrivate(configRole, value);
        }
        else
        {
            String xml = getText(rset, 4);
            if (xml != null && xml.length() > 0)
            {
                PreferenceReader preferenceReader = (PreferenceReader) context.lookup(PreferenceReader.READERMAP).get(Preferences.class);
                processXML(preferenceReader, xml);
                RaplaObject type = preferenceReader.getChildType();
                preferences.putEntryPrivate(configRole, type);
            }
        }
        final java.time.LocalDateTime lastChanged = preferences.getLastChanged();
        // update last changed
        if (lastChanged.isBefore(lastUpdateDate))
        {
            preferences.setLastChanged(lastUpdateDate);
        }
    }

    @Override
    public void deleteEntities(Iterable<ReferenceInfo<Preferences>> entities) throws SQLException
    {
        PreparedStatement stmt = null;
        boolean deleteNullUserPreference = false;
        try
        {
            stmt = con.prepareStatement(deleteSql);
            boolean empty = true;
            for (ReferenceInfo<Preferences> referenceInfo : entities)
            {
                Preferences preferences = cache.tryResolve(referenceInfo);
                ReferenceInfo<User> userId = preferences.getOwnerRef();
                if (userId == null)
                {
                    deleteNullUserPreference = true;
                }
                empty = false;
                setId(stmt, 1, userId);
                if ( checkLastChanged)
                {
                    final LocalDateTime lastChanged = preferences.getLastChanged();
                    setTimestamp(stmt, 2, lastChanged);
                }
                stmt.addBatch();
            }
            if (!empty)
            {
                stmt.executeBatch();
            }
        }
        finally
        {
            if (stmt != null)
                stmt.close();
        }
        if (deleteNullUserPreference)
        {
            PreparedStatement deleteNullStmt = con.prepareStatement("DELETE FROM " + getTableName() + " WHERE USER_ID IS NULL OR USER_ID=0");
            deleteNullStmt.execute();
        }
    }
}

class UserStorage extends RaplaTypeStorage<User>
{
    UserGroupStorage groupStorage;

    public UserStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, User.class, "RAPLA_USER",
                new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "USERNAME VARCHAR(255) NOT NULL", "PASSWORD VARCHAR(255)", "NAME VARCHAR(255) NOT NULL",
                        "EMAIL VARCHAR(255) NOT NULL", "ISADMIN INTEGER NOT NULL", "CREATION_TIME TIMESTAMP", "LAST_CHANGED TIMESTAMP KEY",
                        // PRD 050: external IdP marker. NULL = local password.
                        "AUTHENTICATION_SOURCE VARCHAR(64)" });
        groupStorage = new UserGroupStorage(context);
        addSubStorage(groupStorage);
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndDrop(schema, "DELETED");
        // PRD 050: additive migration. Existing rows stay NULL = local.
        checkAndAdd(schema, "AUTHENTICATION_SOURCE");
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        insert(cache.getUsers());
    }

    @Override
    protected int write(PreparedStatement stmt, User user) throws SQLException, RaplaException
    {
        setId(stmt, 1, user);
        setString(stmt, 2, user.getUsername());
        String password = cache.getPassword(user.getReference());
        setString(stmt, 3, password);
        //setId(stmt,4,user.getPerson());
        setString(stmt, 4, user.getName());
        setString(stmt, 5, user.getEmail());
        stmt.setInt(6, user.isAdmin() ? 1 : 0);
        setTimestamp(stmt, 7, user.getCreateDate());
        setTimestamp(stmt, 8, user.getLastChanged());
        setString(stmt, 9, user.getAuthenticationSource());
        stmt.addBatch();
        return 1;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        ReferenceInfo<User> userId = readId(rset, 1, User.class);
        String username = getString(rset, 2, null);
        if (username == null)
        {
            getLogger().warn("Username is null for " + userId + " Ignoring user.");
        }
        String password = getString(rset, 3, null);
        //String personId = readId(rset,4, Allocatable.class, true);
        String name = getString(rset, 4, "");
        String email = getString(rset, 5, "");
        boolean isAdmin = rset.getInt(6) == 1;
        LocalDateTime createDate = getTimestampOrNow(rset, 7);
        LocalDateTime lastChanged = getTimestampOrNow(rset, 8);
        String authenticationSource = getString(rset, 9, null);

        UserImpl user = new UserImpl(createDate, lastChanged);
        //        if ( personId != null)
        //        {
        //            user.putId("person", personId);
        //        }
        user.setId(userId);
        user.setUsername(username);
        user.setName(name);
        user.setEmail(email);
        user.setAdmin(isAdmin);
        user.setAuthenticationSource(authenticationSource);
        if (password != null)
        {
            putPassword(userId, password);
        }
        put(user);
    }

}

class ConflictStorage extends RaplaTypeStorage<Conflict>
{

    public ConflictStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, Conflict.class, "RAPLA_CONFLICT",
                new String[] { "RESOURCE_ID VARCHAR(255) NOT NULL", "APPOINTMENT1 VARCHAR(255) NOT NULL", "APPOINTMENT2 VARCHAR(255) NOT NULL",
                        "APP1ENABLED INTEGER NOT NULL", "APP2ENABLED INTEGER NOT NULL", "LAST_CHANGED TIMESTAMP KEY" });
        this.deleteSql = "delete from " + getTableName() + " where RESOURCE_ID=? and APPOINTMENT1=? and APPOINTMENT2=?";
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndDrop(schema, "DELETED");
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        insert(cache.getDisabledConflicts());
    }

    @Override
    public void deleteEntities(Iterable<ReferenceInfo<Conflict>> entities) throws SQLException, RaplaException
    {
        PreparedStatement stmt = null;
        try
        {
            stmt = con.prepareStatement(deleteSql);
            boolean execute = false;
            for (ReferenceInfo<Conflict> conflictRef : entities)
            {
                String id = conflictRef.getId();
                final LocalDateTime connectionTimestamp = getConnectionTimestamp();
                Conflict conflict = new ConflictImpl(id, connectionTimestamp, connectionTimestamp);
                ReferenceInfo<Allocatable> allocatableId = conflict.getAllocatableId();
                ReferenceInfo<Appointment> appointment1Id = conflict.getAppointment1();
                ReferenceInfo<Appointment> appointment2Id = conflict.getAppointment2();

                setId(stmt, 1, allocatableId);
                setId(stmt, 2, appointment1Id);
                setId(stmt, 3, appointment2Id);
                //setTimestamp(stmt, 4, lastChanged);
                stmt.addBatch();
                execute = true;
            }
            if (execute)
            {
                stmt.executeBatch();
            }
        }
        finally
        {
            if (stmt != null)
                stmt.close();
        }
    }

    @Override
    protected int write(PreparedStatement stmt, Conflict conflict) throws SQLException, RaplaException
    {
        setId(stmt, 1, conflict.getAllocatableId());
        setId(stmt, 2, conflict.getAppointment1());
        setId(stmt, 3, conflict.getAppointment2());
        boolean appointment1Enabled = conflict.isAppointment1Enabled();
        setInt(stmt, 4, appointment1Enabled ? 1 : 0);
        boolean appointment2Enabled = conflict.isAppointment2Enabled();
        setInt(stmt, 5, appointment2Enabled ? 1 : 0);
        setTimestamp(stmt, 6, conflict.getLastChanged());
        stmt.addBatch();
        return 1;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        ReferenceInfo<Allocatable> allocatableId = readId(rset, 1, Allocatable.class);
        ReferenceInfo<Appointment> appointment1Id = readId(rset, 2, Appointment.class);
        ReferenceInfo<Appointment> appointment2Id = readId(rset, 3, Appointment.class);

        boolean appointment1Enabled = rset.getInt(4) == 1;
        boolean appointment2Enabled = rset.getInt(5) == 1;
        LocalDateTime timestamp = getTimestamp(rset, 6, true);
        LocalDateTime today = getConnectionTimestamp();
        String id = ConflictImpl.createId(allocatableId, appointment1Id, appointment2Id);
        ConflictImpl conflict = new ConflictImpl(id, today, timestamp);
        conflict.setAppointment1Enabled(appointment1Enabled);
        conflict.setAppointment2Enabled(appointment2Enabled);
        put(conflict);
    }

}

class UserGroupStorage extends EntityStorage<User> implements SubStorage<User>
{
    public UserGroupStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, "RAPLA_USER_GROUP", new String[] { "USER_ID VARCHAR(255) NOT NULL KEY", "CATEGORY_ID VARCHAR(255) NOT NULL" });
    }

    @Override
    protected int write(PreparedStatement stmt, User entity) throws SQLException, RaplaException
    {
        setId(stmt, 1, entity);
        int count = 0;
        for (Category category : entity.getGroupList())
        {
            setId(stmt, 2, category);
            stmt.addBatch();
            count++;
        }
        return count;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException
    {
        User user = resolveFromId(rset, 1, User.class, true);
        if (user == null)
        {
            return;
        }
        Category category = resolveFromId(rset, 2, Category.class, true);
        if (category == null)
        {
            return;
        }
        user.addGroup(category);
    }
}

class HistoryStorage<T extends Entity<T>> extends RaplaTypeStorage<T>
{

    private final JsonParserWrapper.JsonParser jsonParser;
    private final LocalDateTime supportTimestamp;
    private final String loadAllUpdatesSql;

    HistoryStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, null, "CHANGES",
                new String[] { "ID VARCHAR(255) KEY", "TYPE VARCHAR(50)", "ENTITY_CLASS VARCHAR(255)", "XML_VALUE TEXT NOT NULL", "CHANGED_AT TIMESTAMP KEY",
                        "ISDELETE INTEGER NOT NULL" });
        loadAllUpdatesSql = "SELECT ID, TYPE, ENTITY_CLASS, XML_VALUE, CHANGED_AT, ISDELETE FROM CHANGES WHERE CHANGED_AT >= ? ORDER BY CHANGED_AT ASC";
        Class[] additionalClasses = new Class[] { RaplaMapImpl.class };
        jsonParser = JsonParserWrapper.defaultJson().get();
        if (context.has(LocalDateTime.class))
        {
            supportTimestamp = context.lookup(LocalDateTime.class);
        }
        else
        {
            supportTimestamp = null;
        }
    }

    public void cleanupHistory(LocalDateTime date) throws SQLException
    {
        // first we collect all dates from entries
        final LinkedHashMap<String, LocalDateTime> idToTimestamp = new LinkedHashMap<>();
        final HashSet<String> hasChangeLargerThanDate = new HashSet<>();
        final HashSet<String> toDeleteFromHistory = new HashSet<>();
        ResultSet result = null;
        final long cleanUpBefore = DateTools.toMilli(date);
        try (final PreparedStatement stmt = con.prepareStatement("SELECT ID, CHANGED_AT FROM CHANGES ORDER BY CHANGED_AT DESC"))
        {
            result = stmt.executeQuery();
            while (result.next())
            {
                final String id = result.getString(1);
                final LocalDateTime timestamp = getTimestamp(result, 2, false);
                if ( timestamp != null && DateTools.toMilli(timestamp) > cleanUpBefore)
                {
                    hasChangeLargerThanDate.add( id );
                }
                else
                {
                    // we have only old entries, so we can delete all ids
                    if ( !hasChangeLargerThanDate.contains( id))
                    {
                        toDeleteFromHistory.add( id);
                    }
                    // we leave the latest timestamp, so we can still get the difference
                    else if (timestamp != null && !idToTimestamp.containsKey(id))
                    {
                        idToTimestamp.put(id, timestamp);
                    }
                }
            }
        }
        finally
        {
            if (result != null)
            {
                result.close();
            }
        }
        // now we delete all older entries or those who have the same timestamp and are deleted
        int sum =0;
        try (final PreparedStatement stmt = con.prepareStatement("DELETE FROM CHANGES WHERE (ID = ? AND CHANGED_AT < ?) OR (ID = ? AND CHANGED_AT = ? AND ISDELETE = 1)"))
        {
            boolean batch = false;
            for (Entry<String, LocalDateTime> idAndTimestamp : idToTimestamp.entrySet())
            {
                final String id = idAndTimestamp.getKey();
                final LocalDateTime changedAt = idAndTimestamp.getValue();
                stmt.setString(1, id);
                setTimestamp(stmt, 2, changedAt);
                stmt.setString(3, id);
                setTimestamp(stmt, 4, changedAt);
                stmt.addBatch();
                batch = true;
            }
            if ( batch)
            {
                final int[] executeBatch = stmt.executeBatch();
                for (int i : executeBatch)
                {
                    sum += i;
                }
            }
        }

        try (final PreparedStatement stmt = con.prepareStatement("DELETE FROM CHANGES WHERE ID = ?"))
        {
            boolean batch = false;
            for (String id : toDeleteFromHistory)
            {
                stmt.setString(1, id);
                stmt.addBatch();
                batch = true;
            }
            if ( batch)
            {
                final int[] executeBatch = stmt.executeBatch();
                for (int i : executeBatch)
                {
                    sum += i;
                }
            }
        }
        getLogger().info("Deleted " + sum + " history entries");
    }

    @Override
    protected void createSQL(Collection<ColumnDef> entries)
    {
        super.createSQL(entries);
        selectSql += " ORDER BY CHANGED_AT DESC";
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        final Collection<Entity> entites = new LinkedList<>();
        entites.addAll(cache.getAllocatables());
        entites.addAll(cache.getDynamicTypes());
        entites.addAll(cache.getReservations());
        entites.addAll(cache.getUsers());
        entites.addAll(CategoryImpl.getRecursive(cache.getSuperCategory()));
        if (entites.isEmpty())
        {
            return;
        }
        try (PreparedStatement stmt = con.prepareStatement(insertSql))
        {
            for (Entity entity : entites)
            {
                write(stmt, (T) entity);
            }
            stmt.executeBatch();
        }
    }

    // Don't update timestamp in historystorage it is already updated  in the storage of the entity itself
    @Override
    protected void updateTimestamp(ModifiableTimestamp timestamp)
    {

    }

    private void insertWhenNotThere(Entity oldEntity, LocalDateTime lastChanged) throws SQLException
    {
        if ( !hasHistory( oldEntity))
        {
            try (PreparedStatement stmt = con.prepareStatement(insertSql))
            {
                final LocalDateTime timestamp = getConnectionTimestamp();
                write(stmt, (T) oldEntity, false, timestamp);
                stmt.executeBatch();
            }
        }
    }

    private boolean hasHistory(Entity oldEntity) throws SQLException
    {
        ResultSet result = null;
            try (final PreparedStatement stmt = con.prepareStatement("SELECT ID, CHANGED_AT FROM CHANGES WHERE ID = ?"))
            {
                stmt.setString(1, oldEntity.getId());
                result = stmt.executeQuery();
                if (result.next())
                {
                    return true;
                }

            }
            finally
            {
                if (result != null)
                {
                    result.close();
                }
            }
        return false;
    }

    public void save(Map<T,T> entities) throws RaplaException, SQLException
    {
        final Set<T> entityList = entities.keySet();
        for ( Entity entity: entityList)
        {
            final Entity oldEntity = entities.get( entity);
            if (oldEntity != null)
            {
                final LocalDateTime lastChanged = ((Timestamp) oldEntity).getLastChanged();
                if (lastChanged != null)
                {
                    insertWhenNotThere( oldEntity, lastChanged);

                }
            }
        }
        insert(entityList, false);
    }

    @Override
    public void deleteEntities(Iterable<ReferenceInfo<T>> referenceInfos) throws SQLException, RaplaException
    {
        // only write history entry if entity is deleted and not updated
        Collection<T> entitiesWithTransitiveCategories = getResolvableEntities(referenceInfos);
        insert(entitiesWithTransitiveCategories, true);
    }

    protected Collection<T> getResolvableEntities(Iterable<ReferenceInfo<T>> referenceInfos)
    {
        Collection<T> resolvedEntities = new LinkedList<>();
        for (ReferenceInfo<T> ref : referenceInfos)
        {
            T entity = cache.tryResolve(ref);
            if (entity != null)
            {
                resolvedEntities.add(entity);
            }
            else
            {
                getLogger().error("can't resolve event for remove entity with id " + ref);
            }
        }
        return resolvedEntities;
    }

    private void insert(Iterable<T> entities, boolean asDelete) throws SQLException, RaplaException
    {
        try (PreparedStatement stmt = con.prepareStatement(insertSql))
        {
            int count = 0;
            for (T entity : entities)
            {
                count += write(stmt, entity, asDelete);
            }
            if (count > 0)
            {
                stmt.executeBatch();
            }
        }
        catch (SQLException ex)
        {
            throw ex;
        }
    }

    @Override
    boolean canDelete(Class<Entity> typeClass)
    {
        return EntityHistory.isSupportedEntity(typeClass);
    }

    @Override
    boolean canStore(@SuppressWarnings("rawtypes") Class<Entity> typeClass)
    {
        return EntityHistory.isSupportedEntity(typeClass);
    }


    @Override
    protected int write(PreparedStatement stmt, T entity) throws SQLException, RaplaException
    {
        return write(stmt, entity, false);
    }

    private int write(PreparedStatement stmt, T entity, boolean asDeletion) throws SQLException, RaplaException
    {
        final LocalDateTime timestamp = getConnectionTimestamp();
        return write(stmt, entity, asDeletion, timestamp);
    }

    private int write(PreparedStatement stmt, T entity, boolean asDeletion, LocalDateTime timestamp) throws SQLException
    {
        stmt.setString(1, entity.getId());
        stmt.setString(2, RaplaType.getLocalName(entity));
        stmt.setString(3, entity.getClass().getCanonicalName());
        final String xml = jsonParser.toJson(entity);
        setText(stmt, 4, xml);
        setTimestamp(stmt, 5, timestamp);
        setInt(stmt, 6, asDeletion ? 1 : 0);
        stmt.addBatch();
        return 1;
    }

    public Collection<ReferenceInfo> update(LocalDateTime lastUpdated) throws SQLException, RaplaException
    {
        try (final PreparedStatement stmt = con.prepareStatement(loadAllUpdatesSql))
        {
            // PRD 108 — CHANGED_AT uses the same convention as every other
            // timestamp column (AbstractTableStorage.setTimestamp: true instant,
            // rendered by the driver in the JVM zone). Write, read predicate and
            // load() must all go through that helper pair — mixing conventions
            // shifts the predicate by the local UTC offset and either misses
            // just-written rows or replays legacy Rapla 2 rows as "future".
            setTimestamp(stmt, 1, lastUpdated);
            final ResultSet result = stmt.executeQuery();
            if (result == null)
            {
                return Collections.emptyList();
            }
            Collection<ReferenceInfo> ids = new HashSet<>();
            while (result.next())
            {
                load(result);
                final String id = result.getString(1);
                final String raplaTypeLocalName = result.getString(2);
                final Class<? extends Entity> typeClass = RaplaType.find(raplaTypeLocalName);
                ids.add(new ReferenceInfo(id, typeClass));
            }
            return ids;
        }
    }

    @Override
    public void loadAll() throws SQLException, RaplaException
    {
        try (Statement stmt = con.createStatement(); ResultSet rset = stmt.executeQuery(selectSql))
        {
            final HashSet<String> finishedIdsToLoad = new HashSet<>();
            while (rset.next())
            {
                final String id = rset.getString(1);
                if (finishedIdsToLoad.contains(id))
                {
                    continue;
                }
                load(rset);
                // the select is ordered desc by last_changed, so if we get to early in time, we do not need to load it
                final LocalDateTime timestamp = getTimestamp(rset, 5, false);
                if (supportTimestamp != null && timestamp != null && timestamp.isBefore(supportTimestamp))
                {
                    finishedIdsToLoad.add(id);
                }
            }
        }
        {
            final Collection<ReferenceInfo> allIds = history.getAllIds();
            final LocalDateTime connectionTimestamp = getConnectionTimestamp();
            for (ReferenceInfo id : allIds)
            {
                final Entity<?> entity = entityStore.tryResolve(id);
                if (entity == null)
                {
                    final HistoryEntry before = history.getLastChangedUntil(id, connectionTimestamp);
                    if (before != null && before.getTimestamp() >= DateTools.toMilli(connectionTimestamp))
                    {
                        put(history.getEntity(before));
                    }
                    continue;
                }
                if (entity instanceof Timestamp)
                {
                    final LocalDateTime lastChanged = ((Timestamp) entity).getLastChanged();
                    if (lastChanged != null)
                    {
                        if (lastChanged.isAfter(connectionTimestamp))
                        {// we need to restore from history
                            final HistoryEntry before = history.getLastChangedUntil(id, connectionTimestamp);
                            if (before != null)
                            {
                                put(history.getEntity(before));
                            }
                        }
                    }
                    else
                    {
                        getLogger().debug("Ignoring entity without timestamp " + entity);
                    }
                }
            }
        }
    }

    @Override
    protected void load(ResultSet rs) throws SQLException, RaplaException
    {
        final String id = rs.getString(1);
        final String raplaTypeLocalName = rs.getString(2);
        final Class<? extends Entity> typeClass = RaplaType.find(raplaTypeLocalName);
        final String className = getString(rs, 3, null);
        final String json = getText(rs, 4);
        // PRD 108 — inverse of write()'s setTimestamp(...). Both sides use the
        // AbstractTableStorage helper pair, so the history map holds the true
        // instant as a UTC LDT, comparable with getConnectionTimestamp() and
        // with the entity tables' LAST_CHANGED.
        final LocalDateTime lastChanged = getTimestamp(rs, 5, false);
        final Integer isDelete = getInt(rs, 6);
        history.addHistoryEntry(new ReferenceInfo(id, typeClass), json, lastChanged, isDelete != null && isDelete == 1);
    }

}

class ImportExportStorage extends RaplaTypeStorage<ExternalSyncEntity>
{
    public ImportExportStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, ExternalSyncEntity.class, "IMPORT_EXPORT",
                new String[] { "FOREIGN_ID VARCHAR(255) KEY", "EXTERNAL_SYSTEM VARCHAR(255) KEY", "RAPLA_ID VARCHAR(255)", "DIRECTION INTEGER NOT NULL",
                        "DATA TEXT NOT NULL", "CONTEXT TEXT", "CHANGED_AT TIMESTAMP KEY" });
    }

    @Override
    public void save( Iterable<ExternalSyncEntity> entitiesOrig ) throws RaplaException, SQLException{
        Collection<ReferenceInfo<ExternalSyncEntity>> toDelete = new ArrayList<>();
        List<ExternalSyncEntity> entities = new ArrayList<>();
        for (ExternalSyncEntity entity:entitiesOrig)
        {
            if ( entity.getExternalSystem() == null) {
                String id = entity.getId();
                if (id != null) {
                    Map<String, ExternalSyncEntity> load = load(id);
                    ExternalSyncEntity existing = load.get(id );
                    if ( existing == null ){
                        continue;
                    }
                    ExternalSyncEntityImpl entityClone = (ExternalSyncEntityImpl) existing.clone();
                    if ( entity.getData() != null) {
                        entityClone.setData( entity.getData());
                    }
                    entity = entityClone;
                } else {
                    continue;
                }
            }
            entities.add( entity);
        }
        for (ExternalSyncEntity entity:entities)
        {
            toDelete.add( entity.getReference());
        }
        deleteEntities(  toDelete );
        insert( entities );
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        // do nothing
    }

    @Override
    protected int write(PreparedStatement stmt, ExternalSyncEntity entity) throws SQLException, RaplaException
    {
        stmt.setString(1, entity.getId());
        stmt.setString(2, entity.getExternalSystem());
        stmt.setString(3, entity.getRaplaId());
        setInt(stmt, 4, entity.getDirection());
        setText(stmt, 5, entity.getData());
        setText(stmt, 6, entity.getContext());
        setTimestamp(stmt, 7, getConnectionTimestamp());
        stmt.addBatch();
        return 1;
    }

    @Override
    protected void createSQL(Collection<ColumnDef> entries)
    {
        super.createSQL(entries);
    }

    public Map<String, ExternalSyncEntity> load(String externalSystemId, int direction) throws SQLException
    {
        final String sqlLoadByExternalSystemAndDirection = selectSql + " WHERE EXTERNAL_SYSTEM = ? AND DIRECTION = ?";
        try (PreparedStatement stmt = con.prepareStatement(sqlLoadByExternalSystemAndDirection))
        {
            stmt.setString(1, externalSystemId);
            stmt.setInt(2, direction);
            return load(stmt);
        }
    }

    public Map<String, ExternalSyncEntity> load(String id) throws SQLException
    {
        final String sqlLoadByExternalSystemAndDirection = selectSql + " WHERE FOREIGN_ID = ?";
        try (PreparedStatement stmt = con.prepareStatement(sqlLoadByExternalSystemAndDirection))
        {
            stmt.setString(1, id);
            return load(stmt);
        }
    }


    @NotNull
    private Map<String, ExternalSyncEntity> load(PreparedStatement stmt) throws SQLException {
        final ResultSet rs = stmt.executeQuery();
        if (rs == null)
        {
            return Collections.emptyMap();
        }
        Map<String, ExternalSyncEntity> result = new LinkedHashMap<>();
        while (rs.next())
        {
            final ExternalSyncEntityImpl importExportEntityImpl = new ExternalSyncEntityImpl();
            importExportEntityImpl.setId(rs.getString(1));
            importExportEntityImpl.setExternalSystem(rs.getString(2));
            importExportEntityImpl.setRaplaId(rs.getString(3));
            importExportEntityImpl.setDirection(rs.getInt(4));
            importExportEntityImpl.setData(getText(rs, 5));
            importExportEntityImpl.setContext(getText(rs, 6));
            result.put(importExportEntityImpl.getId(), importExportEntityImpl);
        }
        return result;
    }

    public Collection<ExternalSyncEntity> loadAllIntoList() throws SQLException, RaplaException
    {
        try (PreparedStatement stmt = con.prepareStatement(selectSql))
        {
            return load(stmt).values();
        }
    }

    @Override
    public void loadAll() throws SQLException, RaplaException
    {
    }

    @Override
    protected void load(ResultSet rs) throws SQLException, RaplaException
    {
        // Do not load into cache
    }

}

class ArtifactStorage extends RaplaTypeStorage<StoredArtifact>
{
    public ArtifactStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, StoredArtifact.class, "ARTIFACT",
                // CHANGED_BY deliberately not named LAST_CHANGED_BY: a column starting with
                // "LAST_CHANGED" flips EntityStorage into timestamp-guarded deletes (LAST_CHANGED column convention)
                new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "KIND VARCHAR(50) KEY", "NAME VARCHAR(255)", "OWNER_ID VARCHAR(255)",
                        "BODY TEXT NOT NULL", "METADATA TEXT", "CREATED_AT TIMESTAMP", "CHANGED_AT TIMESTAMP KEY", "CHANGED_BY VARCHAR(255)" });
    }

    @Override
    public void save(Iterable<StoredArtifact> entitiesOrig) throws RaplaException, SQLException
    {
        Collection<ReferenceInfo<StoredArtifact>> toDelete = new ArrayList<>();
        List<StoredArtifact> entities = new ArrayList<>();
        for (StoredArtifact entity : entitiesOrig)
        {
            toDelete.add(entity.getReference());
            entities.add(entity);
        }
        deleteEntities(toDelete);
        insert(entities);
    }

    @Override
    protected int write(PreparedStatement stmt, StoredArtifact entity) throws SQLException, RaplaException
    {
        stmt.setString(1, entity.getId());
        stmt.setString(2, entity.getKind());
        stmt.setString(3, entity.getName());
        final ReferenceInfo<User> ownerRef = entity.getOwnerRef();
        stmt.setString(4, ownerRef != null ? ownerRef.getId() : null);
        setText(stmt, 5, entity.getBody());
        setText(stmt, 6, entity.getMetadata());
        setTimestamp(stmt, 7, entity.getCreateDate());
        setTimestamp(stmt, 8, getConnectionTimestamp());
        final ReferenceInfo<User> lastChangedBy = entity.getLastChangedBy();
        stmt.setString(9, lastChangedBy != null ? lastChangedBy.getId() : null);
        stmt.addBatch();
        return 1;
    }

    public Collection<StoredArtifact> loadAllIntoList() throws SQLException
    {
        return query(selectSql, null);
    }

    /** Catalog listing — bodies stay in the database (metadata-only rows, BODY selected as NULL). */
    public Collection<StoredArtifact> loadMetadataIntoList() throws SQLException
    {
        final String sql = "select ID,KIND,NAME,OWNER_ID,CAST(NULL AS CHAR(1)),METADATA,CREATED_AT,CHANGED_AT,CHANGED_BY from " + tableName;
        return query(sql, null);
    }

    /** Point read by natural key; null when absent. */
    public StoredArtifact loadById(String id) throws SQLException
    {
        final Collection<StoredArtifact> result = query(selectSql + " where ID = ?", id);
        return result.isEmpty() ? null : result.iterator().next();
    }

    private Collection<StoredArtifact> query(String sql, String idParam) throws SQLException
    {
        try (PreparedStatement stmt = con.prepareStatement(sql))
        {
            if (idParam != null)
            {
                stmt.setString(1, idParam);
            }
            final ResultSet rs = stmt.executeQuery();
            if (rs == null)
            {
                return Collections.emptyList();
            }
            List<StoredArtifact> result = new ArrayList<>();
            while (rs.next())
            {
                final StoredArtifactImpl artifact = new StoredArtifactImpl();
                artifact.setId(rs.getString(1));
                artifact.setKind(rs.getString(2));
                artifact.setName(rs.getString(3));
                final String ownerId = rs.getString(4);
                if (ownerId != null)
                {
                    artifact.putId("owner", new ReferenceInfo<>(ownerId, User.class));
                }
                artifact.setBody(getText(rs, 5));
                artifact.setMetadata(getText(rs, 6));
                artifact.setCreateDate(getTimestamp(rs, 7, false));
                artifact.setLastChanged(getTimestamp(rs, 8, false));
                final String lastChangedById = rs.getString(9);
                if (lastChangedById != null)
                {
                    artifact.putId("last_changed_by", new ReferenceInfo<>(lastChangedById, User.class));
                }
                result.add(artifact);
            }
            return result;
        }
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        // do nothing
    }

    @Override
    public void loadAll() throws SQLException, RaplaException
    {
    }

    @Override
    protected void load(ResultSet rs) throws SQLException, RaplaException
    {
        // Do not load into cache
    }
}
