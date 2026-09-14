package org.rapla.autoconfigtest;

import com.exampleplugin.ExamplePluginAutoConfiguration;
import com.exampleplugin.ExamplePluginAutoConfiguration.ExamplePluginBean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD 045 Phase 5 — drop-in plugin contract.
 *
 * <p>Verifies that a fixture {@code @AutoConfiguration} jar (under
 * {@code com.exampleplugin}, simulating a drop-in plugin) is discovered and its
 * {@code @Bean}s wired into the running context — and that the
 * {@code rapla.plugins.<id>.enabled=false} switch removes them again.
 *
 * <p>Uses {@link ApplicationContextRunner} with {@link AutoConfigurations#of} to
 * exercise the real {@code @AutoConfiguration} processing path without bundling
 * a jar or polluting other tests with {@code AutoConfiguration.imports}. The
 * end-to-end jar-on-classpath aggregation is already proven by
 * {@code AutoConfigImportTest} (PRD 003 / the rapla-server autoconfig itself).
 */
class DropInPluginContractTest
{
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExamplePluginAutoConfiguration.class));

    @Test
    void pluginBeanIsWiredByDefault()
    {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ExamplePluginBean.class);
            assertThat(ctx.getBean(ExamplePluginBean.class).greet())
                    .isEqualTo("hello from the example plugin");
        });
    }

    @Test
    void pluginCanBeDisabledViaConfiguration()
    {
        runner.withPropertyValues("rapla.plugins.example.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ExamplePluginBean.class));
    }

    @Test
    void pluginStaysOnWhenPropertyExplicitlyTrue()
    {
        runner.withPropertyValues("rapla.plugins.example.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ExamplePluginBean.class));
    }
}
