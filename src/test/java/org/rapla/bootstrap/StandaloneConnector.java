package org.rapla.bootstrap;

import java.lang.reflect.Method;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class StandaloneConnector
{
    private final Object connector;
    private final Semaphore waitForSemarphore = new Semaphore(0);
    private final Semaphore waitForStart = new Semaphore(0);
    Method isStopped;
    Method isRunning;
    Method executeRequest;
    Method takeOutput;
    Method toArray;


    public StandaloneConnector(Object connector,Method toArray) throws Exception
    {
        this.connector = connector;
        isStopped = connector.getClass().getMethod("isStopped");
        isRunning = connector.getClass().getMethod("isRunning");
        executeRequest = connector.getClass().getMethod("executeRequest",String.class);
        takeOutput = executeRequest.getReturnType().getMethod("takeOutput");
        this.toArray = toArray;
        waitForSemarphore.release();
    }

    // Called with reflection. Dont delete
    public void requestFinished()
    {
        waitForSemarphore.release();
    }

    // Called with reflection. Dont delete
    public byte[] doSend(String rawHttpRequest) throws Exception
    {
        final boolean running = (Boolean)isRunning.invoke(connector);
        final boolean stopped = (Boolean)isStopped.invoke(connector);
        if (!running && !stopped)
        {
            try
            {
                waitForStart.tryAcquire(10, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                throw new IllegalStateException("Server did not answer within 60 sec: " + e.getMessage(), e);
            }
        }
        Object endpoint = executeRequest.invoke(connector,rawHttpRequest);
        try
        {
            // Wait max 60 seconds
            waitForSemarphore.tryAcquire(10, TimeUnit.SECONDS);
            //endpoint.waitUntilClosed();
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        {
            throw new IllegalStateException("Server did not answer within 60 sec: " + e.getMessage(), e);
        }

        final Object byteBuffer = takeOutput.invoke(endpoint);
        return (byte[])toArray.invoke(null,byteBuffer);
    }

}
