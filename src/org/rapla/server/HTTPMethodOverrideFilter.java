package org.rapla.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

public class HTTPMethodOverrideFilter implements Filter
{
    Collection<String> VALID_METHODS = Arrays.asList(new String[] {"GET","POST","DELETE","PUT","PATCH"});

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)   throws IOException,  ServletException {
        MethodOverrideWrapper wrapper = new MethodOverrideWrapper( (HttpServletRequest) request);
        chain.doFilter(wrapper, response);

        HttpServletResponse hresponse = (HttpServletResponse) response;
        hresponse.addHeader("Vary", "X-HTTP-Method-Override");
    }

    private class MethodOverrideWrapper extends HttpServletRequestWrapper 
    {
        public MethodOverrideWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getMethod() {
            String method = super.getMethod();
            String newMethod = getHeader("X-HTTP-Method-Override");
            if ("POST".equals(method) &&  newMethod != null &&  VALID_METHODS.contains(newMethod)) 
            {
                method = newMethod;
            }
            return method;
        }
    }

}