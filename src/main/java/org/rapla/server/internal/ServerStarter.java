package org.rapla.server.internal;

import org.rapla.framework.RaplaException;
import org.rapla.inject.Injector;
import org.rapla.logger.Logger;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.dagger.DaggerServerCreator;
import org.rapla.server.extensionpoints.ServletRequestPreprocessor;
import org.rapla.server.internal.console.ImportExportManagerContainer;
import org.rapla.storage.impl.DefaultRaplaLock;
import org.rapla.storage.impl.RaplaLock;

import javax.servlet.ServletException;
import java.util.Collection;

public class ServerStarter
{

    private ServerServiceContainer server;
    private Injector membersInjector;
    private Logger logger;
    private RaplaLock restartLock;
    private Collection<ServletRequestPreprocessor> processors;
    private ServerContainerContext backendContext;

    public ServerStarter(Logger logger, ServerContainerContext backendContext)
    {
        this.logger = logger;
        this.restartLock = new DefaultRaplaLock(logger);
        this.backendContext =  backendContext;
    }

    /** Locks the server, so no restart can't be triggererd unless lock is released */
    public RaplaLock.ReadLock lockRestart() throws RaplaException
    {
        return restartLock.readLock( getClass(),"lockRestart", 25);
    }



    public void freeRestartLock(RaplaLock.ReadLock lock)
    {
        this.restartLock.unlock( lock);
    }


    //Logger logger;
    public void startServer()    throws ServletException {

        final Runnable shutdownCommand = backendContext.getShutdownCommand();
        try
        {
            if ( shutdownCommand != null)
            {
                backendContext.setShutdownService(new ShutdownServiceImpl());
            }
            final DaggerServerCreator.ServerContext serverContext = DaggerServerCreator.create(logger, backendContext);
            server = serverContext.getServiceContainer();
            membersInjector = serverContext.getMembersInjector();
            logger.info("Rapla server started");
            processors = server.getServletRequestPreprocessors();
            //return server;
        }
        catch( Exception e )
        {
            logger.error(e.getMessage(), e);
            String message = "Error during initialization see logs for details: " + e.getMessage();
            if ( server != null)
            {
                server.dispose();
            }
            if ( shutdownCommand != null)
            {
                shutdownCommand.run();
            }
            
            throw new ServletException( message,e);
        }
    }

    public Injector getMembersInjector()
    {
        return membersInjector;
    }

    public ServerServiceContainer getServer()
    {
        return server;
    }

    public void stopServer() {
        if ( server != null)
        {
            server.dispose();
        }
        final Runnable shutdownCommand = backendContext.getShutdownCommand();
        if ( shutdownCommand != null)
        {
            shutdownCommand.run();
        }
    }

    public ImportExportManagerContainer createManager() throws Exception
    {
        return DaggerServerCreator.createImportExport(logger, backendContext);
    }


    private final class ShutdownServiceImpl implements ShutdownService {
        public void shutdown(final boolean restart) {
            RaplaLock.WriteLock writeLock;
            try
            {
                writeLock = restartLock.writeLock(getClass(),"shutdown restart=" + restart);
            }
            catch (RaplaException ex)
            { 
                logger.error("Can't restart server:" + ex.getMessage());
                return;
            }
            try
            {
                //acquired = requestCount.tryAcquire(maxRequests -1,10, TimeUnit.SECONDS);
                logger.info( "Stopping  Server");
                server.dispose();
                if ( restart)
                {
                    try {
                        logger.info( "Restarting Server");
                        startServer();
                    } catch (Exception e) {
                        logger.error( "Error while restarting Server", e );
                    }
                }
                else
                {
                    stopServer();
                }
            }
            catch (Exception ex)
            {
                stopServer();
            }
            finally
            {
                restartLock.unlock(writeLock);
            }
        }

    }

    public Collection<ServletRequestPreprocessor> getServletRequestPreprocessors() 
    {
        return processors;
    }

}