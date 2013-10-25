package org.rapla.storage.dbrm;

import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;

public interface ConnectorFactory
{
    Connector create(Configuration config) throws RaplaException;
    
}
