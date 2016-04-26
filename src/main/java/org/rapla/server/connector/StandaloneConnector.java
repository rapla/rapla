package org.rapla.server.connector;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.LocalConnector.LocalEndPoint;
import org.rapla.rest.client.swing.AbstractLocalJsonConnector;

public class StandaloneConnector extends AbstractLocalJsonConnector
{
    private final LocalConnector connector;
    private final Semaphore waitForSemarphore = new Semaphore(0);

    public StandaloneConnector(LocalConnector connector)
    {
        this.connector = connector;
    }
    
    public void requestFinished()
    {
        waitForSemarphore.release();
    }

    @Override
    protected String doSend(String rawHttpRequest)
    {
        final LocalEndPoint endpoint = connector.executeRequest(rawHttpRequest);
        try
        {
            // Wait max 60 seconds
            waitForSemarphore.tryAcquire(60, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            throw new IllegalStateException("Server did not answer within 60 sec: " + e.getMessage(), e);
        }
        return endpoint.takeOutputString();
    }
    
}
