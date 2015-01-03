package org.rapla.plugin.jndi.internal;

import javax.jws.WebService;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.rest.gwtjsonrpc.common.RemoteJsonService;

@WebService
public interface JNDIConfig extends RemoteJsonService
{
    public void test(DefaultConfiguration config,String username,String password) throws RaplaException;
    public DefaultConfiguration getConfig() throws RaplaException;
}
