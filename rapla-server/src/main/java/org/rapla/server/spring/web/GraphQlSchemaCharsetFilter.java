package org.rapla.server.spring.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Stamps {@code charset=UTF-8} onto the Spring-GraphQL schema-printer response.
 *
 * <p>Spring-GraphQL's {@code SchemaHandler.handleRequest} hard-codes
 * {@code MediaType.TEXT_PLAIN} with no charset on the SDL response served at
 * {@code ${spring.graphql.http.path}/schema}. The SDL bytes are UTF-8 (the
 * {@code """…"""} type descriptions contain em-dashes etc.), but per RFC 6657
 * {@code text/plain} has no default charset — and legacy browsers/clients then
 * fall back to ISO-8859-1, rendering UTF-8 multibyte sequences as mojibake
 * ("â€"" instead of "—").
 *
 * <p>Path-scoped (option B, chosen 2026-06-05): only the schema endpoint is
 * touched, so the JSON {@code /api/graphql} endpoint is unaffected (no
 * {@code charset} stamped on {@code application/json}, which RFC 8259
 * discourages). The clean long-term fix is upstream in spring-graphql; this
 * compensates until then.
 *
 * <p>Active only when the printer itself is enabled
 * ({@code spring.graphql.schema.printer.enabled=true}) — otherwise the route
 * doesn't exist and the filter would be dead weight.
 */
@Component
@ConditionalOnProperty(prefix = "spring.graphql.schema.printer", name = "enabled", havingValue = "true")
public class GraphQlSchemaCharsetFilter extends OncePerRequestFilter
{
    private final String schemaPath;

    public GraphQlSchemaCharsetFilter(
            @Value("${spring.graphql.http.path:/graphql}") String graphQlHttpPath)
    {
        this.schemaPath = graphQlHttpPath + "/schema";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        return !schemaPath.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        chain.doFilter(request, new Utf8TextPlainResponse(response));
    }

    /**
     * Intercepts the content-type the downstream {@code SchemaHandler} sets and
     * appends {@code charset=UTF-8} when it's a bare {@code text/plain}. Leaves
     * any explicitly-charsetted or non-text/plain type alone.
     */
    private static final class Utf8TextPlainResponse extends HttpServletResponseWrapper
    {
        Utf8TextPlainResponse(HttpServletResponse response)
        {
            super(response);
        }

        @Override
        public void setContentType(String type)
        {
            super.setContentType(withUtf8(type));
        }

        @Override
        public void setHeader(String name, String value)
        {
            if ("Content-Type".equalsIgnoreCase(name))
            {
                super.setHeader(name, withUtf8(value));
            }
            else
            {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value)
        {
            // Spring's ServletServerHttpResponse.writeHeaders() flushes the
            // ServerResponse header map via addHeader — including Content-Type.
            if ("Content-Type".equalsIgnoreCase(name))
            {
                super.addHeader(name, withUtf8(value));
            }
            else
            {
                super.addHeader(name, value);
            }
        }

        private static String withUtf8(String type)
        {
            if (type == null) return null;
            String lower = type.toLowerCase(Locale.ROOT);
            if (lower.startsWith("text/plain") && !lower.contains("charset"))
            {
                return "text/plain;charset=UTF-8";
            }
            return type;
        }
    }
}
