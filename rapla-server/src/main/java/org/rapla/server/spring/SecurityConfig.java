package org.rapla.server.spring;

import org.rapla.facade.RaplaFacade;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.internal.RaplaTokenRepository;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity   // enables @PreAuthorize / @PostAuthorize on @Controller and @Bean methods
public class SecurityConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Dedicated chain for the rapla OAuth helper endpoints
     * ({@code /api/auth/oauth/exchange/*}, {@code /api/auth/oauth/config}).
     * These are pre-authentication endpoints — the SPA calls them to obtain
     * a fresh token or discover available providers. They must never run the
     * resource-server JWT filter, otherwise a stale Bearer left in the SPA's
     * storage from a prior session is decoded, fails, and the request gets
     * 401 before the controller can run (the "stale-JWT-blocks-OAuth-login"
     * regression, 2026-05-21). The {@code permitAll} on the main chain is
     * NOT sufficient — Spring's {@code BearerTokenAuthenticationFilter}
     * rejects invalid tokens regardless of authorization rules. The only
     * reliable carve-out is a separate chain that never installs the filter.
     *
     * Ordered ahead of the main chain ({@link #filterChain}, order=2) so
     * Spring picks this one for {@code /api/auth/oauth/**} traffic.
     */
    /**
     * PRD 072 Phase 1/4 — the form-login (username + password) success TAIL:
     * mint a rapla JWT + set the credential cookies so the browser form login
     * yields a working credential-model-A session (not just a JSESSIONID the
     * stateless {@code /api} ignores). {@link RefreshSessionService} +
     * {@link CookieAuthSupport} are always present alongside this server-side
     * {@code SecurityConfig}, so the bean is unconditional (a {@code @ConditionalOnBean}
     * on a plain {@code @Configuration} evaluates unreliably against
     * component-scanned beans). {@code filterChain} consumes it via {@code ObjectProvider}.
     */
    @Bean
    public FormLoginSuccessHandler formLoginSuccessHandler(
            RaplaFacade facade,
            RefreshSessionService refreshSessionService,
            CookieAuthSupport cookies,
            @Value("${rapla.oauth.web.login-success-redirect:/app/}") String redirectAfterLogin)
    {
        return new FormLoginSuccessHandler(facade, refreshSessionService, cookies, redirectAfterLogin);
    }

    @Bean
    @org.springframework.core.annotation.Order(0)
    public SecurityFilterChain oauthHelperFilterChain(HttpSecurity http) throws Exception
    {
        http
                .securityMatcher("/api/auth/oauth/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            ObjectProvider<JwtDecoder> jwtDecoderProvider,
                                            RememberMeServices rememberMeServices,
                                            ExternalProvidersProperties externalProviders,
                                            LoginRateLimitFilter loginRateLimitFilter,
                                            ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> clientRegistrationRepositoryProvider,
                                            ObjectProvider<org.rapla.server.spring.oauth.OidcLoginSuccessHandler> oidcSuccessHandlerProvider,
                                            ObjectProvider<FormLoginSuccessHandler> formLoginSuccessHandlerProvider) throws Exception
    {
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrations =
                clientRegistrationRepositoryProvider.getIfAvailable();
        // PRD 071 Phase 2: connect-src derived from the configured OAuth IdP endpoints
        // (zero CSP-specific config — the IdP base-url is already set for OAuth).
        java.util.List<String> idpEndpoints = externalProviders.enabledProviders().stream()
                .flatMap(p -> java.util.stream.Stream.of(p.issuer(), p.authorizeUrl(), p.tokenUrl(), p.jwksUrl()))
                .toList();
        String csp = CspPolicyBuilder.build(idpEndpoints);
        LOGGER.info("CSP — /api/** + /rapla/** ENFORCED (default-src 'none'); SPA/login report-only: {}", csp);
        // PRD 072 Phase 2/3: one CookieCsrfTokenRepository shared by the csrf config
        // and CsrfCookieFilter (eager XSRF-TOKEN materialization).
        org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfTokenRepository =
                org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse();
        http
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    // PRD 043: api-keys management requires a real Bearer access token —
                    // must come BEFORE the broader /api/auth/** permit-all rule.
                    auth.requestMatchers("/api/auth/api-keys", "/api/auth/api-keys/**").authenticated();
                    // PRD 051: admin "switch to user" — requires a valid Bearer
                    // (any issuer) belonging to a user who can canAdminUser the
                    // target. The controller runs the per-target authorization
                    // check; this matcher only enforces "must be authenticated".
                    auth.requestMatchers("/api/auth/impersonate").authenticated();
                    // PRD 051: GET /api/users returns the list of users the
                    // caller can admin. Requires auth; the controller
                    // applies canAdminUser per entry server-side.
                    auth.requestMatchers("/api/users", "/api/users/**").authenticated();
                    // PRD 071 Phase 3: the API-explorer pages (Swagger UI +
                    // GraphiQL) are static HTML that, in a browser, can only be
                    // gated by the form-login session/remember-me cookie (the
                    // SPA's localStorage Bearer is NOT sent on a navigation GET).
                    // An unauthenticated GET is rejected by the chain's active
                    // authentication entry point — 401 here, because
                    // oauth2ResourceServer().jwt() installs the Bearer entry
                    // point. The OAuth2 SPA login establishes a JSESSIONID
                    // (the /oauth2/authorize flow
                    // signs in via form-login), so a dev who has signed into the
                    // SPA carries a session that satisfies this gate. The
                    // underlying /v3/api-docs + /api/graphql/schema specs stay
                    // public above — only the human-facing explorer UIs are
                    // gated. Bare /swagger-ui + /graphiql (ExplorerRedirects)
                    // are covered by the /** glob; the 302→index.html redirect
                    // itself requires auth, so it can't leak the page either.
                    auth.requestMatchers("/swagger-ui", "/swagger-ui/**", "/graphiql", "/graphiql/**").authenticated();
                    auth.requestMatchers("/api/auth/**", "/", "/index", "/server", "/static/**", "/*.html", "/*.css",
                            "/images/**", "/webclient/**", "/app/**",
                            "/api/logger/**", "/api/ical/timezones/**",
                            "/rapla/calendar", "/rapla/calendar.csv",
                            "/rapla/internal_calendar", "/rapla/internal_calendar.csv",
                            "/rapla/ical", "/rapla/internal_ical",
                            "/raplaclient", "/raplaclient.jnlp",
                            "/api/v3/api-docs/**", "/v3/api-docs/**",
                            // PRD 035 testbed: GraphQL endpoint open while
                            // resolvers expose only trivial public data
                            // (hello, serverTime, version). Tighten to
                            // .authenticated() when real PRD 035 resolvers
                            // land — every read/write field is §12-gated
                            // server-side regardless of transport auth.
                            "/api/graphql",
                            // GraphQL schema printer (spring.graphql.schema.printer.enabled)
                            // — the SDL pendant to the public /v3/api-docs above. API
                            // shape metadata only, no entity data; public to match Swagger.
                            "/api/graphql/schema",
                            // PRD 072: the oauth2Login() server-side callback. The
                            // bare "/login" entry below does NOT cover the deeper
                            // "/login/oauth2/code/<id>" callback path (review S1) —
                            // without this the IdP redirect-back would hit the
                            // authenticated() gate and bounce. "/oauth2/**" already
                            // covers the "/oauth2/authorization/<id>" kickoff.
                            "/login/oauth2/code/**",
                            "/oauth2/**", "/.well-known/**", "/login", "/error").permitAll();
                    // Vanilla rapla carries no plugin-specific paths here. A plugin
                    // that needs an unauthenticated endpoint (e.g. a health probe or
                    // a kiosk display feed polled without a Bearer) contributes its
                    // own higher-precedence SecurityFilterChain — @Order(1), ahead
                    // of this catch-all (@Order 2) — with a narrow securityMatcher,
                    // exactly like oauthHelperFilterChain above. First-match-wins:
                    // any path the plugin chain does NOT match falls through here
                    // and inherits the authenticated() gate below. Pattern + the
                    // regression lock: PluginSecurityChainContributionTest.
                    if (decoder != null)
                    {
                        auth.anyRequest().authenticated();
                    }
                    else
                    {
                        auth.anyRequest().permitAll();
                    }
                })
                .formLogin(form -> {
                    form.loginPage("/login").permitAll();
                    // PRD 072 Phase 1/4: run the rapla TAIL on form-login success so
                    // the browser gets the credential cookies (not just a session the
                    // stateless /api ignores). Only present in server mode.
                    FormLoginSuccessHandler formSuccess = formLoginSuccessHandlerProvider.getIfAvailable();
                    if (formSuccess != null)
                    {
                        form.successHandler(formSuccess);
                    }
                })
                .rememberMe(rm -> rm.rememberMeServices(rememberMeServices))
                .headers(headers -> headers
                        // PRD 071 Phase 2: path-scoped CSP — /api/** and /rapla/**
                        // (calendar pages) get strict ENFORCED default-src 'none';
                        // the SPA + login fall through to report-only (Material walk).
                        .addHeaderWriter(new RaplaCspHeaderWriter(csp))
                        .referrerPolicy(r -> r.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(fo -> fo.deny()))
                // PRD 072 Phase 2 (review B1): CSRF protection fires ONLY for
                // cookie-authenticated mutating requests — i.e. a request that
                // carries the access_token cookie AND no Authorization header.
                // Header-Bearer clients (Swing / iCal / API-keys) and the
                // current SPA's plain-session POSTs (no access_token cookie)
                // stay exempt. The double-submit token lives in a
                // JS-readable XSRF-TOKEN cookie (Angular HttpClient reads it).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        // Raw (non-XOR) token handler so the value the browser reads from
                        // the XSRF-TOKEN cookie matches the X-XSRF-TOKEN header it submits.
                        .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(new CookieAuthCsrfMatcher()))
                // PRD 072 Phase 3 fix: eagerly materialize the XSRF-TOKEN cookie on
                // safe GETs so the double-submit header can be populated before the
                // first cookie-auth POST (else GraphiQL/SPA mutations 403). Shares the
                // SAME repository so CsrfFilter validates against the same cookie value.
                .addFilterAfter(new CsrfCookieFilter(csrfTokenRepository),
                        org.springframework.security.web.csrf.CsrfFilter.class)
                .cors(Customizer.withDefaults());

        // PRD 072 Phase 1 — server-side oauth2Login() HEAD. Only wired when at
        // least one external provider is configured (the ClientRegistrationRepository
        // carries >=1 registration). The repository bean is ALWAYS published
        // (oauth2-client auto-config requires it), but is empty for vanilla rapla
        // — in which case we skip oauth2Login() so behaviour is unchanged.
        if (!org.rapla.server.spring.oauth.RaplaClientRegistrationConfig.isEmpty(clientRegistrations))
        {
            org.rapla.server.spring.oauth.OidcLoginSuccessHandler successHandler =
                    oidcSuccessHandlerProvider.getIfAvailable();
            http.oauth2Login(oauth -> {
                oauth.loginPage("/login");
                oauth.clientRegistrationRepository(clientRegistrations);
                if (successHandler != null)
                {
                    oauth.successHandler(successHandler);
                }
            });
        }

        // PRD 072 Phase 1 — pin the AuthenticationEntryPoint explicitly. Adding
        // oauth2Login() would otherwise install a LoginUrlAuthenticationEntryPoint
        // that 302s EVERY unauthenticated request (including JSON /api/** that the
        // SPA + explorers expect a 401 for) to /login. Route by Accept: an HTML
        // browser navigation gets 302 /login; everything else (JSON / Bearer /
        // XHR / curl) gets 401. NOTE (review SF2): in the default chain composition
        // the resource-server's own BearerTokenAuthenticationEntryPoint already
        // yields 401 for /api/**, so this pin is defensive/redundant today — it
        // makes the 401 contract explicit + order-independent for future refactors.
        MediaTypeRequestMatcher htmlEntryMatcher =
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        htmlEntryMatcher.setUseEquals(false);
        htmlEntryMatcher.setIgnoredMediaTypes(java.util.Set.of(MediaType.ALL));
        http.exceptionHandling(exc -> {
            exc.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    htmlEntryMatcher);
            exc.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE);
        });

        if (decoder != null)
        {
            // PRD 072 Phase 2: the cookie credential reaches the resource-server
            // bearer filter via CookieToBearerFilter (installed after CsrfFilter,
            // below) which promotes the access_token cookie to a synthetic
            // Authorization: Bearer header. The resource server keeps its default
            // HEADER-ONLY resolver, so its automatic Not[BearerTokenRequestMatcher]
            // CSRF exclusion does NOT exempt cookie requests (review B1) — those
            // are gated by CookieAuthCsrfMatcher instead. The decoder chain still
            // applies the same typ=refresh / typ=api_key rejection.
            http.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)));
            // Promote cookie → Bearer AFTER CsrfFilter so CSRF sees the original
            // (cookie, no Authorization) request shape.
            http.addFilterAfter(new CookieToBearerFilter(),
                    org.springframework.security.web.csrf.CsrfFilter.class);
        }
        return http.build();
    }

    /**
     * Promoted to an explicit bean so the OIDC logout endpoint in
     * {@code AuthorizationServerConfig} can wrap it as a {@code LogoutHandler}
     * — necessary because Spring SAS's {@code OidcLogoutAuthenticationSuccessHandler}
     * only clears the HttpSession/SecurityContext and does NOT consult
     * {@code RememberMeServices}. Without this wiring, a successful
     * {@code /connect/logout} leaves the {@code rapla-remember-me} cookie alive
     * and the next {@code /oauth2/authorize} silently re-authenticates via the
     * surviving cookie ("sign out → instantly signed back in" bug).
     */
    @Bean
    public RememberMeServices rememberMeServices(PersistentTokenRepository repo,
                                                 UserDetailsService userDetailsService,
                                                 ObjectProvider<RaplaKeyStorage> keyStorageProvider,
                                                 @Value("${rapla.auth.remember-me-days:30}") int rememberMeDays)
    {
        RaplaKeyStorage keyStorage = keyStorageProvider.getIfAvailable();
        String key = keyStorage != null
                ? keyStorage.getRootKeyBase64()
                : "rapla-remember-me-fallback-key-not-persistent";
        PersistentTokenBasedRememberMeServices services =
                new PersistentTokenBasedRememberMeServices(key, userDetailsService, repo);
        services.setTokenValiditySeconds(rememberMeDays * 24 * 60 * 60);
        services.setParameter("remember-me");
        services.setCookieName("rapla-remember-me");
        return services;
    }

    /**
     * Server-side store for remember-me tokens, backed by Rapla's system
     * preferences. A cookie issued before a server restart still validates
     * after the JVM comes back up — matching the persistent-JWK pattern used
     * by {@link RaplaKeyStorage}.
     */
    @Bean
    public PersistentTokenRepository rememberMeTokenRepository(RaplaFacade facade)
    {
        return new RaplaTokenRepository(facade);
    }

    @Bean
    public LoginAttemptTracker loginAttemptTracker()
    {
        return new LoginAttemptTracker();
    }

    @Bean
    public LoginRateLimitFilter loginRateLimitFilter(LoginAttemptTracker tracker)
    {
        return new LoginRateLimitFilter(tracker);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${rapla.cors.allowed-origins:}") List<String> configuredOrigins)
    {
        // A2: reflect ONLY allowed origins — never a wildcard-with-credentials
        // (which echoed any caller's Origin, incl. evil.com, with credentials).
        // All production traffic is same-origin and never triggers a CORS check,
        // so this restrictive policy doesn't touch it; CORS is only exercised by
        // the dev SPA (localhost:4200 / WSL bridge) hitting /oauth2/* cross-origin.
        // Extra origins (split deployments) go in rapla.cors.allowed-origins.
        final java.util.Set<String> extras = configuredOrigins == null
                ? java.util.Set.of()
                : configuredOrigins.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .collect(java.util.stream.Collectors.toSet());
        return request -> {
            String origin = request.getHeader("Origin");
            if (origin == null || !CorsOriginPolicy.isAllowedOrigin(origin, extras))
            {
                return null; // not a CORS request, or origin not allowed → no CORS headers
            }
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of(origin)); // reflect exactly the validated origin
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);
            return config;
        };
    }
}
