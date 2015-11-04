package org.rapla.server.internal.dagger;

import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.ServerServiceImpl;

public class DaggerServerCreator
{
    public static ServerServiceImpl create(Logger logger, ServerServiceImpl.ServerContainerContext containerContext)
    {
        ServerComponent component= DaggerServerComponent.builder().myModule(new MyModule(containerContext, logger)).build();
        final ServerServiceImpl server = component.getServer();
        server.getMethodProvider().setList( component.getWebservices().getList());
        return server;
    }
}
