package org.rapla.plugin.exchangeconnector;

import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

@RemoteJsonMethod
public interface ExchangeConnectorRemote 
{
    TypedComponentRole<String> LAST_SYNC_ERROR_CHANGE = new TypedComponentRole<String>("org.rapla.plugin.exchangconnector.last_sync_error_change");
    SynchronizationStatus getSynchronizationStatus() throws RaplaException;
	SynchronizeResult synchronize() throws RaplaException;

	/** 
	 * Add an Exchange user to the user list (register a user to the Exchange Server)
	 * (User wants to have his Exchange Account synchronized with the Rapla system)
	 * 
	 * @param exchangeUsername  
	 * @param exchangePassword
	 * @return {@link ClientMessage}
	 * @throws RaplaException
	 */
	void changeUser(String exchangeUsername, String exchangePassword/*, Boolean downloadFromExchange*/) throws RaplaException;
	void removeUser() throws RaplaException;
	SynchronizeResult retry() throws RaplaException;
	
	/**
	 * Remove an existing user from the user list (unregister a user from the Exchange Server)
	 * (The User and the password will no longer be saved)
	 * 
	 * @param raplaUsername : {@link String} name of the Rapla {@link User} which should be removed from the user list
	 * @return {@link ClientMessage}
	 * @throws RaplaException
	 */
	//public String removeExchangeUser() throws RaplaException;

	/**
	 * This method initialises a so called "complete reconciliation" - meaning a re-sync of all existing appointments on both systems.
	 * @return {@link ClientMessage}
	 * @throws RaplaException
	 */
	//public String completeReconciliation() throws RaplaException;


    /**
     * enables/disable pull
     * @param raplaUsername
     * @param downloadFromExchange
     * @throws RaplaException
     */
    //public void setDownloadFromExchange( boolean downloadFromExchange) throws RaplaException;


}