package org.rapla.client.gwt;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Window;
import org.rapla.components.i18n.client.gwt.GwtBundleManager;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.ClientFacadeImpl;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RemoteLocaleService;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteAuthentificationService;
import org.rapla.storage.dbrm.RemoteConnectionInfo;

import javax.inject.Inject;
import javax.inject.Provider;

@DefaultImplementation(of=GwtStarter.class,context = InjectionContext.gwt, export = true)
public class RaplaGwtStarter implements GwtStarter
{
    private final Provider<ClientFacade> facade;
    private final Provider<StorageOperator> operator;
    private final Logger logger;
    private final RemoteConnectionInfo remoteConnectionInfo;
    Provider<JsApi> jsApi;
    private final Provider<RemoteAuthentificationService> remoteAuthentificationService;
    private final RemoteLocaleService remoteLocaleService;
    private GwtBundleManager gwtBundleManager;

    @Inject
    public RaplaGwtStarter(Provider<ClientFacade> facade, Provider<StorageOperator> operator, Logger logger, RemoteConnectionInfo remoteConnectionInfo, Provider<JsApi> jsApi,
            Provider<RemoteAuthentificationService> remoteAuthentificationService, RemoteLocaleService remoteLocaleService, GwtBundleManager gwtBundleManager)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
        this.operator = operator;
        this.jsApi = jsApi;
        this.facade = facade;
        this.logger = logger;
        this.remoteAuthentificationService = remoteAuthentificationService;
        this.remoteLocaleService = remoteLocaleService;
        this.gwtBundleManager = gwtBundleManager;
        final String moduleBaseURL = GWT.getModuleBaseURL();
        this.remoteConnectionInfo.setServerURL(moduleBaseURL + "../rapla");

    }

    public Logger getLogger() { return  logger;}

    public JsApi getAPI() { return  jsApi.get();}

    public RaplaFacade getFacade()
    {
        return facade.get().getRaplaFacade();
    }

    public RemoteAuthentificationService getAuthentification()
    {
        return remoteAuthentificationService.get();
    }

    @Override
    public Promise<Void> initLocale(String localeParam)
    {
        String id = "org.rapla";//"123";
        logger.info("loading locale for " + localeParam);
        return remoteLocaleService.locale(id, localeParam).thenAccept(localePackage -> {
            logger.info("Locale loaded for " + localePackage.getCountry() + " Language: " + localePackage.getLanguage());
            gwtBundleManager.setLocalPackage(localePackage);
        });
    }

    @Override
    public Promise<JsApi> registerApi(String accessToken)
    {
        logger.info("Register Start");
        return load(accessToken).thenApply((dummy2) -> jsApi.get());
    }

    public Promise<Void> load(String accessToken){
        logger.info("Starting GWT Client with accessToken" + accessToken);
        remoteConnectionInfo.setAccessToken(accessToken);
        final ClientFacade facadeImpl =  facade.get();
        final StorageOperator operator = this.operator.get();
        ((ClientFacadeImpl)facadeImpl).setOperator(operator);
        Promise<Void> load = facadeImpl.load();
        return load;
    }

    public static final String LOGIN_COOKIE = "raplaLoginToken";
    public LoginTokens getValidToken()
    {
        logger.info(Cookies.getCookieNames().toString());
        String hashToken = Window.Location.getHash();
        String tokenString = null;
        if (hashToken != null && !hashToken.isEmpty() ) {
            final int indexOf = hashToken.indexOf(LOGIN_COOKIE);
            if ( indexOf >= 0)
            {
                final String encodedToken = hashToken.substring(indexOf + LOGIN_COOKIE.length() + 1);
                tokenString = encodedToken.replaceAll("&valid_until=","#");
                Cookies.setCookie(LOGIN_COOKIE, tokenString);
            }
        }
        if ( tokenString == null)
        {
            tokenString = Cookies.getCookie(LOGIN_COOKIE);
        }
        if (tokenString != null)
        {
            // re request the server for refresh token
            LoginTokens token = LoginTokens.fromString(tokenString);
            boolean valid = token.isValid();
            if (valid)
            {
                logger.info("found valid cookie: " + tokenString);
                return token;
            }
            logger.info( "No valid login token found");
        }
        else
        {
            logger.info( "No login token found");
        }
        return null;
    }


    //    public void start(Promise<Void> load)
    //    {
    //        RaplaPopups.getProgressBar().setPercent(40);
    //        load.thenRun(() ->
    //        {
    //            try
    //            {
    //                RaplaPopups.getProgressBar().setPercent(70);
    //                Collection<Allocatable> allocatables = Arrays.asList(getFacade().getAllocatables());
    //                logger.info("loaded " + allocatables.size() + " resources. Starting application");
    //                boolean defaultLanguageChosen = false;
    //                final Application application = getApplication();
    //                application.start(defaultLanguageChosen, () -> {
    //                    logger.info("Restarting.");
    //                    Window.Location.reload();
    //                }
    //                );
    //            }
    //            catch (Exception e)
    //            {
    //                logger.error(e.getMessage(), e);
    //                if (e instanceof RaplaSecurityException)
    //                {
    //                    RaplaGwtStarter.redirectToStart();
    //                }
    //
    //            }
    //        }).exceptionally((e) ->
    //        {
    //            logger.error(e.getMessage(), e);
    //            if (e instanceof RaplaSecurityException)
    //            {
    //                RaplaGwtStarter.redirectToStart();
    //            }
    //            return null;
    //        });
    //
    //    }




    public void startApplication()
    {
//        final LoginTokens token = getValidToken();
//        if (token != null)
//        {
//            RaplaPopups.getProgressBar().setPercent(20);
//            //bootstrapProvider.load(token.getAccessToken());
//            Scheduler.get().scheduleFixedDelay(new Scheduler.RepeatingCommand()
//            {
//                @Override
//                public boolean execute()
//                {
//                    final Promise<Void> load = bootstrapProvider.load(token.getAccessToken());
//                    bootstrapProvider.start( load);
//                    return false;
//                }
//            }, 100);
//        }
//        else
//        {
//            redirectToStart();
//        }
    }




}
