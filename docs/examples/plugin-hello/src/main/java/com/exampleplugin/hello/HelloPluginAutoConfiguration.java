package com.exampleplugin.hello;

import org.rapla.server.spring.RaplaServerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

/**
 * Drop-in plugin autoconfig — picked up via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * when this jar lands in {@code /opt/rapla/plugins/}.
 *
 * <ul>
 *   <li>{@code after = RaplaServerAutoConfiguration.class} — stock rapla
 *       beans (RaplaFacade, PermissionController, ...) are available before
 *       this plugin loads, so you can {@code @Autowired} them.</li>
 *   <li>{@code @ConditionalOnProperty(matchIfMissing = true)} — operator can
 *       disable via {@code rapla.plugins.hello.enabled=false} in
 *       {@code config/application.yml} without removing the jar.</li>
 *   <li>{@code @ComponentScan("com.exampleplugin.hello")} — picks up
 *       {@link HelloController}. NEVER scan {@code org.rapla.*}.</li>
 * </ul>
 */
@AutoConfiguration(after = RaplaServerAutoConfiguration.class)
@ConditionalOnProperty(prefix = "rapla.plugins", name = "hello.enabled", matchIfMissing = true)
@ComponentScan("com.exampleplugin.hello")
public class HelloPluginAutoConfiguration { }
