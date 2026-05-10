package org.rapla.rest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.web.service.annotation.HttpExchange;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural guardrail for REST proxy interfaces: any method on a Spring
 * {@link HttpExchange @HttpExchange} interface that returns
 * {@code org.rapla.scheduler.Promise<X>} (or any other type Spring's
 * {@code HttpServiceProxyFactory} doesn't recognize as an async wrapper)
 * causes Jackson to deserialize the response body INTO a {@code Promise}
 * instance directly — Promise is an interface, fails with
 * {@code InvalidDefinitionException}.
 *
 * <p>Spring DOES adapt {@code Mono<X>}, {@code Flux<X>},
 * {@code CompletableFuture<X>} — those are fine. {@code Promise<X>} is not.
 *
 * <p>Walks every {@code .class} file under {@code rapla-core/target/classes}
 * (so siblings see proxies declared anywhere in the module) and fails if any
 * {@code @HttpExchange} interface has a method returning {@code Promise<X>}.
 * Pinning this means the next contributor who adds a Promise-typed REST proxy
 * sees a clear failure with a pointer instead of a runtime
 * {@code InvalidDefinitionException} from inside a Swing dialog.
 */
@RunWith(JUnit4.class)
public class RestProxyReturnTypeContractTest
{
    @Test
    public void noHttpExchangeMethodReturnsPromise() throws Exception
    {
        Path classesRoot = Path.of("target", "classes");
        if (!Files.isDirectory(classesRoot))
        {
            // Sibling-module run from repo root: hop into rapla-core/.
            classesRoot = Path.of("rapla-core", "target", "classes");
        }
        Assert.assertTrue("classpath root not found: " + classesRoot.toAbsolutePath(),
                Files.isDirectory(classesRoot));

        List<String> offenders = new ArrayList<>();
        scanForHttpExchangeProxies(classesRoot.toFile(), classesRoot, offenders);

        if (!offenders.isEmpty())
        {
            StringBuilder msg = new StringBuilder();
            msg.append("Found @HttpExchange interface method(s) returning Promise<X>. ")
               .append("Spring's HttpServiceProxyFactory has no Promise adapter — these will throw\n")
               .append("InvalidDefinitionException at runtime. Change the return type to the inner X\n")
               .append("(sync) and have callers wrap in commandScheduler.supply(...). See PRD 011 D5.\n\n");
            for (String o : offenders) msg.append("  - ").append(o).append('\n');
            Assert.fail(msg.toString());
        }
    }

    private static void scanForHttpExchangeProxies(File dir, Path root, List<String> offenders)
    {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children)
        {
            if (f.isDirectory())
            {
                scanForHttpExchangeProxies(f, root, offenders);
                continue;
            }
            if (!f.getName().endsWith(".class")) continue;
            String relative = root.relativize(f.toPath()).toString();
            String className = relative.substring(0, relative.length() - ".class".length())
                    .replace(File.separatorChar, '.');
            try
            {
                Class<?> c = Class.forName(className, false, RestProxyReturnTypeContractTest.class.getClassLoader());
                if (!c.isInterface()) continue;
                if (c.getAnnotation(HttpExchange.class) == null) continue;
                for (Method m : c.getDeclaredMethods())
                {
                    Type rt = m.getGenericReturnType();
                    if (!(rt instanceof ParameterizedType pt)) continue;
                    Type raw = pt.getRawType();
                    if (!(raw instanceof Class<?> rawClass)) continue;
                    if ("org.rapla.scheduler.Promise".equals(rawClass.getName()))
                    {
                        offenders.add(c.getName() + "#" + m.getName() + " returns " + rt.getTypeName());
                    }
                }
            }
            catch (Throwable ignore)
            {
                // Skip classes we can't load (test-only deps, missing SPI, etc.).
            }
        }
    }
}
