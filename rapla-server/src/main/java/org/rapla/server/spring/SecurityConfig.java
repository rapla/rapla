package org.rapla.server.spring;

import org.rapla.facade.RaplaFacade;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.internal.RaplaTokenRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity   // enables @PreAuthorize / @PostAuthorize on @Controller and @Bean methods
public class SecurityConfig
{
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
                                            RememberMeServices rememberMeServices) throws Exception
    {
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        http
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
                    auth.requestMatchers("/api/auth/**", "/", "/index", "/server", "/static/**", "/*.html", "/*.css",
                            "/images/**", "/webclient/**", "/app/**",
                            "/api/logger/**", "/api/ical/timezones/**",
                            "/rapla/calendar", "/rapla/calendar.csv",
                            "/rapla/internal_calendar", "/rapla/internal_calendar.csv",
                            "/rapla/ical", "/rapla/internal_ical",
                            "/raplaclient", "/raplaclient.jnlp",
                            "/api/v3/api-docs/**", "/v3/api-docs/**",
                            "/scalar/**", "/swagger-ui/**", "/graphiql/**",
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
                            "/oauth2/**", "/.well-known/**", "/login", "/error",
                            // dhbwrapla plugin endpoints. /dhbw/status is the
                            // dhbw-specific health probe; /api/dhbw/stele is
                            // the terminal-display XML feed polled by
                            // unauthenticated terminal hardware (scope is
                            // server-side via rapla.dhbw.terminal.stele-user).
                            "/dhbw/status", "/api/dhbw/stele").permitAll();
                    if (decoder != null)
                    {
                        auth.anyRequest().authenticated();
                    }
                    else
                    {
                        auth.anyRequest().permitAll();
                    }
                })
                .formLogin(form -> form.loginPage("/login").permitAll())
                .rememberMe(rm -> rm.rememberMeServices(rememberMeServices))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());
        if (decoder != null)
        {
            http.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)));
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
    public CorsConfigurationSource corsConfigurationSource()
    {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
