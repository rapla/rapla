package org.rapla.server.spring.web;

import org.rapla.endpoints.RemoteLogger;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RemoteLoggerController implements RemoteLogger
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteLoggerController.class);

    public RemoteLoggerController()
    {
    }

    @Override
    public void info(String id, String message) throws RaplaException
    {
        if (id == null)
        {
            String message2 = "Id missing in logging call";
            LOGGER.error(message2);
            throw new RaplaException(message);
        }
        LoggerFactory.getLogger("rapla." + id).info(message);
    }
}
