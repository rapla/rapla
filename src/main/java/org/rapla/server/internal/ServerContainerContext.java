package org.rapla.server.internal;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServerContainerContext
{
    private Map<String,DataSource> dbDatasources = new LinkedHashMap<String,DataSource>();
    private Map<String,String> fileDatasources = new LinkedHashMap<String,String>();
    private Map<String,String> services = new LinkedHashMap<>();
    private Object mailSession;
    Runnable shutdownCommand;
    String patchScript;

    private ShutdownService shutdownService = new ShutdownService()
    {
        @Override public void shutdown(boolean restart)
        {
            if ( restart )
                throw new IllegalStateException("Restart not implemented");
        }
    };

    public Runnable getShutdownCommand()
    {
        return shutdownCommand;
    }

    public void setShutdownCommand(Runnable runnable)
    {
        shutdownCommand = runnable;
    }

    public String getMainFilesource()
    {
        return getFileDatasource("raplafile");
    }

    public String getFileDatasource(String datasourename)
    {
        final String s = fileDatasources.get(datasourename);
        return s;
    }

    public Object getMailSession()
    {
        return mailSession;
    }

    public DataSource getMainDbDatasource()
    {
        return getDbDatasource("jdbc/rapladb");
    }

    public DataSource getDbDatasource(String key)
    {
        return dbDatasources.get( key);
    }

    public boolean isDbDatasource()
    {
        return getMainDbDatasource() != null;
    }

    public ShutdownService getShutdownService()
    {
        return shutdownService;
    }

    public void addDbDatasource(String key,DataSource dbDatasource)
    {
        dbDatasources.put(key, dbDatasource);
    }
    
    public void putServiceState(String key, boolean value)
    {
        services.put(key, Boolean.toString(value));
    }
    
    public boolean isServiceEnabled(String serviceKey)
    {
        if(services.containsKey(serviceKey))
        {
            final String s = services.get(serviceKey);
            return Boolean.valueOf( s );
        }
        return true;
    }


    public void addFileDatasource(String key,String fileDatasource)
    {
        fileDatasources.put(key, fileDatasource);
    }

    public void setMailSession(Object mailSession)
    {
        this.mailSession = mailSession;
    }

    public void setShutdownService(ShutdownService shutdownService)
    {
        this.shutdownService = shutdownService;
    }

    public String getPatchScript()
    {
        return patchScript;
    }

    public void setPatchScript(String patchScript)
    {
        this.patchScript = patchScript;
    }


}
