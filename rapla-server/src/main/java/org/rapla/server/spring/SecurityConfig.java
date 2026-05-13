package org.rapla.server.spring;

import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.internal.RaplaTokenRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
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
public class SecurityConfig
{
    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            ObjectProvider<JwtDecoder> jwtDecoderProvider,
                                            RememberMeServices rememberMeServices) throws Exception
    {
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/**", "/", "/index", "/server", "/static/**", "/*.html", "/*.css",
                            "/images/**", "/webclient/**", "/app/**",
                            "/api/logger/**", "/api/ical/timezones/**",
                            "/rapla/calendar", "/rapla/calendar.csv",
                            "/rapla/internal_calendar", "/rapla/internal_calendar.csv",
                            "/rapla/ical", "/rapla/internal_ical",
                            "/raplaclient", "/raplaclient.jnlp",
                            "/api/v3/api-docs/**", "/v3/api-docs/**",
                            "/swagger-ui/**", "/swagger-ui.html",
                            "/oauth2/**", "/.well-known/**", "/login", "/error",
                            "/dhbw/status").permitAll();
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
    public PersistentTokenRepository rememberMeTokenRepository(RaplaFacade facade, Logger logger)
    {
        return new RaplaTokenRepository(facade, logger);
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
