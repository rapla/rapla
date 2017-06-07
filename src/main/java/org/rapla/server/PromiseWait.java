package org.rapla.server;

import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;

public interface PromiseWait
{
    <T> T  waitForWithRaplaException(Promise<T> promise, int timeoutInMillis) throws RaplaException;
}
