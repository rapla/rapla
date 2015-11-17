package org.rapla.plugin.exchangeconnector;

import java.util.List;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

@RemoteJsonMethod
public interface ExchangeConnectorConfigRemote 
{
	public DefaultConfiguration getConfig() throws RaplaException;
	public List<String> getTimezones() throws RaplaException;

}