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
/** A StorageOperator that operates on a LocalCache-Object.
 */
package org.rapla.storage;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;
import org.rapla.storage.impl.EntityStore;

import javax.xml.stream.events.EntityReference;
import java.util.*;

import java.time.LocalDateTime;
public interface CachableStorageOperator extends StorageOperator {

    LocalDateTime getLastRefreshed();
    LocalDateTime getHistoryValidStart();
    LocalDateTime getConnectStart();

    void connect() throws RaplaException;

    /** PRD 058: one-shot startup migration that renames every DynamicType /
     *  Attribute / Category key with a non-GraphQL-spec character to a
     *  deterministic spec-compliant key. Called once from the operator's
     *  bean factory after {@link #connect()} returns, before any consumer
     *  (in particular the GraphQL SDL generator) sees the operator. Marker-
     *  guarded; subsequent boots skip-fast. Cache-side assertion runs whether
     *  or not the migration plan was empty; a non-spec key surviving in the
     *  cache is a fatal startup error. */
    void migrateGraphqlKeysIfNeeded() throws RaplaException;

    /** PRD 048: logical restart — reloads all data from the store, clears and
     *  rebuilds the caches and re-arms the operator's scheduled tasks, without
     *  a JVM/Spring restart. Reloads only the serving pod; other pods re-sync
     *  via the update history. */
    void reload() throws RaplaException;
	void runWithReadLock(CachableStorageOperatorCommand cmd) throws RaplaException;
    void dispatch(UpdateEvent evt) throws RaplaException;
    String authenticate(String username,String password) throws RaplaException;
    void saveData(LocalCache cache, Collection<ExternalSyncEntity> syncEntities, String version) throws RaplaException;
    
    Collection<Entity> getVisibleEntities(final User user) throws RaplaException;

    /** PRD 081 — every reservation held in the operator's cache, UNFILTERED.
     *  Windowless accessor for the omnibox event name-search (no from/to). The
     *  caller MUST §12-gate every entry via {@code PermissionController.canRead}
     *  before exposing it — this returns the raw store. The server keeps the
     *  full model in memory, so this is an in-memory cache read. */
    Collection<Reservation> getReservations() throws RaplaException;
    //Collection<Entity> getUpdatedEntities(final User user,LocalDateTime timestamp) throws RaplaException;
    //Collection<ReferenceInfo> getDeletedEntities(finaldf User user, final LocalDateTime timestamp) throws RaplaException;

    ReferenceInfo tryResolveExternalId(String externalId);

    TimeZone getTimeZone();
    //DynamicType getUnresolvedAllocatableType(); 
    //DynamicType getAnonymousReservationType();

    UpdateResult getUpdateResult(LocalDateTime since) throws RaplaException;
    UpdateResult getUpdateResult(LocalDateTime since,User user) throws RaplaException;

    Map<String, ExternalSyncEntity> getImportExportEntities(String systemId, int importExportDirection) throws RaplaException;
    
    /**
     * Tries to receive the lock for the given id. If another System has the lock, a RaplaException is thrown
     * @param id the id of the lock
     * @param validMilliseconds the milliseconds the lock is valid (after that time the cleanup will remove the lock on the next
     * execution)
     * @return the time taken from the underlying system (database or file) when the lock was last requested
     * @throws RaplaException if the lock can not be received
     */
    LocalDateTime requestLock(String id, Long validMilliseconds) throws RaplaException;
    void releaseLock(String id, LocalDateTime updatedUntil) throws RaplaException;

    Set<ReferenceInfo<Allocatable>> filterAllocatablesWithNonTemplateReservations(Set<ReferenceInfo<Allocatable>> allocatables);
}
















