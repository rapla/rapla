package org.rapla.plugin.jndi.internal;

import org.rapla.framework.RaplaException;

public interface JNDITest {
    
    public void test(String config,String username,String password) throws RaplaException;
}
