package org.rapla.server.spring.web;

import org.rapla.endpoints.RemoteLogger;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RemoteLoggerController implements RemoteLogger
{
    private final Logger logger;

    public RemoteLoggerController(Logger logger)
    {
        this.logger = logger;
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
