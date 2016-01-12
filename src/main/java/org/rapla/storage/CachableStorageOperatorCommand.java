package org.rapla.storage;

import org.rapla.framework.RaplaException;

public interface CachableStorageOperatorCommand {
	void execute(LocalCache cache) throws RaplaException;
}
