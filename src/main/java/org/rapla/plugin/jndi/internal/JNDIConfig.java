package org.rapla.plugin.jndi.internal;

import javax.jws.WebService;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

@WebService
public interface JNDIConfig 
{
    public void test(DefaultConfiguration config,String username,String password) throws RaplaException;
    public DefaultConfiguration getConfig() throws RaplaException;
}
