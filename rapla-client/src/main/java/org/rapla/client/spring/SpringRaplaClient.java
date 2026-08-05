package org.rapla.client.spring;

import org.rapla.ConnectInfo;
import org.rapla.client.api.ClientService;
import org.rapla.facade.client.ClientFacade;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.awt.Frame;

/**
 * Spring-based bootstrap for the Swing client.
 *
 * <p>Boots a Swing-side Spring {@link AnnotationConfigApplicationContext} with
 * {@link ClientConfig} and {@link ClientProxyConfig}, wires the full Swing UI
 * graph via component scan (see {@link SwingClientConfig}), and exposes the
 * client facade.
 *
 * <p>PRD 052: {@link #main(String[])} owns the context lifecycle in a loop.
 * Logout, switch-to-user, switch-back, and exit all flow through
 * {@link LogoutSignal} — each one closes the current context and either
 * builds a fresh one (clean slate per session, no carry-over of cached
 * entities or singleton UI state) or breaks the loop.
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
        this.context = buildContext(configClasses);
        this.facade = context.getBean(ClientFacade.class);
    }

    private static AnnotationConfigApplicationContext buildContext(Class<?>... configClasses)
    {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.addBeanFactoryPostProcessor(globalLazyInitPostProcessor());
        ctx.addBeanFactoryPostProcessor(new org.rapla.spring.SupplierAutoWrapperBeanFactoryPostProcessor());
        ctx.register(configClasses);
        ctx.refresh();
        return ctx;
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
     * Entry point for the Swing client. Owns the Spring-context lifecycle
     * loop: each iteration constructs a fresh
     * {@link AnnotationConfigApplicationContext}, starts the
     * {@link ClientService} with the current session's {@link ConnectInfo},
     * and blocks on {@link LogoutSignal#take()} until logout / switch-user /
     * exit. On logout: dispose all Swing frames, close the context, build a
     * new one. On exit: close and break the loop.
     *
     * <p>Usage:
     * <pre>
     *   java -cp ... org.rapla.client.spring.SpringRaplaClient
     *   java -cp ... org.rapla.client.spring.SpringRaplaClient username
     *   java -cp ... org.rapla.client.spring.SpringRaplaClient username password
     * </pre>
     *
     * <p>If a username (and optionally password) is supplied, the first
     * iteration attempts auto-login; otherwise the login dialog appears.
     */
    public static void main(String[] args) throws Exception
    {
        // Route j.u.l calls from third-party libs (JNLP runtime, JDK HTTP
        // client, Swing/AWT internals) through SLF4J/Logback so they land
        // in logs/rapla-client.log and respect logback.xml category rules.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        ConnectInfo initial = parseConnectInfo(args);
        NextSession next = initial != null ? NextSession.reconnectAs(initial) : NextSession.showLoginDialog();

        // Admin's session info saved during switch-to-user. The launcher
        // owns this because the Spring context (and connectionInfo with it)
        // dies on close, so context-internal storage would be lost.
        ConnectInfo savedAdminInfo = null;

        while (!next.isExit())
        {
            // An iteration is an impersonation session when we have a saved
            // admin ConnectInfo AND we're NOT currently switching back (the
            // switch-back iteration restores admin). The flag controls
            // RaplaClientServiceImpl.canSwitchBack() in the new context so
            // the "Switch back" admin-menu entry only appears under
            // impersonation.
            boolean isImpersonationSession = savedAdminInfo != null && !next.isSwitchBack();

            ConnectInfo currentInfo;
            if (next.isSwitchBack())
            {
                currentInfo = savedAdminInfo;
                savedAdminInfo = null;
            }
            else
            {
                currentInfo = next.info();
            }

            try (AnnotationConfigApplicationContext ctx = buildContext(
                    ClientConfig.class, ClientProxyConfig.class, SwingClientConfig.class,
                    EditTaskPresenterConfig.class, PluginResourcesConfig.class))
            {
                ctx.getBean(LogoutSignal.class).setImpersonationSession(isImpersonationSession);
                // After an explicit logout the fresh context must force
                // prompt=login on its first OAuth flow — the intent can only
                // travel via NextSession because the previous context (and any
                // flag on its beans) is gone.
                ctx.getBean(LogoutSignal.class).setForceOauthLoginNext(next.isForceOauthLogin());
                ClientService clientService = ctx.getBean(ClientService.class);
                clientService.start(currentInfo);

                // PRD 029 Phase 5 — dual-slot impersonation. The context started
                // above with admin's tokens as primary; now apply the
                // impersonation token as the override slot so outbound calls use
                // it while renewal/refresh continue to use admin's tokens.
                if (isImpersonationSession && next.impersonationAccessToken() != null)
                {
                    clientService.setImpersonation(
                            next.impersonationAccessToken(),
                            next.impersonationTargetUsername());
                }

                NextSession received = ctx.getBean(LogoutSignal.class).take();
                disposeAllFrames();

                // Capture admin restore info from the signal — the impersonation
                // call (RaplaClientServiceImpl.switchTo) reads connectionInfo
                // and packs the admin's full session into restoreInfo() BEFORE
                // the context closes. Can't read it from `currentInfo` because
                // that is null when admin logged in via the interactive dialog.
                if (received.restoreInfo() != null)
                {
                    savedAdminInfo = received.restoreInfo();
                }

                next = received;
            }   // ctx.close() — fires DisposableBean / @PreDestroy across every bean
        }
    }

    /**
     * Parses CLI args into a bootstrap {@link ConnectInfo}. PRD 029 Phase 5
     * (2026-05-25): the CLI takes a long-lived API JWT as a single arg, not
     * username+password. Mint via {@code POST /api/auth/api-keys} (PRD 043)
     * through the Swagger UI at {@code /swagger-ui} once, then paste the JWT into
     * {@code -Dexec.args="$RAPLA_DEV_TOKEN"}.
     *
     * <p>Returns {@code null} for no-args → next iteration shows the login
     * dialog. For one arg, treats it as the access token (no refresh — API
     * keys have their own lifetime via {@code exp}).
     */
    private static ConnectInfo parseConnectInfo(String[] args)
    {
        if (args.length == 0) return null;
        return new ConnectInfo(args[0], null);
    }

    private static void disposeAllFrames()
    {
        for (Frame f : Frame.getFrames())
        {
            try { f.dispose(); } catch (Throwable ignored) {}
        }
    }
}
