package org.rapla.client.spring;

import org.rapla.ConnectInfo;
import org.rapla.client.api.ClientService;
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
        this(ClientConfig.class, ClientProxyConfig.class, SwingClientConfig.class, EditTaskPresenterConfig.class, PluginResourcesConfig.class);
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

    /**
     * Entry point for the Swing client. Boots the Spring context and launches
     * the {@link ClientService} (which puts up the login dialog and, on
     * successful login, opens the main application window).
     *
     * <p>Usage:
     * <pre>
     *   java -cp ... org.rapla.client.spring.SpringRaplaClient
     *   java -cp ... org.rapla.client.spring.SpringRaplaClient username
     *   java -cp ... org.rapla.client.spring.SpringRaplaClient username password
     * </pre>
     *
     * <p>If a username (and optionally password) is supplied, the client
     * attempts auto-login; otherwise it shows the interactive login dialog.
     */
    public static void main(String[] args) throws Exception
    {
        SpringRaplaClient client = new SpringRaplaClient();
        ClientService clientService = client.getContext().getBean(ClientService.class);
        ConnectInfo connectInfo = null;
        if (args.length >= 1)
        {
            String user = args[0];
            char[] password = args.length >= 2 ? args[1].toCharArray() : new char[0];
            connectInfo = new ConnectInfo(user, password);
        }
        clientService.start(connectInfo);
    }
}
