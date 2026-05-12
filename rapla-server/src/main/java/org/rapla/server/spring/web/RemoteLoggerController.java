package org.rapla.server.spring.web;

import org.rapla.endpoints.RemoteLogger;
import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/logger", produces = "application/json")
public class RemoteLoggerController
{
    private final RemoteLogger remoteLogger;

    public RemoteLoggerController(RemoteLogger remoteLogger)
    {
        this.remoteLogger = remoteLogger;
    }

    @PutMapping("/{id}")
    public void info(@PathVariable("id") String id, @RequestBody String message) throws RaplaException
    {
        remoteLogger.info(id, message);
    }
}
