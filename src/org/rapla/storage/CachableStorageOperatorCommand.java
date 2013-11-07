package org.rapla.storage;

import org.rapla.framework.RaplaException;

public interface CachableStorageOperatorCommand {
	public void execute( LocalCache cache) throws RaplaException;
}
