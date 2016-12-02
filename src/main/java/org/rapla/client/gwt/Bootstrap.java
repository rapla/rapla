package org.rapla.client.gwt;

import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Provider;

import org.rapla.client.Application;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteConnectionInfo;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;

public class Bootstrap
{

    //private final Provider<Application> application;
    private final Provider<RaplaFacade> facade;
    private final Provider<StorageOperator> operator;
    private final Logger logger;
    private final RemoteConnectionInfo remoteConnectionInfo;

    @Inject
    public Bootstrap(Provider<RaplaFacade> facade, Provider<StorageOperator> operator,  Logger logger, RemoteConnectionInfo remoteConnectionInfo /*,Provider<Application> application*/)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
      //  this.application = null;
        this.operator = operator;
        this.facade = facade;
        this.logger = logger;
        final String moduleBaseURL = GWT.getModuleBaseURL();
        this.remoteConnectionInfo.setServerURL(moduleBaseURL + "../rapla/");
    }

    public void load(String accessToken)
    {
        remoteConnectionInfo.setAccessToken(accessToken);
        final FacadeImpl facadeImpl = (FacadeImpl) facade.get();
        final StorageOperator operator = this.operator.get();
        facadeImpl.setOperator(operator);
        Promise<Void> load = facadeImpl.load();
        logger.info("Loading resources");
        RaplaPopups.getProgressBar().setPercent(40);
        load.thenRun(() ->
        {
            try
            {
                RaplaPopups.getProgressBar().setPercent(70);
                Collection<Allocatable> allocatables = Arrays.asList(facadeImpl.getAllocatables());
                logger.info("loaded " + allocatables.size() + " resources. Starting application");
                boolean defaultLanguageChosen = false;
//                final Application application = this.application.get();
//                application.start(defaultLanguageChosen, () -> {
//                    logger.info("Restarting.");
//                    Window.Location.reload();
//                }
//                );
            }
            catch (Exception e)
            {
                logger.error(e.getMessage(), e);
                if (e instanceof RaplaSecurityException)
                {
                    RaplaGwtStarter.redirectToStart();
                }

            }
        }).exceptionally((e) ->
        {
            logger.error(e.getMessage(), e);
            if (e instanceof RaplaSecurityException)
            {
                RaplaGwtStarter.redirectToStart();
            }
            return null;
        });

    }
}
