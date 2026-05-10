package org.rapla.client.spring;

import org.rapla.ConnectInfo;
import org.rapla.client.api.ClientService;
import org.rapla.facade.client.ClientFacade;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Spring-based bootstrap for the Swing client.
 *
 * <p>Boots a Swing-side Spring {@link AnnotationConfigApplicationContext} with
 * {@link ClientConfig} and {@link ClientProxyConfig}, wires the full Swing UI
 * graph via component scan (see {@link SwingClientConfig}), and exposes the
 * client facade.
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
        this.context = new AnnotationConfigApplicationContext();
        context.addBeanFactoryPostProcessor(globalLazyInitPostProcessor());
        context.addBeanFactoryPostProcessor(new org.rapla.spring.SupplierAutoWrapperBeanFactoryPostProcessor());
        context.register(configClasses);
        context.refresh();
        this.facade = context.getBean(ClientFacade.class);
    }

    /**
     * Marks every bean definition as lazy-init before context refresh. The
     * legacy DI created {@code @Autowired}-annotated classes on first use, and
     * several Swing constructors (e.g. {@code ConflictReservationCheck},
     * {@code CountryChooser}) touch facade/operator state that isn't ready
     * during {@code preInstantiateSingletons()}. Global lazy-init defers each
     * bean's construction to its first dereference, by which point the full
     * graph (including the remote operator) is in place. Beans that genuinely
     * need eager init can opt back in with {@code @Lazy(false)}.
     */
    private static BeanFactoryPostProcessor globalLazyInitPostProcessor()
    {
        return (ConfigurableListableBeanFactory bf) -> {
            for (String name : bf.getBeanDefinitionNames())
            {
                BeanDefinition def = bf.getBeanDefinition(name);
                if (!def.isLazyInit())
                {
                    def.setLazyInit(true);
                }
            }
        };
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
