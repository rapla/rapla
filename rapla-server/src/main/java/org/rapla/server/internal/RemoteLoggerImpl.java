package org.rapla.server.internal;

import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.endpoints.RemoteLogger;

import org.springframework.beans.factory.annotation.Autowired;

public class RemoteLoggerImpl implements RemoteLogger
{
    @Autowired
    Logger logger;

    @Autowired
    public RemoteLoggerImpl()
    {
    }

    @Override
    public void info(String id, String message) throws RaplaException
    {
        if (id == null)
        {
            String message2 = "Id missing in logging call";
            logger.error(message2);
            throw new RaplaException(message);
        }
        Logger childLogger = logger.getChildLogger(id);
        childLogger.info(message);
    }

}
