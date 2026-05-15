package org.rapla.endpoints;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange("/api/logger")
public interface RemoteLogger
{
    @PutExchange("/{id}")
    void info(@PathVariable("id") String id, @RequestBody String message) throws RaplaException;
}
