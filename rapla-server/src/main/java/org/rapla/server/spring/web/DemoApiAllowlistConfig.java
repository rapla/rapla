package org.rapla.server.spring.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.servlet.filter.OrderedFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * PRD 118 D8-3 — registers {@link DemoApiAllowlistFilter} under the {@code demo} profile only, after
 * {@link LegacyPathFilterConfig}'s rewrite and ahead of the Spring Security chain, so a closed path
 * gets the same 404 with or without credentials.
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoApiAllowlistConfig
{
    @Bean
    public FilterRegistrationBean<DemoApiAllowlistFilter> demoApiAllowlistFilter()
    {
        FilterRegistrationBean<DemoApiAllowlistFilter> registration = new FilterRegistrationBean<>(new DemoApiAllowlistFilter());
        registration.setOrder(OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 105);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
