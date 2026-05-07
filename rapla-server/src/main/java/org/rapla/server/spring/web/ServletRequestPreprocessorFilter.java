package org.rapla.server.spring.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.framework.RaplaException;
import org.rapla.server.extensionpoints.ServletRequestPreprocessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Bridges the legacy {@link ServletRequestPreprocessor} extension point onto
 * Spring's {@link jakarta.servlet.Filter} chain. Each registered preprocessor
 * runs in turn; if a preprocessor returns {@code null} or commits the response,
 * the filter chain stops.
 *
 * <p>This preserves the URL-rewrite contract used by {@code UrlEncryption}
 * (decrypts {@code ?key=…} into the plaintext query params before any
 * controller sees the request).
 */
@Component
@ConditionalOnBean(ServletRequestPreprocessor.class)
public class ServletRequestPreprocessorFilter extends OncePerRequestFilter
{
    private final Set<ServletRequestPreprocessor> preprocessors;
    private final ServletContext servletContext;

    public ServletRequestPreprocessorFilter(Set<ServletRequestPreprocessor> preprocessors, ServletContext servletContext)
    {
        this.preprocessors = preprocessors;
        this.servletContext = servletContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException
    {
        HttpServletRequest current = request;
        for (ServletRequestPreprocessor preprocessor : preprocessors)
        {
            try
            {
                HttpServletRequest next = preprocessor.handleRequest(servletContext, current, response);
                if (next != null)
                {
                    current = next;
                }
                if (response.isCommitted())
                {
                    return;
                }
            }
            catch (RaplaException ex)
            {
                throw new ServletException(ex);
            }
        }
        chain.doFilter(current, response);
    }
}
