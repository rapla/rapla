package org.rapla.server.spring.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * PRD 041 + PRD 035: redirect bare {@code /scalar}, {@code /swagger-ui}, and
 * {@code /graphiql} URLs (with or without trailing slash) to their respective
 * {@code index.html} pages.
 *
 * <p>Spring Boot's static-resource handler only auto-resolves the welcome page
 * for the root {@code /} path; sub-directory welcome files don't get the same
 * treatment. Without these redirects, visiting {@code /scalar},
 * {@code /swagger-ui/}, or {@code /graphiql} returns 404.
 *
 * <p>Spring for GraphQL's bundled GraphiQL launcher is disabled
 * ({@code spring.graphql.graphiql.enabled=false}) — we ship our own at
 * {@code static/graphiql/index.html} with OAuth2 PKCE login wired in
 * (mirrors the Swagger UI pattern). See PRD 035.
 */
@Configuration
public class ExplorerRedirects implements WebMvcConfigurer
{
    @Override
    public void addViewControllers(ViewControllerRegistry registry)
    {
        registry.addRedirectViewController("/scalar", "/scalar/index.html");
        registry.addRedirectViewController("/scalar/", "/scalar/index.html");
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui/", "/swagger-ui/index.html");
        registry.addRedirectViewController("/graphiql", "/graphiql/index.html");
        registry.addRedirectViewController("/graphiql/", "/graphiql/index.html");
    }
}
