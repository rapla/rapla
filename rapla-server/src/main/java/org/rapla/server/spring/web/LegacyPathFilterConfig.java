package org.rapla.server.spring.web;

import org.rapla.server.spring.RaplaServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.servlet.filter.OrderedFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PRD 109 — registers {@link LegacyPathFilter} ahead of the Spring Security chain.
 *
 * <p>The order matters: Spring Security's filter chain is registered at
 * {@code OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 100} (= -100), and the rewrite
 * has to happen <em>before</em> it so the security matchers see the canonical path —
 * otherwise {@code ${prefix}/rapla/internal_calendar} would bypass its authentication
 * rule.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rapla.legacy-context-path")
public class LegacyPathFilterConfig
{
    @Bean
    public FilterRegistrationBean<LegacyPathFilter> legacyPathFilter(RaplaServerProperties properties)
    {
        FilterRegistrationBean<LegacyPathFilter> registration =
                new FilterRegistrationBean<>(new LegacyPathFilter(properties.getLegacyContextPath()));
        registration.setOrder(OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 110);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
