package org.rapla.plugin.jndi.internal;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;

@WebService
public interface JNDITest 
{
    public void test(String config,String username,String password) throws RaplaException;
}
