package org.rapla.plugin.exchangeconnector;

import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Collection;

@HttpExchange("/exchange/connect")
public interface ExchangeConnectorRemote
{
    TypedComponentRole<String> LAST_SYNC_ERROR_CHANGE = new TypedComponentRole<>("org.rapla.plugin.exchangconnector.last_sync_error_change");

    @GetExchange
    SynchronizationStatus getSynchronizationStatus() throws RaplaException;

    @PostExchange("/synchronize")
    void synchronize(@RequestBody String mailbox) throws RaplaException;

	/**
	 * Add an Exchange user to the user list (register a user to the Exchange Server)
	 * (User wants to have his Exchange Account synchronized with the Rapla system)
	 *
	 * @param exchangeUsername
	 * @param exchangePassword
	 * @throws RaplaException
	 */
	@PostExchange
	Collection<String> changeUser(@RequestParam("user")String exchangeUsername, @RequestBody String exchangePassword/*, Boolean downloadFromExchange*/) throws RaplaException;

	@PostExchange("/remove")
	void removeUser() throws RaplaException;

	@PostExchange("/refreshMailboxes")
	Collection<String> refreshMailboxes() throws RaplaException;

}
