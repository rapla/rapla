package org.rapla.server.spring.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * PRD 041: redirect bare {@code /scalar} and {@code /swagger-ui} URLs (with or
 * without trailing slash) to their respective {@code index.html} pages.
 *
 * <p>Spring Boot's static-resource handler only auto-resolves the welcome page
 * for the root {@code /} path; sub-directory welcome files don't get the same
 * treatment. Without these redirects, visiting {@code /scalar} or
 * {@code /swagger-ui/} returns 404.
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
    }
}
