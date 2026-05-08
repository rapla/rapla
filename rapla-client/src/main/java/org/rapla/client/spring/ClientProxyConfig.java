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
        RestClient restClient = builder
                .uriBuilderFactory(dynamicFactory)
                .messageConverters(converters -> converters.add(0, jacksonConverter))
                .requestInitializer(request -> {
                    String token = info.getAccessToken();
                    if (token != null && !token.isEmpty())
                    {
                        request.getHeaders().setBearerAuth(token);
                    }
                })
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
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
    public org.rapla.storage.dbrm.RemoteAuthentificationService remoteAuthentificationServiceProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.storage.dbrm.RemoteAuthentificationService.class);
    }

    @Bean
    public org.rapla.storage.dbrm.RemoteStorage remoteStorageProxy(HttpServiceProxyFactory factory)
    {
        return factory.createClient(org.rapla.storage.dbrm.RemoteStorage.class);
    }

    // RestartServer has no @Bean here — RemoteOperator implements RestartServer directly
    // (legacy @DefaultImplementation annotation; honored by ClientConfig.remoteOperator() bean).
    // Adding a separate HTTP proxy here would create a NoUniqueBeanDefinitionException since
    // both beans match the same interface type.
}
