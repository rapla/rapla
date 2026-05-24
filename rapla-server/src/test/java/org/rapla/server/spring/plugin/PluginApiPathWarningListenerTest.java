package org.rapla.server.spring.plugin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exampleplugin.PluginTestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD 045 Phase 5 — runtime safety net for plugin REST endpoints.
 *
 * <p>AGENTS.md §15 says every {@code @RestController} must map under {@code /api/}.
 * Stock rapla controllers are caught at build time by {@code ApiPrefixArchitectureTest}.
 * Drop-in plugin jars are not in the rapla-app build, so they need a runtime check.
 *
 * <p>The listener walks all {@code RequestMappingHandlerMapping} entries after context
 * refresh; any handler whose declaring class is outside {@code org.rapla.*} (i.e. a
 * plugin) and maps to a path not under {@code /api/} gets a WARN log line. The handler
 * still works — this is a footgun warning, not a hard rejection.
 */
class PluginApiPathWarningListenerTest
{
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender()
    {
        appender = new ListAppender<>();
        appender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PluginApiPathWarningListener.class))
                .addAppender(appender);
    }

    @AfterEach
    void detachAppender()
    {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PluginApiPathWarningListener.class))
                .detachAppender(appender);
        appender.stop();
    }

    @Test
    void warnsWhenPluginControllerMapsOutsideApi()
    {
        StaticApplicationContext ctx = contextWithMapping(PluginTestFixtures.BadPathPluginController.class);
        new PluginApiPathWarningListener().onApplicationEvent(new ContextRefreshedEvent(ctx));

        List<ILoggingEvent> warns = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warns).hasSize(1);
        assertThat(warns.get(0).getFormattedMessage())
                .contains("/admin/hello")
                .contains("BadPathPluginController");
    }

    @Test
    void silentWhenPluginControllerMapsUnderApi()
    {
        StaticApplicationContext ctx = contextWithMapping(PluginTestFixtures.GoodPathPluginController.class);
        new PluginApiPathWarningListener().onApplicationEvent(new ContextRefreshedEvent(ctx));

        long warns = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
        assertThat(warns).isZero();
    }

    @Test
    void silentForStockRaplaControllersEvenOutsideApi()
    {
        StaticApplicationContext ctx = contextWithMapping(StockRaplaController.class);
        new PluginApiPathWarningListener().onApplicationEvent(new ContextRefreshedEvent(ctx));

        long warns = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
        assertThat(warns)
                .as("Stock rapla controllers (org.rapla.*) are caught at build time by "
                        + "ApiPrefixArchitectureTest — runtime listener only flags plugin classes "
                        + "in non-rapla packages")
                .isZero();
    }

    /**
     * Bypasses {@code @EnableWebMvc} (which would need a ServletContext) and
     * directly registers a single controller into a fresh
     * {@link RequestMappingHandlerMapping}.
     */
    private static StaticApplicationContext contextWithMapping(Class<?> controller)
    {
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.refresh();
        ctx.getBeanFactory().registerSingleton(controller.getName(), instantiate(controller));

        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.setApplicationContext(ctx);
        mapping.afterPropertiesSet();
        ctx.getBeanFactory().registerSingleton("requestMappingHandlerMapping", mapping);
        return ctx;
    }

    private static Object instantiate(Class<?> controller)
    {
        try
        {
            return controller.getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Top-level class in {@code org.rapla.server.spring.plugin} so its FQCN starts
     * with {@code org.rapla.*} — emulates a stock rapla controller.
     */
    @RestController
    @RequestMapping("/legacy")
    static class StockRaplaController
    {
        @GetMapping("/hello") public String hello() { return "x"; }
    }
}
