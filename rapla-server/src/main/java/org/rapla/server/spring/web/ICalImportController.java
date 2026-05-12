package org.rapla.server.spring.web;

import org.rapla.framework.RaplaException;
import org.rapla.plugin.ical.ICalImport;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean({ICalImport.class, RemoteSession.class})
@RequestMapping(value = "/ical/import", produces = "application/json")
public class ICalImportController
{
    private final ICalImport service;

    public ICalImportController(ICalImport service)
    {
        this.service = service;
    }

    @PostMapping
    public Integer[] importICal(@RequestBody ICalImport.Import job) throws RaplaException
    {
        return service.importICal(job);
    }
}
