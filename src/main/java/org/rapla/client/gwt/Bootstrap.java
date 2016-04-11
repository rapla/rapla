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
import org.rapla.framework.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;

import com.google.gwt.user.client.Window;

public class Bootstrap
{

    private final Provider<Application> application;
    private final RaplaFacade facade;
    private final StorageOperator operator;
    private final Logger logger;

    @Inject
    public Bootstrap(RaplaFacade facade, StorageOperator operator, Provider<Application> application, Logger logger)
    {
        this.application = application;
        this.operator = operator;
        this.facade = facade;
        this.logger = logger;
    }

    public void load()
    {
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
                application.get().start();
            }
            catch (Exception e)
            {
                logger.error(e.getMessage(), e);
                if (e instanceof RaplaSecurityException)
                {
                    Window.Location.replace("../rest/auth");
                }

            }
        }).exceptionally((e) ->
        {
            logger.error(e.getMessage(), e);
            if (e instanceof RaplaSecurityException)
            {
                Window.Location.replace("../rest/auth");
            }
            return null;
        });

    }
}
