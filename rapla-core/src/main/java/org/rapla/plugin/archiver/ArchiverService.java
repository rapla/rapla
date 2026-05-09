package org.rapla.plugin.archiver;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Returns synchronous types. Spring's HttpServiceProxyFactory has no adapter
 * for {@code org.rapla.scheduler.Promise<X>} — using it would make Jackson try
 * to deserialize the response body INTO a Promise instance and throw
 * {@code InvalidDefinitionException}. Callers wanting async dispatch wrap the
 * call site in {@code commandScheduler.supply(() -> ...)}.
 */
@HttpExchange("/archiver")
public interface ArchiverService
{
	String PLUGIN_ID = "org.rapla.plugin.archiver.server";
	TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<>(PLUGIN_ID);

	String REMOVE_OLDER_THAN_ENTRY = "remove-older-than";
	String EXPORT = "export";

	@PostExchange
	void delete(@RequestParam(value = "olderThanInDays", required = false) Integer olderThanInDays) throws RaplaException;
	@GetExchange
	boolean isExportEnabled() throws RaplaException;
	@PostExchange("/backup")
	void backupNow() throws RaplaException;
	@PostExchange("/restore")
	void restore() throws RaplaException;
}
