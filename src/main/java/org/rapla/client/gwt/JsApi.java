package org.rapla.client.gwt;

import jsinterop.annotations.JsType;
import org.rapla.client.ReservationController;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.storage.dbrm.RemoteAuthentificationService;

import javax.inject.Inject;

@JsType
public class JsApi
{
    private final RaplaFacade facade;
    private final Logger logger;
    private final RemoteAuthentificationService remoteAuthentificationService;

    @Inject
    public JsApi(RaplaFacade facade, Logger logger,  RemoteAuthentificationService remoteAuthentificationService)
    {
        this.facade = facade;
        this.logger = logger;
        this.remoteAuthentificationService = remoteAuthentificationService;
    }

    public RaplaFacade getFacade()
    {
        return facade;
    }

    public RemoteAuthentificationService getRemoteAuthentification()
    {
        return remoteAuthentificationService;
    }


}
