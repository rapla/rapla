package org.rapla.server.internal;

import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.dagger.DaggerServerCreator;
import org.rapla.server.servletpages.ServletRequestPreprocessor;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServerStarter
{

    private ServerServiceContainer server;
    Logger logger;
    Runnable shutdownCommand;
    private ReadWriteLock restartLock = new ReentrantReadWriteLock();
    Collection<ServletRequestPreprocessor> processors;
    ServerContainerContext backendContext;

    private ServerServiceContainer create() throws Exception
    {
        return DaggerServerCreator.create(logger, backendContext);
    }

    public ServerStarter(Logger logger, RaplaJNDIContext jndi)
    {
        this.logger = logger;
        shutdownCommand = (Runnable) jndi.lookup("rapla_shutdown_command", false);

        ServerContainerContext backendContext = createBackendContext(logger, jndi);
        this.backendContext = backendContext;

    }



    public static ServerContainerContext createBackendContext(Logger logger, RaplaJNDIContext jndi) {
        String env_raplafile;
        DataSource env_rapladb = null;
        Object env_raplamail;
        env_raplafile = jndi.lookupEnvString("raplafile", true);
        Object lookupResource = jndi.lookupResource("jdbc/rapladb", true);
        if ( lookupResource != null)
        {
            if ( lookupResource instanceof DataSource)
            {
                env_rapladb =  (DataSource) lookupResource;
            }
            else
            {
                logger.error("Passed Object does not implement Datasource " + env_rapladb  );
            }
        }
        
      
        env_raplamail =  jndi.lookupResource( "mail/Session", false);
        if ( env_raplamail != null)
        {
            logger.info("Configured mail service via JNDI");
        }
        ServerContainerContext backendContext = new ServerContainerContext();
        backendContext.setFileDatasource( env_raplafile);
        backendContext.setDbDatasource( env_rapladb);
        backendContext.setMailSession( env_raplamail);

        String env_rapladatasource = jndi.lookupEnvString( "rapladatasource", true);
        if ( env_rapladatasource == null || env_rapladatasource.trim().length() == 0  || env_rapladatasource.startsWith( "${"))
        {
            if ( backendContext.getDbDatasource() != null)
            {
                env_rapladatasource = "rapladb";
            }
            else if ( backendContext.getFileDatasource() != null)
            {
                env_rapladatasource = "raplafile";
            }
            else
            {
                logger.warn("Neither file nor database setup configured.");
            }
            logger.info("Passed JNDI Environment rapladatasource=" + env_rapladatasource + " env_rapladb=" + backendContext.getDbDatasource() + " env_raplafile="+ backendContext.getFileDatasource());
        }
        backendContext.setIsDbDatasource(env_rapladatasource != null && env_rapladatasource.equalsIgnoreCase("rapladb"));
        return backendContext;
    }
    
    
    
    public ReadWriteLock getRestartLock()
    {
        return restartLock;
    }
    
    //Logger logger;
    public ServerServiceContainer startServer()    throws ServletException {
        

        try
        {
            if ( shutdownCommand != null)
            {
                backendContext.setShutdownService(new ShutdownServiceImpl());
            }
            server = create();
            logger.info("Rapla server started");

            processors = server.getServletRequestPreprocessors();
            return server;
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


    public ServerServiceContainer getServer()
    {
        return server;
    }

    public void stopServer() {
        if ( server != null)
        {
            server.dispose();
        }
        if ( shutdownCommand != null)
        {
            shutdownCommand.run();
        }
    }

    private final class ShutdownServiceImpl implements ShutdownService {
        public void shutdown(final boolean restart) {
            Lock writeLock;
            try
            {
                try
                {
                    RaplaComponent.unlock( restartLock.readLock());
                }
                catch (IllegalMonitorStateException ex)
                {
                    logger.error("Error unlocking read for restart " + ex.getMessage());
                }
                writeLock = RaplaComponent.lock( restartLock.writeLock(), 60);
            }
            catch (RaplaException ex)
            { 
                logger.error("Can't restart server " + ex.getMessage());
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
                RaplaComponent.unlock(writeLock);
            }
        }

    }

    public Collection<ServletRequestPreprocessor> getServletRequestPreprocessors() 
    {
        return processors;
    }

}