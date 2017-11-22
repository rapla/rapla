package org.rapla.client.gwt;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import jsinterop.annotations.JsType;
import org.rapla.client.Application;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteAuthentificationService;
import org.rapla.storage.dbrm.RemoteConnectionInfo;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;
import java.util.Collection;

@JsType
public class Bootstrap
{
    private final Provider<RaplaFacade> facade;
    private final Provider<StorageOperator> operator;
    private final Logger logger;
    private final RemoteConnectionInfo remoteConnectionInfo;
    Provider<JsApi> jsApi;
    private final Provider<RemoteAuthentificationService> remoteAuthentificationService;

    @Inject
    public Bootstrap(Provider<RaplaFacade> facade, Provider<StorageOperator> operator, Logger logger, RemoteConnectionInfo remoteConnectionInfo, Provider<JsApi> jsApi,
            Provider<RemoteAuthentificationService> remoteAuthentificationService)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
        this.operator = operator;
        this.jsApi = jsApi;
        this.facade = facade;
        this.logger = logger;
        this.remoteAuthentificationService = remoteAuthentificationService;
        final String moduleBaseURL = GWT.getModuleBaseURL();
        this.remoteConnectionInfo.setServerURL(moduleBaseURL + "../rapla");
    }

    public JsApi getAPI() { return  jsApi.get();}

    public RaplaFacade getFacade()
    {
        return facade.get();
    }

    public RemoteAuthentificationService getAuthentification()
    {
        return remoteAuthentificationService.get();
    }

    public Promise<Void> load(String accessToken){
        logger.info("Starting GWT Client with accessToken" + accessToken);
        remoteConnectionInfo.setAccessToken(accessToken);
        final FacadeImpl facadeImpl = (FacadeImpl) facade.get();
        final StorageOperator operator = this.operator.get();
        facadeImpl.setOperator(operator);
        Promise<Void> load = facadeImpl.load();
        return load;
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
}
