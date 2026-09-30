package org.rapla.server.spring.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.RaplaResources;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RaplaJNLPControllerTest
{
    private RaplaJNLPController controller;
    private RaplaFacade facade;
    private RaplaResources i18n;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter body;
    private java.io.File fakeWebRoot;

    @BeforeEach
    void setUp() throws Exception
    {
        facade = mock(RaplaFacade.class);
        Preferences prefs = mock(Preferences.class);
        when(facade.getSystemPreferences()).thenReturn(prefs);
        when(prefs.getEntryAsString(any(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(prefs.getEntryAsBoolean(any(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(prefs.getEntryAsInteger(any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        i18n = mock(RaplaResources.class);
        when(i18n.getString("rapla.title")).thenReturn("Rapla");

        controller = new RaplaJNLPController(facade, i18n, "");

        request = mock(HttpServletRequest.class);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8051);
        when(request.getContextPath()).thenReturn("/rapla");
        when(request.isSecure()).thenReturn(false);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);

        fakeWebRoot = java.nio.file.Files.createTempDirectory("rapla-jnlp-test-").toFile();
        java.io.File webclient = new java.io.File(fakeWebRoot, "webclient");
        webclient.mkdirs();
        new java.io.File(webclient, "rapla-core-2.1-SNAPSHOT.jar").createNewFile();
        new java.io.File(webclient, "rapla-client-2.1-SNAPSHOT.jar").createNewFile();
        new java.io.File(webclient, "slf4j-api-2.0.17.jar").createNewFile();
        when(servletContext.getRealPath(".")).thenReturn(fakeWebRoot.getAbsolutePath());

        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    @Test
    void iconHrefDoesNotProduceDoubleSlashAfterContextPath() throws Exception
    {
        controller.generateJnlp(request, response);
        String jnlp = body.toString();

        assertTrue(jnlp.contains("<icon "), "expected <icon> entries in:\n" + jnlp);
        assertFalse(jnlp.contains("/rapla//webclient/"),
                "icon URL must not contain '/rapla//webclient/' (Spring Security 401s the doubled slash):\n" + jnlp);
    }

    @Test
    void mainRaplaClientJarIsMarkedMainTrueRegardlessOfVersion() throws Exception
    {
        controller.generateJnlp(request, response);
        String jnlp = body.toString();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<jar href=\"[^\"]*rapla-client-[0-9][^\"]*\\.jar\"([^/]*)/>").matcher(jnlp);
        assertTrue(m.find(), "expected a <jar href=\"...rapla-client-VERSION.jar\".../> entry in:\n" + jnlp);
        assertTrue(m.group(1).contains("main=\"true\""),
                "rapla-client jar entry must carry main=\"true\" but found: <jar href=\"...rapla-client-...jar\""
                        + m.group(1) + "/>");
    }

    @Test
    void mainRaplaClientJarIsListedFirst() throws Exception
    {
        controller.generateJnlp(request, response);
        String jnlp = body.toString();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<jar href=\"[^\"]*\"").matcher(jnlp);
        assertTrue(m.find(), "expected at least one <jar> entry in:\n" + jnlp);
        String firstJar = m.group();
        assertTrue(firstJar.matches(".*rapla-client-[0-9][^\"]*\\.jar.*"),
                "first <jar> in JNLP should be the main rapla-client jar but was: " + firstJar);
    }

    @Test
    void noUrlInJnlpHasDoubleSlashAfterAuthority() throws Exception
    {
        controller.generateJnlp(request, response);
        String jnlp = body.toString();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s\"<>]+").matcher(jnlp);
        while (m.find())
        {
            String url = m.group();
            int afterScheme = url.indexOf("://") + 3;
            int afterAuthority = url.indexOf('/', afterScheme);
            if (afterAuthority < 0) continue;
            String path = url.substring(afterAuthority);
            assertFalse(path.contains("//"),
                    "URL in JNLP has a doubled slash in its path: " + url);
        }
    }

    // ============================================================ codebase (ruling 2026-10-01, option 1)

    private String codebaseOf(String jnlp)
    {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("codebase=\"([^\"]*)\"").matcher(jnlp);
        assertTrue(m.find(), "no codebase in:\n" + jnlp);
        return m.group(1);
    }

    @Test
    void codebaseIsTheRequestOriginIncludingANonStandardPort() throws Exception
    {
        controller.generateJnlp(request, response);
        assertEquals("http://localhost:8051/", codebaseOf(body.toString()));
    }

    @Test
    void configuredBaseUrlOnTheRequestHostIsTheCodebase() throws Exception
    {
        new RaplaJNLPController(facade, i18n, "https://localhost").generateJnlp(request, response);
        assertEquals("https://localhost/", codebaseOf(body.toString()));
    }

    /** An external IdP origin (Keycloak) is not rapla's origin — the request origin wins. */
    @Test
    void configuredBaseUrlOnAnotherHostIsIgnored() throws Exception
    {
        new RaplaJNLPController(facade, i18n, "https://keycloak.example.com").generateJnlp(request, response);
        assertEquals("http://localhost:8051/", codebaseOf(body.toString()));
    }

    @Test
    void ipv6LiteralHostIsBracketed() throws Exception
    {
        when(request.getServerName()).thenReturn("::1");
        controller.generateJnlp(request, response);
        assertEquals("http://[::1]:8051/", codebaseOf(body.toString()));
    }

    @Test
    void hostWithMarkupCharactersAnswers400WithoutWritingTheJnlp() throws Exception
    {
        when(request.getServerName()).thenReturn("evil\"><x");
        controller.generateJnlp(request, response);
        verify(response).sendError(400);
        assertEquals("", body.toString());
    }

    /** Cache-Control: no-store is the contract; an Expires date contradicts it. */
    @Test
    void noExpiresHeader() throws Exception
    {
        controller.generateJnlp(request, response);
        verify(response, never()).addDateHeader(eq("Expires"), anyLong());
        verify(response, never()).setDateHeader(eq("Expires"), anyLong());
    }
}
