package org.rapla.server.spring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PRD 125 Phase 2: the SPA sends the hashed main bundle name it was loaded with as
 * X-Rapla-Build; when the index.html served now names a different one, the response
 * carries X-Rapla-Build-Mismatch so the SPA can offer a reload. Never rejects (D1).
 */
@Component
public class SpaBuildMismatchFilter extends OncePerRequestFilter
{
    static final String BUILD_HEADER = "X-Rapla-Build";
    static final String MISMATCH_HEADER = "X-Rapla-Build-Mismatch";
    private static final Pattern MAIN_BUNDLE = Pattern.compile("main-[\\w-]+\\.js");

    private record Served(long modified, String build) {}

    private final Resource devIndex;
    private final String jarBuild;
    private volatile Served devServed = new Served(-1, null);

    public SpaBuildMismatchFilter(ResourceLoader resourceLoader,
            @Value("${rapla.spa.dev-dir:../rapla-angular/dist/rapla-angular/browser/}") String devDir)
    {
        this.devIndex = resourceLoader.getResource("file:" + devDir + "index.html");
        this.jarBuild = read(resourceLoader.getResource("classpath:/static/app/index.html"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String clientBuild = request.getHeader(BUILD_HEADER);
        return clientBuild == null || "dev".equals(clientBuild) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String served = servedBuild();
        if (served != null && !served.equals(request.getHeader(BUILD_HEADER)))
        {
            response.setHeader(MISMATCH_HEADER, served);
        }
        chain.doFilter(request, response);
    }

    // Same location order as SpaResourceConfig. The classpath index.html never changes at
    // runtime; only the dev dir is re-read, lock-free (a concurrent double read is harmless).
    private String servedBuild()
    {
        if (!devIndex.exists() || !devIndex.isReadable())
        {
            return jarBuild;
        }
        long modified;
        try
        {
            modified = devIndex.lastModified();
        }
        catch (IOException e)
        {
            return null;
        }
        Served served = devServed;
        if (served.modified() != modified)
        {
            served = new Served(modified, read(devIndex));
            devServed = served;
        }
        return served.build();
    }

    private static String read(Resource index)
    {
        if (!index.exists() || !index.isReadable())
        {
            return null;
        }
        try (InputStream in = index.getInputStream())
        {
            Matcher m = MAIN_BUNDLE.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return m.find() ? m.group() : null;
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
