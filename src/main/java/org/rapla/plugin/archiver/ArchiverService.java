package org.rapla.plugin.archiver;

import javax.jws.WebService;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;

@RemoteJsonMethod
public interface ArchiverService
{
	public static final String PLUGIN_ID = "org.rapla.plugin.archiver.server";
	public static final TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<RaplaConfiguration>(PLUGIN_ID);

	String REMOVE_OLDER_THAN_ENTRY = "remove-older-than";
	String EXPORT = "export";
	
	void delete(Integer olderThanInDays) throws RaplaException;
	boolean isExportEnabled() throws RaplaException;
	void backupNow() throws RaplaException;
	void restore() throws RaplaException;
}
