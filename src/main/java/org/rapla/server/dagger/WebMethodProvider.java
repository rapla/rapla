package org.rapla.server.dagger;

import org.rapla.jsonrpc.server.WebserviceCreator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton public class WebMethodProvider
{
    Map<String,WebserviceCreator> providers = new LinkedHashMap<>();

    @Inject public WebMethodProvider()
    {

    }

    public WebserviceCreator get(String name)
    {
        return providers.get( name );
    }

    public void setProviders(Map<String,WebserviceCreator> providers)
    {
        this.providers = providers;

    }
}
