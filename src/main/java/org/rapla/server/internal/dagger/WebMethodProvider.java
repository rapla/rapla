package org.rapla.server.internal.dagger;

import org.rapla.dagger.DaggerWebserviceComponent;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton public class WebMethodProvider
{
    DaggerWebserviceComponent.ServiceList list;

    @Inject public WebMethodProvider()
    {

    }

    public Provider<Object> find(HttpServletRequest request, HttpServletResponse response, String name)
    {
        return list.find(request, response, name);
    }

    public <T> Provider<T> find(HttpServletRequest request, HttpServletResponse response, Class<T> className)
    {
        final Provider<T> tProvider = list.find(request, response, className);
        return tProvider;
    }

    void setList(DaggerWebserviceComponent.ServiceList list)
    {
        this.list = list;
    }
}
