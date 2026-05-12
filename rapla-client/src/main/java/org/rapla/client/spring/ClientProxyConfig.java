package org.rapla.client.spring;

import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.rest.JacksonObjectMapperFactory;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Phase 5 — REST client proxies via {@link HttpServiceProxyFactory}.
 *
 * <p>Each remote service interface gets a {@code @Bean} that returns
 * a Spring-generated proxy. The same interface is implemented server-side
 * as a {@code @RestController} (Phase 1.6). Bearer auth + retry-on-401
 * is wired via {@code RestClient.Builder} interceptors (TODO Phase 5
 * step 2).
 *
 * <p>Currently demonstrates the pattern for a single read-only,
 * unauthenticated endpoint (`ICalTimezones`). The full set of proxies
 * (`RemoteStorage`, `RaplaResources` REST page, etc.) follows the same
 * recipe — the interfaces are already declared with `@GetMapping`-style
 * annotations on the server side, but the client requires the JAX-RS
 * `@Path`/`@GET` style on the *interface* to be replaced by Spring's
 * `@HttpExchange` / `@GetExchange`. That migration is staged separately;
 * the bean below uses the "manual" proxy registration which already
 * works against any server endpoint.
 */
@Configuration
public class ClientProxyConfig
{
    @Bean
    public RestClient.Builder restClientBuilder()
    {
        return RestClient.builder();
    }

    @Bean
    public RemoteConnectionInfo remoteConnectionInfo()
    {
        return new RemoteConnectionInfo();
    }

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(RestClient.Builder builder, RemoteConnectionInfo info)
    {
        // The server URL is set by RaplaClientServiceImpl after context refresh,
        // so we cannot freeze a baseUrl at @Bean factory time. Use a custom
        // UriBuilderFactory that reads info.getServerURL() lazily on each request.
        org.springframework.web.util.UriBuilderFactory dynamicFactory =
                new DynamicBaseUriBuilderFactory(info, "http://localhost:8051");
        // Force Jackson with the shared Rapla config (field-based, transient-aware).
        // Without this, RestClient picks up the default Jackson 3 mapper which calls all
        // getters and re-enters the resolver → cycle / StackOverflowError.
        // (java.time.* support is built into Jackson 3 — no JavaTimeModule registration needed.)
        JacksonJsonHttpMessageConverter jacksonConverter =
                new JacksonJsonHttpMessageConverter(JacksonObjectMapperFactory.create());
        // URLConnection-based factory instead of the JDK HttpClient default.
        // The async JDK HttpClient path fails with "java.io.IOException: selector
        // manager closed" when called from the Swing client's commandScheduler
        // worker thread (raplascheduler-N) — the daemon SelectorManager
        // terminates between request submission and execution.
        //
        // Spring's stock SimpleClientHttpRequestFactory unconditionally enables
        // HttpURLConnection streaming mode, which causes the JDK to discard the
        // error-stream body on 401 responses (HttpRetryException path). The Swing
        // login dialog then sees an empty body instead of the server's i18n'd
        // "Login failed!". BufferingHttpUrlConnectionRequestFactory keeps the
        // URLConnection path but buffers the request body so streaming mode
        // never gets enabled — the 401 body survives.
        org.springframework.http.client.ClientHttpRequestFactory requestFactory =
                new BufferingHttpUrlConnectionRequestFactory();
        RestClient restClient = builder
                .requestFactory(requestFactory)
                .uriBuilderFactory(dynamicFactory)
                .messageConverters(converters -> converters.add(0, jacksonConverter))
                .requestInterceptor(new RefreshOn401Interceptor(info, requestFactory))
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
    }

    /**
     * Adds the bearer token to every outgoing request, then on 401 calls
     * {@code POST /auth/refresh} with the stored refresh token, updates
     * {@link RemoteConnectionInfo#setAccessToken}, and retries the request once.
     * Avoids the user having to re-login when the JWT TTL (default 1h) elapses
     * mid-session. If the refresh itself returns 401 the original 401 is propagated
     * (caller can prompt for re-login).
     *
     * <p>Refreshes are skipped for the auth endpoint itself ({@code /auth/...}) to
     * avoid an infinite recursion if the refresh token is also invalid.
     */
    public static class RefreshOn401Interceptor implements org.springframework.http.client.ClientHttpRequestInterceptor
    {
        /** Test instrumentation. Counts every intercepted request and every refresh attempt
         *  so integration tests can assert "refresh fired exactly once". Production code
         *  should never read these. */
        public static final java.util.concurrent.atomic.AtomicInteger interceptCount = new java.util.concurrent.atomic.AtomicInteger();
        public static final java.util.concurrent.atomic.AtomicInteger refreshAttempts = new java.util.concurrent.atomic.AtomicInteger();
        private final RemoteConnectionInfo info;
        private final org.springframework.http.client.ClientHttpRequestFactory requestFactory;
        private final java.util.concurrent.locks.Lock refreshLock = new java.util.concurrent.locks.ReentrantLock();

        RefreshOn401Interceptor(RemoteConnectionInfo info,
                                org.springframework.http.client.ClientHttpRequestFactory requestFactory)
        {
            this.info = info;
            this.requestFactory = requestFactory;
        }

        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException
        {
            interceptCount.incrementAndGet();
            String token = info.getAccessToken();
            if (token != null && !token.isEmpty()) request.getHeaders().setBearerAuth(token);
            org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
            if (response.getStatusCode().value() != 401) return response;
            refreshAttempts.incrementAndGet();
            // Don't recurse on /auth/* itself.
            if (request.getURI().getPath().contains("/auth/")) return response;
            String refresh = info.getRefreshToken();
            if (refresh == null || refresh.isEmpty()) return response;
            // Single-flight refresh — concurrent 401-failed requests share one /auth/refresh hit.
            refreshLock.lock();
            try
            {
                String currentToken = info.getAccessToken();
                if (java.util.Objects.equals(currentToken, token))
                {
                    if (!doRefresh(refresh)) return response;
                }
            }
            finally
            {
                refreshLock.unlock();
            }
            response.close();
            request.getHeaders().setBearerAuth(info.getAccessToken());
            return execution.execute(request, body);
        }

        private boolean doRefresh(String refreshToken) throws java.io.IOException
        {
            String baseUrl = info.getServerURL();
            if (baseUrl == null || baseUrl.isEmpty()) return false;
            // baseUrl already includes the /rapla context path (set by RaplaClientServiceImpl
            // from rapla.download.url + the configured context). Just append the auth path.
            String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String refreshUrl = trimmed.endsWith("/rapla") ? trimmed + "/auth/refresh"
                                                           : trimmed + "/rapla/auth/refresh";
            byte[] reqBody = ("{\"refreshToken\":\"" + refreshToken + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            org.springframework.http.client.ClientHttpRequest req =
                    requestFactory.createRequest(java.net.URI.create(refreshUrl), org.springframework.http.HttpMethod.POST);
            req.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            req.getBody().write(reqBody);
            try (org.springframework.http.client.ClientHttpResponse resp = req.execute())
            {
                if (resp.getStatusCode().value() != 200) return false;
                String json = new String(resp.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String newAccess = extractJsonStringField(json, "accessToken");
                String newRefresh = extractJsonStringField(json, "refreshToken");
                if (newAccess == null) return false;
                info.setAccessToken(newAccess);
                if (newRefresh != null) info.setRefreshToken(newRefresh);
                return true;
            }
        }

        /** Tiny string-grep extractor so we don't pull a Jackson mapper in here just to read 2 fields. */
        private static String extractJsonStringField(String json, String field)
        {
            String key = "\"" + field + "\"";
            int k = json.indexOf(key);
            if (k < 0) return null;
            int colon = json.indexOf(':', k + key.length());
            if (colon < 0) return null;
            int q1 = json.indexOf('"', colon + 1);
            if (q1 < 0) return null;
            int q2 = json.indexOf('"', q1 + 1);
            if (q2 < 0) return null;
            return json.substring(q1 + 1, q2);
        }
    }

    /**
     * UriBuilderFactory that reads the base URL from {@link RemoteConnectionInfo}
     * at each request, not at @Bean factory time. This lets {@link org.rapla.client.swing.internal.RaplaClientServiceImpl}
     * set {@code serverURL} after context refresh and have all REST proxies
     * pick up the correct base URL on the next call.
     */
    static class DynamicBaseUriBuilderFactory extends org.springframework.web.util.DefaultUriBuilderFactory
    {
        private final RemoteConnectionInfo info;
        private final String fallbackBaseUrl;

        DynamicBaseUriBuilderFactory(RemoteConnectionInfo info, String fallbackBaseUrl)
        {
            this.info = info;
            this.fallbackBaseUrl = fallbackBaseUrl;
        }

        private String currentBase()
        {
            String url = info.getServerURL();
            if (url == null || url.isEmpty()) return fallbackBaseUrl;
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }

        @Override
        public org.springframework.web.util.UriBuilder uriString(String uriTemplate)
        {
            // The URI template from @HttpExchange is something like "/authentication".
            // Prepend the current base URL.
            if (uriTemplate.startsWith("/"))
            {
                uriTemplate = currentBase() + uriTemplate;
            }
            return super.uriString(uriTemplate);
        }

        @Override
        public org.springframework.web.util.UriBuilder builder()
        {
            return super.uriString(currentBase());
        }
    }

    @Bean
    public ICalTimezones iCalTimezonesProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(ICalTimezones.class);
    }

    @Bean
    public org.rapla.storage.RemoteLocaleService remoteLocaleServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.storage.RemoteLocaleService.class);
    }

    @Bean
    public org.rapla.plugin.export2ical.ICalConfigService iCalConfigServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.export2ical.ICalConfigService.class);
    }

    @Bean
    public org.rapla.plugin.mail.MailToUserInterface mailToUserProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.mail.MailToUserInterface.class);
    }

    @Bean
    public org.rapla.plugin.mail.MailConfigService mailConfigServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.mail.MailConfigService.class);
    }

    @Bean
    public org.rapla.plugin.archiver.ArchiverService archiverServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.archiver.ArchiverService.class);
    }

    @Bean
    public org.rapla.rest.PluginsService pluginsServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.rest.PluginsService.class);
    }

    @Bean
    public org.rapla.rest.SettingsService settingsServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.rest.SettingsService.class);
    }

    @Bean
    public org.rapla.plugin.eventtimecalculator.EventTimeCalculatorConfigService eventTimeCalculatorConfigServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.eventtimecalculator.EventTimeCalculatorConfigService.class);
    }

    @Bean
    public org.rapla.plugin.urlencryption.UrlEncryption urlEncryptionProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.urlencryption.UrlEncryption.class);
    }

    @Bean
    public org.rapla.plugin.export2ical.ICalExport iCalExportProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.export2ical.ICalExport.class);
    }

    @Bean
    public org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote exchangeConnectorRemoteProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote.class);
    }

    @Bean
    public org.rapla.plugin.exchangeconnector.ExchangeConnectorConfigRemote exchangeConnectorConfigRemoteProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.exchangeconnector.ExchangeConnectorConfigRemote.class);
    }

    @Bean
    public org.rapla.plugin.jndi.internal.JNDIConfig jndiConfigProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.jndi.internal.JNDIConfig.class);
    }

    @Bean
    public org.rapla.plugin.ical.ICalImport iCalImportProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.ical.ICalImport.class);
    }

    @Bean
    public org.rapla.plugin.eventimport.TemplateImport templateImportProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.eventimport.TemplateImport.class);
    }

    @Bean
    public org.rapla.endpoints.RemoteLogger remoteLoggerProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.endpoints.RemoteLogger.class);
    }

    @Bean
    public org.rapla.plugin.tableview.TableViewService tableViewServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.tableview.TableViewService.class);
    }

    @Bean
    public org.rapla.storage.dbrm.RemoteAuthentificationService remoteAuthentificationServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.storage.dbrm.RemoteAuthentificationService.class);
    }

    @Bean
    public org.rapla.storage.dbrm.RemoteStorage remoteStorageProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.storage.dbrm.RemoteStorage.class);
    }

    @Bean
    public org.rapla.plugin.adminpanels.PreferencesAdminService preferencesAdminServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.adminpanels.PreferencesAdminService.class);
    }

    @Bean
    public org.rapla.plugin.externaleventimport.ExternalEventImportService externalEventImportServiceProxy(HttpServiceProxyFactory factory)
    {
        // PRD 012: the wizard's controller (gated by rapla.externalevents.enabled)
        // injects this. Without the proxy, enabling the flag crashes context
        // refresh with NoSuchBeanDefinitionException at login.
        return factory.createClient(org.rapla.plugin.externaleventimport.ExternalEventImportService.class);
    }

    @Bean
    public org.rapla.plugin.calendarview.CalendarViewService calendarViewServiceProxy(HttpServiceProxyFactory factory)
    {
        // PRD 024 Phase 3: server-side calendar layout. Swing client doesn't
        // call this in-flow yet (the in-process RaplaBuilder path is still
        // the renderer source), but the proxy is registered so a future
        // migration or a worktree-side experiment can resolve the bean.
        return factory.createClient(org.rapla.plugin.calendarview.CalendarViewService.class);
    }

    @Bean
    public org.rapla.plugin.reservationedit.ReservationEditService reservationEditServiceProxy(HttpServiceProxyFactory factory)
    {
        // PRD 024 Phase 1: server-authoritative recurrence-rule validation.
        // Wraps the pure-Java RepeatingRuleValidator from rapla-core. Swing
        // client keeps calling the validator in-process; this proxy exists
        // for the future Angular client.
        return factory.createClient(org.rapla.plugin.reservationedit.ReservationEditService.class);
    }

    // RestartServer has no @Bean here — RemoteOperator implements RestartServer directly
    // (legacy @DefaultImplementation annotation; honored by ClientConfig.remoteOperator() bean).
    // Adding a separate HTTP proxy here would create a NoUniqueBeanDefinitionException since
    // both beans match the same interface type.
}
