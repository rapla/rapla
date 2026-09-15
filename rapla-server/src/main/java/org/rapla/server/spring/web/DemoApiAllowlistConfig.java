package org.rapla.server.spring.web;

import org.rapla.server.spring.RaplaServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.servlet.filter.OrderedFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PRD 118 D8-3/D8-3a — registers {@link DemoApiAllowlistFilter} when {@code rapla.api-allowlist.enabled=true} (the
 * demo sets it in {@code application-demo.yml}), after {@link LegacyPathFilterConfig}'s rewrite and ahead of the
 * Spring Security chain, so a closed path gets the same 404 with or without credentials. Invalid entries stop the start.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "rapla.api-allowlist", name = "enabled", havingValue = "true")
public class DemoApiAllowlistConfig
{
    @Bean
    public FilterRegistrationBean<DemoApiAllowlistFilter> demoApiAllowlistFilter(RaplaServerProperties properties)
    {
        RaplaServerProperties.ApiAllowlist lists = properties.getApiAllowlist();
        DemoApiAllowlistFilter.validate(lists);
        FilterRegistrationBean<DemoApiAllowlistFilter> registration = new FilterRegistrationBean<>(new DemoApiAllowlistFilter(lists));
        registration.setOrder(OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 105);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
