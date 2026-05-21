package org.rapla.server.spring.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.internal.AbstractRaplaLocale;
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
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
public class RaplaJNLPController
{
    private static final TypedComponentRole<Boolean> CREATE_SHORTCUT = new TypedComponentRole<>("org.rapla.jnlp.createshortcut");
    private static final TypedComponentRole<Integer> CLIENT_VM_MIN_SIZE = new TypedComponentRole<>("org.rapla.jnlp.xms");

    private final RaplaFacade facade;
    private final RaplaResources i18n;

    public RaplaJNLPController(RaplaFacade facade, RaplaResources i18n)
    {
        this.facade = facade;
        this.i18n = i18n;
    }

    @GetMapping("/raplaclient")
    public void raplaclient(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        generateJnlp(request, response);
    }

    @GetMapping("/raplaclient.jnlp")
    public void raplaclientJnlp(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        generateJnlp(request, response);
    }

    /** Package-visible so JNLP tests can construct the controller and exercise the
     *  full JNLP-XML generation flow without going through MockMvc. */
    void generateJnlp(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        PrintWriter out = response.getWriter();
        String webstartRoot = ".";
        long currentTimeMillis = System.currentTimeMillis();
        response.setDateHeader("Last-Modified", currentTimeMillis);
        response.addDateHeader("Expires", currentTimeMillis + DateTools.MILLISECONDS_PER_MINUTE);
        response.addDateHeader("Date", currentTimeMillis);
        response.setHeader("Cache-Control", "no-cache");
        final String defaultTitle = i18n.getString("rapla.title");
        String menuName;
        boolean createShortcut = true;
        Integer vmXmsSize = null;
        try
        {
            final Preferences systemPreferences = facade.getSystemPreferences();
            menuName = systemPreferences.getEntryAsString(AbstractRaplaLocale.TITLE, defaultTitle);
            createShortcut = systemPreferences.getEntryAsBoolean(CREATE_SHORTCUT, true);
            vmXmsSize = systemPreferences.getEntryAsInteger(CLIENT_VM_MIN_SIZE, -1);
        }
        catch (RaplaException e)
        {
            menuName = defaultTitle;
        }
        response.setContentType("application/x-java-jnlp-file;charset=utf-8");
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        String codebase = getCodebase(request);
        out.println("<jnlp spec=\"6.0+\" codebase=\"" + codebase + "\" href=\"" + codebase + "raplaclient.jnlp\" >");
        out.println("<information>");
        out.println(" <title>" + menuName + "</title>");
        out.println(" <vendor>Rapla team</vendor>");
        out.println(" <homepage href=\"https://rapla.org\"/>");
        out.println(" <description>Resource Scheduling Application</description>");
        out.println(" <icon kind=\"default\" href=\"" + codebase + "webclient/rapla_64x64.png\" width=\"64\" height=\"64\"/> ");
        out.println(" <icon kind=\"shortcut\" href=\"" + codebase + "webclient/rapla_64x64.png\" width=\"64\" height=\"64\"/> ");
        if (createShortcut)
        {
            out.println(" <shortcut online=\"true\">");
            out.println("       <desktop/>");
            out.println("       <menu submenu=\"" + menuName + "\"/>");
            out.println(" </shortcut>");
        }
        out.println("</information>");
        out.println("<update check=\"always\" policy=\"always\"/>");
        out.println("<security>");
        out.println("  <all-permissions/>");
        out.println("</security>");
        out.println("<resources>");
        if (vmXmsSize != null && vmXmsSize > 0)
        {
            out.println("  <j2se version=\"1.8+ \" java-vm-args=\"-Xms" + vmXmsSize + "m\"/>");
        }
        else
        {
            out.println("  <j2se version=\"1.8+\"/>");
        }

        // rapla.download.url is the server root URL WITHOUT the servlet context path —
        // the REST proxy appends the context path itself. Emitting the full codebase
        // (e.g. http://host:port/rapla/) caused doubled paths like /rapla/rapla/auth/login → 401.
        String contextPath = request.getContextPath();
        String rootUrl = codebase;
        if (contextPath != null && !contextPath.isEmpty() && rootUrl.endsWith(contextPath + "/"))
        {
            rootUrl = rootUrl.substring(0, rootUrl.length() - contextPath.length() - 1) + "/";
        }
        out.println("  <property name=\"rapla.download.url\" value=\"" + rootUrl + "\"/>");

        String passedUsername = request.getParameter("username");
        if (passedUsername != null)
        {
            String usernameProperty = "jnlp.org.rapla.startupUser";
            String safeUsername = URLEncoder.encode(passedUsername, "UTF-8");
            out.println("  <property name=\"" + usernameProperty + "\" value=\"" + safeUsername + "\"/>");
        }
        out.println(getLibsJNLP(request.getServletContext(), webstartRoot));
        out.println("</resources>");
        out.println("<application-desc main-class=\"org.rapla.client.spring.SpringRaplaClient\">");
        for (Iterator<String> it = getProgramArguments().iterator(); it.hasNext();)
        {
            out.println("  <argument>" + it.next() + "</argument> ");
        }
        out.println("</application-desc>");
        out.println("</jnlp>");
        out.close();
    }

    @GetMapping("/webclient/{name:.+\\.jar}")
    public ResponseEntity<Resource> webclientJar(HttpServletRequest request,
                                                 @PathVariable("name") String name) throws IOException
    {
        if (name.contains("/") || name.contains("\\") || name.contains(".."))
        {
            return ResponseEntity.badRequest().build();
        }
        if (!isWebclientJar(request.getServletContext(), name))
        {
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

    private boolean isWebclientJar(ServletContext context, String name) throws IOException
    {
        String prefix = "webclient/";
        for (String entry : getClientLibs(context))
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
     * Tries the application classpath (via {@code java.class.path}, covering both
     * extracted-fat-JAR and {@code java -cp} runs), then falls back to the
     * {@code static/webclient/<name>} classpath resource (jars excluded from
     * BOOT-INF/lib/ but still bundled).
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
        return getClass().getClassLoader().getResource("static/webclient/" + name);
    }

    private String getCodebase(HttpServletRequest request)
    {
        StringBuffer codebaseBuffer = new StringBuffer();
        String forwardProto = request.getHeader("X-Forwarded-Proto");
        String forwardPort = request.getHeader("X-Forwarded-Port");
        boolean secure = (forwardProto != null && forwardProto.equalsIgnoreCase("https")) || request.isSecure();
        codebaseBuffer.append(secure ? "https://" : "http://");
        codebaseBuffer.append(request.getServerName());
        if (forwardPort != null)
        {
            codebaseBuffer.append(':');
            codebaseBuffer.append(forwardPort);
        }
        else if (forwardProto == null && request.getServerPort() != (!secure ? 80 : 443))
        {
            codebaseBuffer.append(':');
            codebaseBuffer.append(request.getServerPort());
        }
        codebaseBuffer.append(request.getContextPath());
        codebaseBuffer.append('/');
        return codebaseBuffer.toString();
    }

    private String getLibsJNLP(ServletContext context, String webstartRoot) throws IOException
    {
        List<String> list = getClientLibs(context);
        StringBuffer buf = new StringBuffer();
        for (String file : list)
        {
            buf.append("\n<jar href=\"" + webstartRoot + "/");
            buf.append(file);
            buf.append("\"");
            if (isMainRaplaClientJar(file))
            {
                buf.append(" main=\"true\"");
            }
            buf.append("/>");
        }
        return buf.toString();
    }

    /**
     * Matches both the legacy unversioned {@code rapla-client.jar} and the Maven-packaged
     * {@code rapla-client-<version>.jar}. Excludes sibling artifacts like a hypothetical
     * {@code rapla-client-api.jar} (next char after {@code rapla-client} must be a digit
     * when versioned).
     */
    static boolean isMainRaplaClientJar(String pathOrName)
    {
        return pathOrName != null && pathOrName.matches(".*rapla-client(-[0-9][\\w.-]*)?\\.jar$");
    }

    /** Package-visible so JNLP tests can stage a fake webclient/ dir and exercise the
     *  fallback (servlet-context filesystem) branch deterministically. */
    static List<String> getClientLibs(ServletContext context) throws IOException
    {
        List<String> list = new ArrayList<>();
        URL resource = RaplaJNLPController.class.getResource("/clientlibs.properties");
        if (resource != null)
        {
            byte[] bytes = IOUtil.readBytes(resource);
            String string = new String(bytes);
            String[] split = string.split(";");
            for (String file : split)
            {
                list.add("webclient/" + file);
            }
        }
        else
        {
            String base = context.getRealPath(".");
            if (base != null)
            {
                File baseFile = new File(base);
                File[] files = IOUtil.getJarFiles(base, "webclient");
                for (File file : files)
                {
                    String relativeURL = IOUtil.getRelativeURL(baseFile, file);
                    list.add(relativeURL);
                }
            }
        }
        int size = list.size();
        for (int i = 0; i < size; i++)
        {
            String entry = list.get(i);
            if (isMainRaplaClientJar(entry))
            {
                list.remove(i);
                list.add(0, entry);
            }
        }
        return list;
    }

    protected List<String> getProgramArguments()
    {
        return new ArrayList<>();
    }
}
