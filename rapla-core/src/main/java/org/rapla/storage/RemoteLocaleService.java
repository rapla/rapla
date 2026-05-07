package org.rapla.storage;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.scheduler.Promise;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;
import java.util.Set;

@HttpExchange("/locale")
public interface RemoteLocaleService
{
    @GetExchange("/{id}")
    Promise<LocalePackage> locale(@PathVariable("id") String id, @RequestParam(value = "locale", required = false) String locale);

    @PostExchange
    Promise<Map<String, Set<String>>> countries(@RequestBody Set<String> languages);
}
