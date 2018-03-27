package org.rapla.plugin.archiver;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.scheduler.Promise;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

@Path("archiver")
public interface ArchiverService
{
	String PLUGIN_ID = "org.rapla.plugin.archiver.server";
	TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<>(PLUGIN_ID);

	String REMOVE_OLDER_THAN_ENTRY = "remove-older-than";
	String EXPORT = "export";
	
	@POST
	Promise<Void> delete(Integer olderThanInDays);
	@GET
	boolean isExportEnabled() throws RaplaException;
	@POST
	@Path("backup")
	Promise<Void> backupNow();
	@POST
	@Path("restore")
	Promise<Void> restore();
}
