package org.rapla.server.spring;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.web.DemoApiAllowlistConfig;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD 118 D8-3a — the allowlist is switched on by {@code rapla.api-allowlist.enabled}, not by a profile, and a
 * dangerous entry stops the start with a message naming the property and the entry (no full server).
 */
class ApiAllowlistValidationTest
{
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RaplaServerProperties.class)
    static class BoundProperties
    {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BoundProperties.class, DemoApiAllowlistConfig.class);

    @Test
    void offByDefault()
    {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void enabledWithAnEmptyOpenListStarts()
    {
        runner.withPropertyValues("rapla.api-allowlist.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(FilterRegistrationBean.class));
    }

    @Test
    void eachInvalidEntryKindFailsTheStart()
    {
        for (String entry : List.of("/", "/api", "/api/", "/api/*", "/api/documents/..", "/app/"))
        {
            for (String property : List.of("open-prefixes", "open-paths"))
            {
                runner.withPropertyValues("rapla.api-allowlist.enabled=true", "rapla.api-allowlist." + property + "[0]=" + entry)
                        .run(context -> {
                            assertThat(context).as(property + " " + entry).hasFailed();
                            assertThat(context.getStartupFailure()).rootCause()
                                    .hasMessageContaining("rapla.api-allowlist." + property)
                                    .hasMessageContaining("\"" + entry + "\"");
                        });
            }
        }
    }

    @Test
    void anEmptyEntryFailsTheStart()
    {
        runner.withPropertyValues("rapla.api-allowlist.enabled=true", "rapla.api-allowlist.open-prefixes[0]=/api/users",
                        "rapla.api-allowlist.open-prefixes[1]=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("rapla.api-allowlist.open-prefixes");
                });
    }

    /** Review R1 — the guard set is code, not configuration: a narrowed guard list must not make /api fail-open. */
    private static void apiStaysGuarded(AssertableApplicationContext context) throws Exception
    {
        assertThat(context).hasNotFailed();
        Filter filter = context.getBean(FilterRegistrationBean.class).getFilter();
        for (String path : List.of("/api/storage/dispatch", "/api/favorites", "/raplaclient.jnlp", "/webclient/rapla.jar"))
        {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(chain.getRequest()).as(path + " must stay closed").isNull();
            assertThat(response.getStatus()).as(path).isEqualTo(404);
        }
    }

    @Test
    void theGuardSetCannotBeNarrowedByYamlStyleProperties()
    {
        runner.withPropertyValues("rapla.api-allowlist.enabled=true", "rapla.api-allowlist.open-prefixes[0]=/api/users",
                        "rapla.api-allowlist.guarded-prefixes[0]=/webclient", "rapla.api-allowlist.guarded-paths[0]=/nothing")
                .run(ApiAllowlistValidationTest::apiStaysGuarded);
    }

    @Test
    void theGuardSetCannotBeNarrowedByEnvironmentVariables()
    {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("RAPLA_APIALLOWLIST_ENABLED", "true", "RAPLA_APIALLOWLIST_OPENPREFIXES_0", "/api/users",
                                "RAPLA_APIALLOWLIST_GUARDEDPREFIXES_0", "/webclient", "RAPLA_APIALLOWLIST_GUARDEDPATHS_0", "/nothing"))))
                .run(ApiAllowlistValidationTest::apiStaysGuarded);
    }
}
