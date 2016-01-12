package org.rapla.plugin.jndi.internal;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

@RemoteJsonMethod
public interface JNDIConfig 
{
    void test(DefaultConfiguration config, String username, String password) throws RaplaException;
    DefaultConfiguration getConfig() throws RaplaException;
}
