package org.rapla.server.spring.web;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RemoteLocaleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping("/locale")
public class RemoteLocaleController
{
    private final RemoteLocaleService service;

    public RemoteLocaleController(RemoteLocaleService service)
    {
        this.service = service;
    }

    @GetMapping("/{id}")
    public LocalePackage locale(@PathVariable("id") String id,
                                @RequestParam(value = "locale", required = false) String locale) throws Exception
    {
        Promise<LocalePackage> promise = service.locale(id, locale);
        return SynchronizedCompletablePromise.waitFor(promise, 10000, null);
    }

    @PostMapping
    public Map<String, Set<String>> countries(@RequestBody Set<String> languages) throws Exception
    {
        Promise<Map<String, Set<String>>> promise = service.countries(languages);
        return SynchronizedCompletablePromise.waitFor(promise, 10000, null);
    }
}
