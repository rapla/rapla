package org.rapla.client;

import java.util.List;

import org.rapla.ConnectInfo;
import org.rapla.framework.Container;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.TypedComponentRole;

public interface ClientServiceContainer extends Container 
{
    TypedComponentRole<List<PluginDescriptor<ClientServiceContainer>>> CLIENT_PLUGIN_LIST = new TypedComponentRole<List<PluginDescriptor<ClientServiceContainer>>>("client-plugin-list");
	void start(ConnectInfo connectInfo) throws Exception;
    //void addCompontentOnClientStart(Class<ClientExtension> componentToStart);
    boolean isRunning();
}
