package org.rapla.client.gwt;

import com.google.gwt.user.client.Window;
import org.rapla.client.Application;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.jsonrpc.common.AsyncCallback;
import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.VoidResult;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;
import java.util.Collection;

public class Bootstrap
{

    private final Provider<Application> application;
    private final ClientFacade facade;
    private final StorageOperator operator;
    private final Logger logger;

    @Inject
    public Bootstrap(ClientFacade facade,StorageOperator operator,Provider<Application> application, Logger logger)
    {
        this.application = application;
        this.operator = operator;
        this.facade = facade;
        this.logger = logger;
    }

    public void load()
    {
        final FacadeImpl facadeImpl = (FacadeImpl) facade;
        ((FacadeImpl) facade).setOperator( operator);
        facadeImpl.setCachingEnabled(false);
        FutureResult<VoidResult> load = facadeImpl.load();
        logger.info("Loading resources");
        RaplaPopups.getProgressBar().setPercent(40);
        load.get(new AsyncCallback<VoidResult>()
        {

            @Override
            public void onSuccess(VoidResult result)
            {
                try
                {
                    RaplaPopups.getProgressBar().setPercent(70);
                    Collection<Allocatable> allocatables = Arrays.asList(facadeImpl.getAllocatables());
                    logger.info("loaded " + allocatables.size() + " resources. Starting application");
                    application.get().start();
                }
                catch (RaplaException e)
                {
                    onFailure(e);
                }
                catch (Exception e)
                {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable e)
            {
                logger.error(e.getMessage(), e);
                if (e instanceof RaplaSecurityException)
                {
                    Window.Location.replace("../rapla?page=auth");
                }
            }
        });

    }
}
