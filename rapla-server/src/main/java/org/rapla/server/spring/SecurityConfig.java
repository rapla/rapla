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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
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
                                            ObjectProvider<RaplaKeyStorage> keyStorageProvider,
                                            PersistentTokenRepository rememberMeTokenRepository,
                                            @Value("${rapla.auth.remember-me-days:30}") int rememberMeDays) throws Exception
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
                .rememberMe(rm -> {
                    RaplaKeyStorage keyStorage = keyStorageProvider.getIfAvailable();
                    String key = keyStorage != null
                            ? keyStorage.getRootKeyBase64()
                            : "rapla-remember-me-fallback-key-not-persistent";
                    rm.key(key)
                      .tokenRepository(rememberMeTokenRepository)
                      .tokenValiditySeconds(rememberMeDays * 24 * 60 * 60)
                      .rememberMeParameter("remember-me")
                      .rememberMeCookieName("rapla-remember-me");
                })
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());
        if (decoder != null)
        {
            http.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)));
        }
        return http.build();
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
