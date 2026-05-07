package org.rapla.rest.client;

import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;


public interface CustomConnector extends ExceptionDeserializer
{
    String getFullQualifiedUrl(String relativePath);
    String reauth(Class proxy) throws Exception;
    String getAccessToken();
    Logger getLogger();
    <T> CompletablePromise<T> createCompletable();
    <T> Promise<T> call(CommandScheduler.Callable<T> callable);
}
