package org.rapla.client.spring;

import org.rapla.RaplaResources;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.rest.JacksonObjectMapperFactory;
import org.rapla.storage.dbrm.OAuth2PasswordLogin;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.TokenStore;
import org.rapla.storage.dbrm.TokenStores;

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
    public HttpServiceProxyFactory httpServiceProxyFactory(RestClient.Builder builder, RemoteConnectionInfo info,
                                                           TokenStore tokenStore)
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
                .requestInterceptor(new RefreshOn401Interceptor(info, requestFactory, tokenStore))
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
        private final TokenStore tokenStore;
        private final java.util.concurrent.locks.Lock refreshLock = new java.util.concurrent.locks.ReentrantLock();

        public RefreshOn401Interceptor(RemoteConnectionInfo info,
                                       org.springframework.http.client.ClientHttpRequestFactory requestFactory)
        {
            this(info, requestFactory, TokenStores.noOp());
        }

        public RefreshOn401Interceptor(RemoteConnectionInfo info,
                                       org.springframework.http.client.ClientHttpRequestFactory requestFactory,
                                       TokenStore tokenStore)
        {
            this.info = info;
            this.requestFactory = requestFactory;
            this.tokenStore = tokenStore == null ? TokenStores.noOp() : tokenStore;
        }

        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException
        {
            interceptCount.incrementAndGet();
            // PRD 051: use effective access token (impersonation if set,
            // else admin's). getAccessToken() is the admin token, read
            // separately below by the renewal/refresh paths.
            String token = info.getEffectiveAccessToken();
            if (token != null && !token.isEmpty()) request.getHeaders().setBearerAuth(token);
            org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
            if (response.getStatusCode().value() != 401) return response;
            refreshAttempts.incrementAndGet();
            // Don't recurse on /api/auth/* itself.
            if (request.getURI().getPath().contains("/api/auth/")) return response;

            // PRD 051 — impersonation renewal precedes the refresh chain.
            // If we're impersonating and got 401, the impersonation
            // token expired; try to renew it via /api/auth/impersonate.
            // If that itself 401s (admin's access also stale), fall into
            // the refresh-then-retry path which refreshes the admin
            // token, then we retry impersonation, then retry the
            // original request.
            if (info.isImpersonating())
            {
                if (tryRenewImpersonation())
                {
                    response.close();
                    request.getHeaders().setBearerAuth(info.getEffectiveAccessToken());
                    return execution.execute(request, body);
                }
                // Fall through — impersonation renewal failed, probably
                // because admin's access token also stale. Refresh admin
                // (below), then re-attempt impersonation renewal, then
                // retry the original request.
            }

            String refresh = info.getRefreshToken();
            if (refresh == null || refresh.isEmpty())
            {
                // Nothing to refresh with — session is dead. Signal the higher
                // layers so the user gets a re-login dialog instead of a silently
                // failing calendar (see RemoteConnectionInfo.onAuthDead).
                fireAuthDeadOnce();
                return response;
            }
            // Single-flight refresh — concurrent 401-failed requests share one /auth/refresh hit.
            refreshLock.lock();
            boolean refreshOk;
            try
            {
                String currentToken = info.getAccessToken();
                // Compare to admin's stored token: refresh is about renewing
                // the admin token, not the impersonation override.
                if (java.util.Objects.equals(currentToken, info.getAccessToken()))
                {
                    refreshOk = doRefresh(refresh);
                }
                else
                {
                    // Another thread already refreshed — proceed with retry.
                    refreshOk = true;
                }
            }
            finally
            {
                refreshLock.unlock();
            }
            if (!refreshOk)
            {
                // Both the access token AND the stored refresh token are dead.
                // Clear them so subsequent calls don't keep spinning on the same
                // broken pair, and notify the Swing UI to re-show the login dialog.
                info.setAccessToken(null);
                info.setRefreshToken(null);
                info.clearImpersonationToken();
                fireAuthDeadOnce();
                return response;
            }
            // Admin token refreshed. If we were impersonating, try the
            // impersonation-renewal again now that the admin Bearer is
            // valid. If that still fails, fall through to retry with the
            // admin token (which will likely 403 on the original request
            // — at which point the audit log explains why and the user
            // sees the dialog via fireAuthDeadOnce).
            if (info.isImpersonating())
            {
                tryRenewImpersonation();
            }
            response.close();
            request.getHeaders().setBearerAuth(info.getEffectiveAccessToken());
            return execution.execute(request, body);
        }

        /**
         * Call POST /api/auth/impersonate with the admin's current
         * access token and the stashed target username. On success,
         * updates the impersonation slot in {@link RemoteConnectionInfo}
         * and returns true. On failure (any non-2xx), returns false —
         * the caller decides whether to clear the impersonation or fall
         * into the admin-refresh path. Errors are swallowed so the
         * request-thread doesn't surface them; the next outbound
         * request will trigger another attempt.
         */
        private boolean tryRenewImpersonation()
        {
            // adminToken() — explicit "give me the admin bearer, ignoring any
            // active impersonation override". Identical to getAccessToken() but
            // names the intent at the call site (parity with Angular's
            // AuthService.adminToken()).
            String adminToken = info.adminToken();
            if (adminToken == null || adminToken.isEmpty()) return false;
            String target = info.getImpersonationTargetUsername();
            if (target == null || target.isEmpty()) return false;
            String baseUrl = info.getServerURL();
            if (baseUrl == null || baseUrl.isEmpty()) return false;
            String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            java.net.URI uri = java.net.URI.create(trimmed + "/api/auth/impersonate");
            try
            {
                org.springframework.http.client.ClientHttpRequest req = requestFactory.createRequest(
                        uri, org.springframework.http.HttpMethod.POST);
                req.getHeaders().setBearerAuth(adminToken);
                req.getHeaders().set("Content-Type", "application/x-www-form-urlencoded");
                req.getHeaders().set("Accept", "application/json");
                String reqBody = "target_username=" + java.net.URLEncoder.encode(
                        target, java.nio.charset.StandardCharsets.UTF_8);
                try (java.io.OutputStream out = req.getBody())
                {
                    out.write(reqBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                try (org.springframework.http.client.ClientHttpResponse renewResp = req.execute())
                {
                    if (renewResp.getStatusCode().value() / 100 != 2) return false;
                    String json = new String(renewResp.getBody().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    String newToken = extractJsonStringField(json, "access_token");
                    if (newToken == null || newToken.isEmpty()) return false;
                    info.setImpersonationAccessToken(newToken, target);
                    return true;
                }
            }
            catch (Exception ex)
            {
                return false;
            }
        }

        private void fireAuthDeadOnce()
        {
            Runnable hook = info.getOnAuthDead();
            if (hook == null) return;
            // Detach so the hook can't fire repeatedly during a 401-storm.
            info.setOnAuthDead(null);
            try { hook.run(); } catch (RuntimeException ignored) { /* don't break the request thread */ }
        }

        private boolean doRefresh(String refreshToken) throws java.io.IOException
        {
            // PRD 041: refresh via OAuth2-standard /oauth2/token grant_type=refresh_token
            // (form-encoded body, snake_case response). Replaces the rapla-custom
            // JSON /api/auth/refresh path.
            // PRD 072 Phase 5: rapla is Swing's single federating Authorization
            // Server, so refresh ALWAYS targets rapla's own /oauth2/token with
            // client_id=rapla-client — every Swing login (rapla-password or
            // rapla-brokered Keycloak/Google/MS) yields a rapla-issuer token, so
            // there is no external provider token endpoint to route to. Same
            // shape as MyCustomConnector.refreshUsingToken().
            String baseUrl = info.getServerURL();
            if (baseUrl == null || baseUrl.isEmpty()) return false;
            String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String tokenUrl = trimmed + "/oauth2/token";
            String clientId = "rapla-client";
            String encoded = java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8);
            String encodedClientId = java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8);
            byte[] reqBody = ("grant_type=refresh_token&refresh_token=" + encoded
                    + "&client_id=" + encodedClientId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            org.springframework.http.client.ClientHttpRequest req =
                    requestFactory.createRequest(java.net.URI.create(tokenUrl), org.springframework.http.HttpMethod.POST);
            req.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
            req.getBody().write(reqBody);
            try (org.springframework.http.client.ClientHttpResponse resp = req.execute())
            {
                if (resp.getStatusCode().value() != 200) return false;
                String json = new String(resp.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String newAccess = extractJsonStringField(json, "access_token");
                String newRefresh = extractJsonStringField(json, "refresh_token");
                if (newAccess == null) return false;
                info.setAccessToken(newAccess);
                if (newRefresh != null)
                {
                    info.setRefreshToken(newRefresh);
                    // Mirror MyCustomConnector.refreshUsingToken(): persist the rotated
                    // refresh token so cold restart can silent-reauth. Without this,
                    // the on-disk token goes stale the first time a Keycloak realm
                    // with refresh-token rotation rotates mid-session.
                    tokenStore.tryWrite(newRefresh);
                }
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
    public org.rapla.plugin.mail.MailConfigService mailConfigServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.plugin.mail.MailConfigService.class);
    }

    /**
     * PRD 051 — admin "switch to user". The proxy lets
     * {@link org.rapla.client.swing.internal.RaplaClientServiceImpl#switchTo}
     * call {@code POST /api/auth/impersonate} via the same Spring
     * RestClient machinery used by every other typed proxy. Bearer
     * attachment is via {@link RefreshOn401Interceptor}, which reads
     * {@code info.getEffectiveAccessToken()} — i.e. the impersonation
     * token if set, otherwise the admin's access token. The renewal
     * path (impersonation expired → call impersonate again) is also
     * handled inside the interceptor.
     */
    @Bean
    public org.rapla.storage.dbrm.ImpersonationService impersonationServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.storage.dbrm.ImpersonationService.class);
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
    public OAuth2PasswordLogin oauth2PasswordLogin(RemoteConnectionInfo info, RaplaResources i18n)
    {
        // PRD 041: the rapla-custom POST /api/auth/login endpoint was removed
        // with AuthController. Direct username/password login — the Swing
        // fallback dialog and ClientFacadeImpl.login (test bootstrap) — now
        // goes through the OAuth 2.0 token endpoint. See docs/authentication.md.
        // PRD 029 Phase 5 (2026-05-25): dropped the RemoteAuthentificationService
        // interface; one method, one impl, no plugin point.
        return new OAuth2PasswordLogin(info, i18n);
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
