package org.rapla.spring;

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
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
    {
        Map<String, ResolvableType> wantedByName = new LinkedHashMap<>();
        for (String beanName : beanFactory.getBeanDefinitionNames())
        {
            Class<?> beanClass = beanFactory.getType(beanName, false);
            if (beanClass == null) continue;
            collectSupplierTypeArgs(beanClass, wantedByName);
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
