package org.rapla.server.spring.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * PRD 041 + PRD 035: redirect bare {@code /swagger-ui} and {@code /graphiql}
 * URLs (with or without trailing slash) to their respective {@code index.html}
 * pages.
 *
 * <p>Spring Boot's static-resource handler only auto-resolves the welcome page
 * for the root {@code /} path; sub-directory welcome files don't get the same
 * treatment. Without these redirects, visiting {@code /swagger-ui/} or
 * {@code /graphiql} returns 404.
 *
 * <p>Spring for GraphQL's bundled GraphiQL launcher is disabled
 * ({@code spring.graphql.graphiql.enabled=false}) — we ship our own at
 * {@code static/graphiql/index.html} with OAuth2 PKCE login wired in
 * (mirrors the Swagger UI pattern). See PRD 035.
 *
 * <p><b>PRD 071 Phase 3:</b> both {@code /swagger-ui/**} and {@code /graphiql/**}
 * now require an authenticated session ({@code SecurityConfig} gates them with
 * {@code .authenticated()}). These redirect targets are covered by that gate too,
 * so a bare {@code /swagger-ui} / {@code /graphiql} from an anonymous browser is
 * rejected (401) before the redirect — the page cannot leak. An authenticated
 * browser (form-login / SPA-OAuth JSESSIONID) gets the redirect as before.
 */
@Configuration
public class ExplorerRedirects implements WebMvcConfigurer
{
    @Override
    public void addViewControllers(ViewControllerRegistry registry)
    {
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui/", "/swagger-ui/index.html");
        registry.addRedirectViewController("/graphiql", "/graphiql/index.html");
        registry.addRedirectViewController("/graphiql/", "/graphiql/index.html");
    }
}
