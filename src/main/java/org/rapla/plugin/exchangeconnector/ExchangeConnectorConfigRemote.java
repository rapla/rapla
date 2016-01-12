package org.rapla.plugin.exchangeconnector;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

import java.util.List;

@RemoteJsonMethod
public interface ExchangeConnectorConfigRemote 
{
	DefaultConfiguration getConfig() throws RaplaException;
	List<String> getTimezones() throws RaplaException;

}