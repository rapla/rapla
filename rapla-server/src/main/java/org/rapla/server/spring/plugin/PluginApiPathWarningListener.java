package org.rapla.server.spring.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runtime safety net for the PRD 045 §4 / Phase 5 plugin contract.
 *
 * <p>AGENTS.md §15 mandates that every {@code @RestController} maps under
 * {@code /api/}. Stock rapla controllers are policed at build time by
 * {@code ApiPrefixArchitectureTest}. Drop-in plugin jars are not in the
 * rapla-app build — so the architecture test cannot see them and they would
 * otherwise be able to expose endpoints outside the contract.
 *
 * <p>After the application context refreshes, this listener walks every
 * {@code RequestMappingHandlerMapping} bean, identifies handlers contributed
 * by classes outside {@code org.rapla.*} (i.e. plugins), and logs WARN for
 * any whose URL pattern is not under {@code /api/}. The handler still works
 * — this is a footgun warning, not a hard rejection.
 *
 * <p>Plugins that map outside {@code /api/} won't be visible in the
 * SpringDoc-generated SPA TypeScript client and won't get the standard
 * {@code /api/} Spring Security configuration. The warning makes that drift
 * obvious at boot rather than at first 401.
 */
@Component
public class PluginApiPathWarningListener implements ApplicationListener<ContextRefreshedEvent>
{
    private static final Logger LOG = LoggerFactory.getLogger(PluginApiPathWarningListener.class);

    private static final String STOCK_RAPLA_PACKAGE_PREFIX = "org.rapla.";
    // Spring framework / Spring Boot ship controllers as infrastructure
    // (e.g. BasicErrorController auto-registered at /error by
    // spring-boot-webmvc-autoconfigure; Actuator endpoints when enabled).
    // These are not third-party "plugins" and shouldn't trigger the warning.
    private static final String SPRING_FRAMEWORK_PACKAGE_PREFIX = "org.springframework.";
    private static final String API_PREFIX = "/api/";
    private static final String API_ROOT = "/api";

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event)
    {
        Map<String, RequestMappingHandlerMapping> mappings =
                event.getApplicationContext().getBeansOfType(RequestMappingHandlerMapping.class);
        for (RequestMappingHandlerMapping mapping : mappings.values())
        {
            mapping.getHandlerMethods().forEach((info, method) -> {
                String declaringClass = method.getBeanType().getName();
                if (declaringClass.startsWith(STOCK_RAPLA_PACKAGE_PREFIX)
                        || declaringClass.startsWith(SPRING_FRAMEWORK_PACKAGE_PREFIX))
                {
                    return;
                }
                for (String pattern : patternsOf(info))
                {
                    if (!pattern.startsWith(API_PREFIX) && !pattern.equals(API_ROOT))
                    {
                        LOG.warn(
                                "Plugin handler {}#{} maps to '{}' — AGENTS.md §15 requires plugin REST endpoints under /api/*. "
                                        + "The handler will still work but won't appear in the SpringDoc-generated SPA client.",
                                declaringClass, method.getMethod().getName(), pattern);
                    }
                }
            });
        }
    }

    private static Set<String> patternsOf(RequestMappingInfo info)
    {
        // Spring 6+ uses PathPatterns; the legacy String-pattern condition is null in that mode.
        if (info.getPathPatternsCondition() != null)
        {
            return info.getPathPatternsCondition().getPatterns().stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        if (info.getPatternsCondition() != null)
        {
            return info.getPatternsCondition().getPatterns();
        }
        return Set.of();
    }
}
