package org.rapla.server.spring;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "rapla")
public class RaplaServerProperties
{
    private Map<String, DataSourceProperties> dbDatasources = new LinkedHashMap<>();
    private Map<String, String> fileDatasources = new LinkedHashMap<>();
    private Map<String, Boolean> services = new LinkedHashMap<>();
    private String mailSession;
    private String patchScript;

    public Map<String, DataSourceProperties> getDbDatasources()
    {
        return dbDatasources;
    }

    public void setDbDatasources(Map<String, DataSourceProperties> dbDatasources)
    {
        this.dbDatasources = dbDatasources;
    }

    public Map<String, String> getFileDatasources()
    {
        return fileDatasources;
    }

    public void setFileDatasources(Map<String, String> fileDatasources)
    {
        this.fileDatasources = fileDatasources;
    }

    public Map<String, Boolean> getServices()
    {
        return services;
    }

    public void setServices(Map<String, Boolean> services)
    {
        this.services = services;
    }

    public String getMailSession()
    {
        return mailSession;
    }

    public void setMailSession(String mailSession)
    {
        this.mailSession = mailSession;
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
