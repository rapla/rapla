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

    private final Provider<Application> application;
    private final RaplaFacade facade;
    private final StorageOperator operator;
    private final Logger logger;
    private final RemoteConnectionInfo remoteConnectionInfo;

    @Inject
    public Bootstrap(RaplaFacade facade, StorageOperator operator,  Logger logger, RemoteConnectionInfo remoteConnectionInfo)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
        this.application = null;//FIXME add application;
        this.operator = operator;
        this.facade = facade;
        this.logger = logger;
        this.remoteConnectionInfo.setServerURL(GWT.getModuleBaseURL() + "../rapla/");
    }

    public void load(String accessToken)
    {
        remoteConnectionInfo.setAccessToken(accessToken);
        final FacadeImpl facadeImpl = (FacadeImpl) facade;
        ((FacadeImpl) facade).setOperator(operator);
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
                application.get().start(defaultLanguageChosen, () -> {
                    logger.info("Restarting.");
                    Window.Location.reload();
                }
                );
            }
            catch (Exception e)
            {
                logger.error(e.getMessage(), e);
                if (e instanceof RaplaSecurityException)
                {
                    Window.Location.replace("../rapla/auth");
                }

            }
        }).exceptionally((e) ->
        {
            logger.error(e.getMessage(), e);
            if (e instanceof RaplaSecurityException)
            {
                Window.Location.replace("../rapla/auth");
            }
            return null;
        });

    }
}
