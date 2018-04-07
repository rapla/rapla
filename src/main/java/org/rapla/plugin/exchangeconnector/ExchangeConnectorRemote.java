package org.rapla.plugin.exchangeconnector;

import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("exchange/connect")
public interface ExchangeConnectorRemote 
{
    TypedComponentRole<String> LAST_SYNC_ERROR_CHANGE = new TypedComponentRole<>("org.rapla.plugin.exchangconnector.last_sync_error_change");
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    SynchronizationStatus getSynchronizationStatus() throws RaplaException;

    @POST
    @Path("synchronize")
    void synchronize() throws RaplaException;

	/** 
	 * Add an Exchange user to the user list (register a user to the Exchange Server)
	 * (User wants to have his Exchange Account synchronized with the Rapla system)
	 * 
	 * @param exchangeUsername  
	 * @param exchangePassword
	 * @return {@link ClientMessage}
	 * @throws RaplaException
	 */
	@POST
	void changeUser(@QueryParam("user")String exchangeUsername, String exchangePassword/*, Boolean downloadFromExchange*/) throws RaplaException;

	@POST
	@Path("remove")
	void removeUser() throws RaplaException;
	
	@POST
	@Path("retry")
	void retry() throws RaplaException;
	
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