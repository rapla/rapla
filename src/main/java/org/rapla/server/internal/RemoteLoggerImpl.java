package org.rapla.server.internal;

import javax.inject.Inject;

import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.rest.RemoteLogger;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.ResultImpl;
import org.rapla.gwtjsonrpc.common.VoidResult;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;

public class RemoteLoggerImpl implements RemoteMethodFactory<RemoteLogger> {
    Logger logger;
    
    @Inject
    public RemoteLoggerImpl(Logger logger) 
    {
        this.logger = logger;
    }
    
    public RemoteLogger createService(RemoteSession remoteSession) throws RaplaContextException {
        return new RemoteLogger() {
            
            @Override
            public FutureResult<VoidResult> info(String id, String message) {
                if ( id == null)
                {
                    String message2 = "Id missing in logging call";
                    logger.error(message2);
                    return new ResultImpl<VoidResult>( new RaplaException(message));
                }
                Logger childLogger = logger.getChildLogger(id);
                childLogger.info(message);
                return ResultImpl.VOID;
            }
        };
    }

}
