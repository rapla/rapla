package org.rapla.client.spring;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Spring auto-wires {@code ObjectProvider<T>} and {@code jakarta.inject.Provider<T>} but NOT
 * {@code java.util.function.Supplier<T>}. PRD 002's Provider→Supplier rename therefore needs
 * a wrapper bean for every {@code Supplier<X>} injection point.
 *
 * <p>This BFPP scans every registered bean class for fields and constructor parameters typed
 * {@code Supplier<X>}, and registers a singleton {@code Supplier<X>} bean per unique {@code X}
 * by closing over {@code beanFactory.getBean(X.class)}. The lookup is lazy — the lambda only
 * fires when {@code .get()} is called, mirroring the legacy {@code Provider<T>} semantics.
 *
 * <p>Self-contained alternative to per-type {@code @Bean Supplier<X>} factories that would
 * otherwise have to grow with every new {@code Supplier<X>} injection point.
 */
public class SupplierAutoWrapperBeanFactoryPostProcessor implements BeanFactoryPostProcessor
{
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
    {
        // Dedupe by ResolvableType.toString() — ResolvableType.equals() is unreliable across
        // Field/Constructor sources for the same generic type (e.g. Field-derived RT and
        // ctor-param-derived RT for the same Supplier<RaplaFacade> may not be equal).
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
        if (arg.resolve() == null) return;  // unresolved type variable
        out.putIfAbsent(arg.toString(), arg);
    }

    /** Spring auto-collects {@code Set<X>}/{@code List<X>}/{@code Map<String,X>} at injection time
     *  from individual {@code X} beans, but {@code BeanFactory.getBeanProvider(Set<X>)} does NOT
     *  trigger that auto-collection — it looks for a literal bean of type {@code Set<X>}. So for
     *  collection-typed Suppliers we have to do the collection ourselves. */
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

    private static String decapitalize(String s)
    {
        if (s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
