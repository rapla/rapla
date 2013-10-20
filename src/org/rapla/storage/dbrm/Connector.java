package org.rapla.storage.dbrm;

import java.io.IOException;
import java.util.Map;

import org.rapla.framework.RaplaException;

public interface Connector
{
    String getInfo();

    String call( String methodName, Map<String,String> args) throws IOException, RaplaException;
    
}
