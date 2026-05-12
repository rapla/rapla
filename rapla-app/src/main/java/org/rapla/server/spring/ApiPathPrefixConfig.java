package org.rapla.server.spring;

import java.util.Set;
import org.rapla.server.spring.web.CalendarPageController;
import org.rapla.server.spring.web.Export2iCalController;
import org.rapla.server.spring.web.IndexPageController;
import org.rapla.server.spring.web.RaplaJNLPController;
import org.rapla.server.spring.web.StatusPageController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * PRD 031 Phase 2 — adds the {@code /api} prefix to every Spring MVC controller
 * EXCEPT the explicit exclusions below.
 *
 * <p>Excluded (stay at root or are anchored elsewhere):
 * <ul>
 *   <li>{@link IndexPageController} — serves {@code /} and {@code /index} (the chooser landing page)</li>
 *   <li>{@link LoginPageController} — Spring form login at {@code /login}</li>
 *   <li>{@link CalendarPageController} — anchored at {@code /rapla/calendar*} for external iCal subscribers (HARD CONSTRAINT)</li>
 *   <li>{@link Export2iCalController} — anchored at {@code /rapla/(internal_)?ical} for external subscribers (HARD CONSTRAINT)</li>
 *   <li>{@link RaplaJNLPController} — JNLP launcher at {@code /raplaclient.jnlp}, {@code /webclient/{name}.jar}</li>
 *   <li>{@link StatusPageController} — {@code /server} status page</li>
 * </ul>
 *
 * <p>SpringDoc's {@code /v3/api-docs} and {@code /swagger-ui/**} aren't @RestController-driven, so
 * they're unaffected by this prefixing. JAX-RS resources (e.g. {@code RaplaAuthRestPage},
 * {@code RaplaIndexPageGenerator}) go through their own dispatcher and are likewise unaffected.
 */
@Configuration
public class ApiPathPrefixConfig implements WebMvcConfigurer
{
    private static final Set<Class<?>> EXCLUDED = Set.of(
            IndexPageController.class,
            LoginPageController.class,
            CalendarPageController.class,
            Export2iCalController.class,
            RaplaJNLPController.class,
            StatusPageController.class
    );

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer)
    {
        configurer.addPathPrefix("/api", clazz -> !EXCLUDED.contains(clazz));
    }
}
