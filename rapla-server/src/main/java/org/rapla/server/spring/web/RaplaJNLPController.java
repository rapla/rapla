package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.server.servletpages.RaplaJNLPPageGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/**
 * Preserves the JNLP launch URL: /rapla/raplaclient and /rapla/raplaclient.jnlp.
 * (HARD CONSTRAINT — see PRD URL Path Preservation.)
 *
 * Also serves the webclient/ jar set referenced from the generated JNLP. The jars
 * physically live in BOOT-INF/lib/ inside the fat JAR (signed + manifest-patched
 * by the antrun replace-bootinf-lib-with-signed step). No duplicate copy under
 * static/webclient/ — saves ~14 MB. We resolve each request by suffix-matching the
 * jar name against the runtime classpath URLs.
 */
@RestController
@ConditionalOnBean(RaplaJNLPPageGenerator.class)
public class RaplaJNLPController
{
    private final RaplaJNLPPageGenerator generator;

    public RaplaJNLPController(RaplaJNLPPageGenerator generator)
    {
        this.generator = generator;
    }

    @GetMapping("/raplaclient")
    public void raplaclient(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        generator.generatePage(request, response, "");
    }

    @GetMapping("/raplaclient.jnlp")
    public void raplaclientJnlp(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        generator.generatePage(request, response, ".jnlp");
    }

    @GetMapping("/webclient/{name:.+\\.jar}")
    public ResponseEntity<Resource> webclientJar(jakarta.servlet.http.HttpServletRequest request,
                                                 @PathVariable("name") String name) throws IOException
    {
        if (name.contains("/") || name.contains("\\") || name.contains(".."))
        {
            return ResponseEntity.badRequest().build();
        }
        if (!isWebclientJar(request.getServletContext(), name))
        {
            // Don't expose server-only jars (e.g. tomcat-embed-core) to /webclient/
            return ResponseEntity.notFound().build();
        }
        URL jarUrl = locateClasspathJar(name);
        if (jarUrl == null)
        {
            return ResponseEntity.notFound().build();
        }
        UrlResource resource = new UrlResource(jarUrl);
        long contentLength = resource.contentLength();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/java-archive"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentLength(contentLength)
                .body(resource);
    }

    private boolean isWebclientJar(jakarta.servlet.ServletContext context, String name) throws IOException
    {
        // RaplaJNLPPageGenerator reads the same clientlibs.properties to drive the JNLP body,
        // so the served set is exactly what the JNLP advertises — no more, no less.
        String prefix = "webclient/";
        for (String entry : RaplaJNLPPageGenerator.getClientLibs(context))
        {
            if (entry.startsWith(prefix) && entry.substring(prefix.length()).equals(name))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a webclient jar name (e.g. "rapla-client-2.1-SNAPSHOT.jar") to a URL.
     * Tries two locations in order:
     *   1. The application classpath, via {@code java.class.path} — covers jars in
     *      BOOT-INF/lib/ in extracted-fat-JAR mode (PRD 018) and plain {@code java -cp} runs.
     *      Java's app classloader is not a {@link java.net.URLClassLoader} since Java 9,
     *      so reading the system property is the portable way to enumerate classpath entries.
     *   2. The classpath resource {@code static/webclient/<name>} — covers jars that were
     *      EXCLUDED from BOOT-INF/lib/ (e.g. rxjava is purely client-side; not on the
     *      server's classpath). Resolved through the classloader so it works whether the
     *      app is running from extracted dirs or from inside a fat JAR.
     */
    private URL locateClasspathJar(String name)
    {
        String target = name.toLowerCase(Locale.ROOT);
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(File.pathSeparator))
        {
            if (entry.isEmpty()) continue;
            File file = new File(entry);
            if (!file.isFile()) continue;
            if (!file.getName().toLowerCase(Locale.ROOT).equals(target)) continue;
            try
            {
                return file.toURI().toURL();
            }
            catch (MalformedURLException ignored)
            {
                // try the next match
            }
        }
        // Fallback: jars excluded from BOOT-INF/lib/ but bundled under static/webclient/
        return getClass().getClassLoader().getResource("static/webclient/" + name);
    }
}
