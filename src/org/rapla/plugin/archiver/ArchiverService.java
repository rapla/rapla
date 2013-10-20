package org.rapla.plugin.archiver;

import org.rapla.framework.RaplaException;

public interface ArchiverService 
{
	String REMOVE_OLDER_THAN_ENTRY = "remove-older-than";
	String EXPORT = "export";
	
	void delete(Integer olderThanInDays) throws RaplaException;
	boolean isExportEnabled() throws RaplaException;
	void backupNow() throws RaplaException;
	void restore() throws RaplaException;
}
