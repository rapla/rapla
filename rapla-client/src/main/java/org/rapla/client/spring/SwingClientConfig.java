package org.rapla.client.spring;

import org.rapla.client.event.TaskPresenter;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 1 of PRD 002 (Swing UI DI migration).
 *
 * <p>Component-scans the Swing UI packages so Spring picks up classes that
 * carry {@code @Service} alongside their legacy {@code @DefaultImplementation}
 * annotation. Adding the {@code @Service} annotation is the work of Phase 2 —
 * with this config alone in scope, the scan finds nothing yet and produces
 * an empty Swing tier.
 */
@Configuration
@ComponentScan(
        basePackages = {
                "org.rapla.client",
                "org.rapla.plugin"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = ".*\\.server\\..*"
        )
)
public class SwingClientConfig
{
    /**
     * Spring's auto-injection of {@code Map<String, T>} populates from beans of type {@code T},
     * but does NOT auto-wrap the values into {@code Supplier<T>} when {@code T} itself is
     * {@code Supplier<X>} — that wrapping is only applied at top-level injection points,
     * not inside nested generics. {@link org.rapla.client.Application#activityPresenters}
     * declares {@code Map<String, Supplier<TaskPresenter>>}, so without this bean it stays
     * empty and {@code startAction("cal", true)} silently returns false.
     */
    @Bean
    public Map<String, Supplier<TaskPresenter>> activityPresenters(ListableBeanFactory beanFactory)
    {
        Map<String, Supplier<TaskPresenter>> map = new LinkedHashMap<>();
        for (String name : beanFactory.getBeanNamesForType(TaskPresenter.class))
        {
            map.put(name, () -> beanFactory.getBean(name, TaskPresenter.class));
        }
        return map;
    }

    /**
     * Same wrapping as {@link #activityPresenters} but for {@code EditComponent}.
     * {@link org.rapla.client.internal.edit.swing.EditTaskViewSwing} declares
     * {@code Map<String, Supplier<EditComponent>>} keyed by entity-type class name.
     * Each editor (e.g. {@code PreferencesEditUI}, {@code DynamicTypeEditUI}) is
     * registered with {@code @Service("<typeClass.getName()>")}; without this bean
     * the map injects empty and clicking "edit" on those types throws
     * {@code RuntimeException("Can't edit objects of type …")}.
     */
    @Bean
    public Map<String, Supplier<org.rapla.client.swing.EditComponent>> editUiProvider(ListableBeanFactory beanFactory)
    {
        Map<String, Supplier<org.rapla.client.swing.EditComponent>> map = new LinkedHashMap<>();
        for (String name : beanFactory.getBeanNamesForType(org.rapla.client.swing.EditComponent.class))
        {
            map.put(name, () -> beanFactory.getBean(name, org.rapla.client.swing.EditComponent.class));
        }
        return map;
    }
}
