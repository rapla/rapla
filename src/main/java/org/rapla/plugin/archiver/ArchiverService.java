package org.rapla.plugin.archiver;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.scheduler.Promise;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/archiver")
public interface ArchiverService
{
	String PLUGIN_ID = "org.rapla.plugin.archiver.server";
	TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<>(PLUGIN_ID);

	String REMOVE_OLDER_THAN_ENTRY = "remove-older-than";
	String EXPORT = "export";

	@PostExchange
	Promise<Void> delete(@RequestParam(value = "olderThanInDays", required = false) Integer olderThanInDays);
	@GetExchange
	boolean isExportEnabled() throws RaplaException;
	@PostExchange("/backup")
	Promise<Void> backupNow();
	@PostExchange("/restore")
	Promise<Void> restore();
}
