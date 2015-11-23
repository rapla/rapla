package org.rapla.server.internal;

import javax.sql.DataSource;

/**
 * Created by Christopher on 23.11.2015.
 */
public class ServerContainerContext
{
    private DataSource dbDatasource;
    private String fileDatasource;
    private Object mailSession;
    private boolean isDbDatasource;
    private ShutdownService shutdownService = new ShutdownService()
    {
        @Override public void shutdown(boolean restart)
        {
            if ( restart )
                throw new IllegalStateException("Restart not implemented");
        }
    };

    public String getFileDatasource()
    {
        return fileDatasource;
    }

    public Object getMailSession()
    {
        return mailSession;
    }

    public DataSource getDbDatasource()
    {
        return dbDatasource;
    }

    public boolean isDbDatasource()
    {
        return isDbDatasource;
    }

    public ShutdownService getShutdownService()
    {
        return shutdownService;
    }

    public void setDbDatasource(DataSource dbDatasource)
    {
        this.dbDatasource = dbDatasource;
    }

    public void setFileDatasource(String fileDatasource)
    {
        this.fileDatasource = fileDatasource;
    }

    public void setMailSession(Object mailSession)
    {
        this.mailSession = mailSession;
    }

    public void setIsDbDatasource(boolean isDbDatasource)
    {
        this.isDbDatasource = isDbDatasource;
    }

    public void setShutdownService(ShutdownService shutdownService)
    {
        this.shutdownService = shutdownService;
    }
}
