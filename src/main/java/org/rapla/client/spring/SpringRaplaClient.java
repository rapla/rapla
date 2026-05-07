package org.rapla.client.spring;

import org.rapla.facade.client.ClientFacade;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Spring-based replacement for the legacy {@code RaplaClient} bootstrap (Phase 4).
 *
 * <p>This boots a Swing-side Spring {@link AnnotationConfigApplicationContext}
 * with {@link ClientConfig} and {@link ClientProxyConfig} and exposes the
 * client facade. It does not yet wire the full Swing UI graph (that's Phase 4
 * step 3) — its purpose is to demonstrate that the client-side Spring DI can
 * produce a working {@code ClientFacade} entirely without {@code restinject}.
 *
 * <p>The legacy {@code RaplaClient} class is still present for backwards
 * compatibility but is broken at runtime since the Phase 1.2 jakarta migration.
 * New client code should use this class instead.
 */
public class SpringRaplaClient implements AutoCloseable
{
    private final AnnotationConfigApplicationContext context;
    private final ClientFacade facade;

    public SpringRaplaClient()
    {
        this(ClientConfig.class, ClientProxyConfig.class, SwingClientConfig.class);
    }

    public SpringRaplaClient(Class<?>... configClasses)
    {
        this.context = new AnnotationConfigApplicationContext(configClasses);
        this.facade = context.getBean(ClientFacade.class);
    }

    public ClientFacade getFacade()
    {
        return facade;
    }

    public AnnotationConfigApplicationContext getContext()
    {
        return context;
    }

    @Override
    public void close()
    {
        context.close();
    }
}
