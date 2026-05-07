package org.rapla.storage;

import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.framework.RaplaException;

import java.util.Collection;

public interface CachableStorageOperatorCommand {
	void execute(LocalCache cache, Collection<ExternalSyncEntity> externalSyncEntityList) throws RaplaException;
}
