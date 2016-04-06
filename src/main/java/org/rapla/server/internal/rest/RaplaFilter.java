package org.rapla.server.internal.rest;

import dagger.MembersInjector;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.RaplaBootstrapLogger;
import org.rapla.server.MainServlet;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.ServerStarter;
import org.rapla.server.internal.rest.validator.RaplaRestDaggerContextProvider;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RaplaFilter implements Filter
{
    private final Map<String, MembersInjector> membersInjector = new HashMap<>();

    @Inject
    public RaplaFilter()
    {
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        Logger logger = RaplaBootstrapLogger.createRaplaLogger();
        try
        {
            Object server = MainServlet.init(logger, filterConfig.getServletContext());
            if(server != null)
            {
                Map<String, MembersInjector> starter = ((ServerServiceImpl)((ServerStarter)server).getServer()).getMembersInjector();
                this.membersInjector.putAll(starter);
            }
        }
        catch (Throwable ex)
        {
            throw new ServletException(ex.getMessage(), ex);
        }
    }
    
    protected Map<String, MembersInjector> getMembersInjector()
    {
        return membersInjector;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        request.setAttribute(RaplaRestDaggerContextProvider.RAPLA_CONTEXT, getMembersInjector());
        chain.doFilter(request, response);
    }

    @Override
    public void destroy()
    {

    }
}
