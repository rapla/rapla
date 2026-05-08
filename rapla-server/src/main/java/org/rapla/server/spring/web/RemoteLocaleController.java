package org.rapla.server.spring.web;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.RemoteLocaleServiceImpl;
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
    private final RemoteLocaleServiceImpl service;

    public RemoteLocaleController(RemoteLocaleServiceImpl service)
    {
        this.service = service;
    }

    @GetMapping("/{id}")
    public LocalePackage locale(@PathVariable("id") String id,
                                @RequestParam(value = "locale", required = false) String locale) throws RaplaException
    {
        return service.localeSync(id, locale);
    }

    @PostMapping
    public Map<String, Set<String>> countries(@RequestBody Set<String> languages)
    {
        return service.countriesSync(languages);
    }
}
