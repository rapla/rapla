package org.rapla.server.internal;

import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.ResultImpl;
import org.rapla.jsonrpc.common.VoidResult;
import org.rapla.rest.RemoteLogger;

import javax.inject.Inject;

@DefaultImplementation(of=RemoteLogger.class,context = InjectionContext.server)
public class RemoteLoggerImpl implements RemoteLogger {
    Logger logger;
    
    @Inject
    public RemoteLoggerImpl(Logger logger) 
    {
        this.logger = logger;
    }
    
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

}
