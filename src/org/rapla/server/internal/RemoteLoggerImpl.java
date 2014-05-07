package org.rapla.server.internal;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.logger.Logger;
import org.rapla.rest.RemoteLogger;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;

public class RemoteLoggerImpl implements RemoteMethodFactory<RemoteLogger> {
    Logger logger;
    public RemoteLoggerImpl(RaplaContext context) throws RaplaContextException
    {
        this.logger = context.lookup(Logger.class);
    }
    
    public RemoteLogger createService(RemoteSession remoteSession) throws RaplaContextException {
        return new RemoteLogger() {
            
            @Override
            public void info(String id, String message) {
                if ( id == null)
                {
                    logger.error("Id missing in logging call");
                    return;
                }
                Logger childLogger = logger.getChildLogger(id);
                childLogger.info(message);
            }
        };
    }

}
