package org.rapla.storage;

import org.rapla.components.i18n.LocalePackage;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;
import java.util.Set;

/**
 * Returns synchronous types — Spring's HttpServiceProxyFactory has no built-in
 * adapter for {@code Promise<X>} (unlike {@code Mono/Flux}), so a Promise-typed
 * proxy method makes Jackson try to deserialize the response body INTO a
 * Promise instance and fail (Promise is an interface). Callers wanting async
 * dispatch should wrap the call site in {@code commandScheduler.supply(() -> ...)}.
 */
@HttpExchange("/api/locale")
public interface RemoteLocaleService
{
    @GetExchange("/{id}")
    LocalePackage locale(@PathVariable("id") String id, @RequestParam(value = "locale", required = false) String locale);

    @PostExchange
    Map<String, Set<String>> countries(@RequestBody Set<String> languages);
}
