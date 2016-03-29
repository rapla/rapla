package org.rapla.plugin.archiver;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

@Path("archiver")
public interface ArchiverService
{
	String PLUGIN_ID = "org.rapla.plugin.archiver.server";
	TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<RaplaConfiguration>(PLUGIN_ID);

	String REMOVE_OLDER_THAN_ENTRY = "remove-older-than";
	String EXPORT = "export";
	
	@POST
	void delete(Integer olderThanInDays) throws RaplaException;
	@GET
	boolean isExportEnabled() throws RaplaException;
	@POST
	@Path("backup")
	void backupNow() throws RaplaException;
	@POST
	@Path("restore")
	void restore() throws RaplaException;
}
