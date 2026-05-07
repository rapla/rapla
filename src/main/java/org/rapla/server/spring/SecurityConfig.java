package org.rapla.server.spring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig
{
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<JwtDecoder> jwtDecoderProvider) throws Exception
    {
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/auth/**", "/", "/index", "/server", "/static/**", "/*.html", "/*.css",
                            "/Rapla/**", "/images/**", "/webclient/**", "/jsclient/**",
                            "/logger/**", "/ical/timezones/**",
                            "/calendar", "/calendar.csv", "/internal_calendar", "/internal_calendar.csv",
                            "/ical", "/internal_ical",
                            "/raplaclient", "/raplaclient.jnlp").permitAll();
                    if (decoder != null)
                    {
                        auth.anyRequest().authenticated();
                    }
                    else
                    {
                        auth.anyRequest().permitAll();
                    }
                })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults());
        if (decoder != null)
        {
            http.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)));
        }
        return http.build();
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
