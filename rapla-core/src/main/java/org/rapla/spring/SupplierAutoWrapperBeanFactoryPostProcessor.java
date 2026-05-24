package org.rapla.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Spring auto-wires {@code ObjectProvider<T>} and {@code jakarta.inject.Provider<T>} but NOT
 * {@code java.util.function.Supplier<T>}. PRD 002's Provider→Supplier rename therefore needs
 * a wrapper bean for every {@code Supplier<X>} injection point.
 */
public class SupplierAutoWrapperBeanFactoryPostProcessor implements BeanFactoryPostProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SupplierAutoWrapperBeanFactoryPostProcessor.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
    {
        Map<String, ResolvableType> wantedByName = new LinkedHashMap<>();
        for (String beanName : beanFactory.getBeanDefinitionNames())
        {
            Class<?> beanClass = beanFactory.getType(beanName, false);
            if (beanClass == null) continue;
            // PRD 002's Supplier wrapper is rapla-internal. Skip Spring framework,
            // 3rd-party, and plugin beans — their fields may reference types not
            // on rapla's runtime classpath (e.g. jakarta.inject.Provider in a
            // Spring autoconfig bean), which crashes getDeclaredFields() →
            // ResolvableType.forField() with NoClassDefFoundError. Plugins
            // (PRD 045 §4) don't use Supplier<T> injection, so this filter is
            // safe.
            String pkg = beanClass.getName();
            if (!pkg.startsWith("org.rapla.")) continue;
            try
            {
                collectSupplierTypeArgs(beanClass, wantedByName);
            }
            catch (NoClassDefFoundError ex)
            {
                // Defensive: a rapla class references a type the runtime class-
                // path doesn't have (typically an optional jakarta.inject.Provider
                // field on a legacy DI seam). Surface it so it's actually
                // fixable, but don't crash the boot — the wrapper this
                // PostProcessor produces is best-effort.
                LOGGER.warn("[SupplierAutoWrapper] skipping bean '{}' class {} — getDeclaredFields() failed: {}",
                        beanName, beanClass.getName(), ex.toString());
            }
        }
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) return;
        for (Map.Entry<String, ResolvableType> e : wantedByName.entrySet())
        {
            String supplierBeanName = e.getKey() + "$Supplier";
            if (registry.containsBeanDefinition(supplierBeanName)) continue;
            ResolvableType targetType = e.getValue();
            RootBeanDefinition def = new RootBeanDefinition();
            def.setTargetType(ResolvableType.forClassWithGenerics(Supplier.class, targetType));
            def.setInstanceSupplier(() -> (Supplier<Object>) () -> resolveValue(beanFactory, targetType));
            def.setLazyInit(true);
            registry.registerBeanDefinition(supplierBeanName, def);
        }
    }

    private static void collectSupplierTypeArgs(Class<?> cls, Map<String, ResolvableType> out)
    {
        for (Field f : cls.getDeclaredFields())
        {
            extractSupplierArg(ResolvableType.forField(f), out);
        }
        for (Constructor<?> ctor : cls.getDeclaredConstructors())
        {
            for (int i = 0; i < ctor.getParameterCount(); i++)
            {
                extractSupplierArg(ResolvableType.forConstructorParameter(ctor, i), out);
            }
        }
    }

    private static void extractSupplierArg(ResolvableType rt, Map<String, ResolvableType> out)
    {
        if (!Supplier.class.equals(rt.resolve())) return;
        ResolvableType arg = rt.getGeneric(0);
        if (arg.resolve() == null) return;
        out.putIfAbsent(arg.toString(), arg);
    }

    private static Object resolveValue(ConfigurableListableBeanFactory bf, ResolvableType targetType)
    {
        Class<?> raw = targetType.resolve();
        if (raw != null && Set.class.isAssignableFrom(raw))
        {
            Class<?> elementType = targetType.getGeneric(0).resolve();
            if (elementType != null) return new java.util.LinkedHashSet<>(bf.getBeansOfType(elementType).values());
        }
        if (raw != null && (java.util.List.class.isAssignableFrom(raw) || java.util.Collection.class.isAssignableFrom(raw)))
        {
            Class<?> elementType = targetType.getGeneric(0).resolve();
            if (elementType != null) return new java.util.ArrayList<>(bf.getBeansOfType(elementType).values());
        }
        if (raw != null && Map.class.isAssignableFrom(raw))
        {
            Class<?> valueType = targetType.getGeneric(1).resolve();
            if (valueType != null) return bf.getBeansOfType(valueType);
        }
        return bf.getBeanProvider(targetType).getObject();
    }
}
