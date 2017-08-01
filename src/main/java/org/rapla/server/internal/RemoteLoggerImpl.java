package org.rapla.server.internal;

import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.rest.RemoteLogger;

import javax.inject.Inject;

@DefaultImplementation(context = InjectionContext.server, of = RemoteLogger.class)
public class RemoteLoggerImpl implements RemoteLogger
{
    @Inject
    Logger logger;

    @Inject
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
