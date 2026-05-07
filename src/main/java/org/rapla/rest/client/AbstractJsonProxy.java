package org.rapla.rest.client;

public abstract class AbstractJsonProxy
{
    /** URL of the service implementation. */
    private String path;
    final protected CustomConnector connector;

    public AbstractJsonProxy(CustomConnector connector)
    {
        this.connector = connector;
    }

    protected void setPath(String path)
    {
        this.path = path;
    }

    protected String getPath()
    {
        return path;
    }

    protected String getMethodUrl( String subPath)
    {
        String contextPath = getPath() + (subPath == null || subPath.isEmpty() ? "" : (subPath.startsWith("?") ? "" : "/") + subPath);
        final String entryPoint = connector.getFullQualifiedUrl(contextPath);
        return entryPoint;
    }


}
