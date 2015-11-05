package org.rapla.plugin.jndi.internal;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

@RemoteJsonMethod
public interface JNDIConfig 
{
    public void test(DefaultConfiguration config,String username,String password) throws RaplaException;
    public DefaultConfiguration getConfig() throws RaplaException;
}
