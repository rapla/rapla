package org.rapla.server.spring.web;

import org.rapla.framework.RaplaException;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.plugin.archiver.server.ArchiverServiceImpl;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean({ArchiverService.class, RemoteSession.class})
@RequestMapping(value = "/archiver", produces = "application/json")
public class ArchiverController
{
    private final ArchiverServiceImpl service;

    public ArchiverController(ArchiverServiceImpl service)
    {
        this.service = service;
    }

    @PostMapping
    public void delete(@RequestParam(value = "olderThanInDays", required = false) Integer olderThanInDays) throws RaplaException
    {
        service.deleteSync(olderThanInDays);
    }

    @GetMapping
    public boolean isExportEnabled() throws RaplaException
    {
        return service.isExportEnabled();
    }

    @PostMapping("/backup")
    public void backupNow() throws RaplaException
    {
        service.backupNowSync();
    }

    @PostMapping("/restore")
    public void restore() throws RaplaException
    {
        service.restoreSync();
    }
}
