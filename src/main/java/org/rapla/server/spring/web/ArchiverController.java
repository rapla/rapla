package org.rapla.server.spring.web;

import org.rapla.framework.RaplaException;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean({ArchiverService.class, RemoteSession.class})
@RequestMapping("/archiver")
public class ArchiverController
{
    private final ArchiverService service;

    public ArchiverController(ArchiverService service)
    {
        this.service = service;
    }

    @PostMapping
    public void delete(@RequestParam(value = "olderThanInDays", required = false) Integer olderThanInDays) throws Exception
    {
        SynchronizedCompletablePromise.waitFor(service.delete(olderThanInDays), 60000, null);
    }

    @GetMapping
    public boolean isExportEnabled() throws RaplaException
    {
        return service.isExportEnabled();
    }

    @PostMapping("/backup")
    public void backupNow() throws Exception
    {
        SynchronizedCompletablePromise.waitFor(service.backupNow(), 60000, null);
    }

    @PostMapping("/restore")
    public void restore() throws Exception
    {
        SynchronizedCompletablePromise.waitFor(service.restore(), 60000, null);
    }
}
