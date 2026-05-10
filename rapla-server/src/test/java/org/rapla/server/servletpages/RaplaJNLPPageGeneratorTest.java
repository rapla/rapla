package org.rapla.server.servletpages;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RaplaJNLPPageGeneratorTest
{
    private RaplaJNLPPageGenerator generator;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter body;

    @BeforeEach
    void setUp() throws Exception
    {
        RaplaFacade facade = mock(RaplaFacade.class);
        Preferences prefs = mock(Preferences.class);
        when(facade.getSystemPreferences()).thenReturn(prefs);
        when(prefs.getEntryAsString(any(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(prefs.getEntryAsBoolean(any(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(prefs.getEntryAsInteger(any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        RaplaResources i18n = mock(RaplaResources.class);
        when(i18n.getString("rapla.title")).thenReturn("Rapla");

        generator = new RaplaJNLPPageGenerator(facade, i18n);

        request = mock(HttpServletRequest.class);
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8051);
        when(request.getContextPath()).thenReturn("/rapla");
        when(request.isSecure()).thenReturn(false);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);

        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    @Test
    void iconHrefDoesNotProduceDoubleSlashAfterContextPath() throws Exception
    {
        generator.generatePage(request, response, "");
        String jnlp = body.toString();

        assertTrue(jnlp.contains("<icon "), "expected <icon> entries in:\n" + jnlp);
        assertFalse(
                jnlp.contains("/rapla//webclient/"),
                "icon URL must not contain '/rapla//webclient/' (Spring Security 401s the doubled slash):\n" + jnlp);
    }

    @Test
    void noUrlInJnlpHasDoubleSlashAfterAuthority() throws Exception
    {
        generator.generatePage(request, response, "");
        String jnlp = body.toString();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s\"<>]+").matcher(jnlp);
        while (m.find())
        {
            String url = m.group();
            int afterScheme = url.indexOf("://") + 3;
            int afterAuthority = url.indexOf('/', afterScheme);
            if (afterAuthority < 0) continue;
            String path = url.substring(afterAuthority);
            assertFalse(
                    path.contains("//"),
                    "URL in JNLP has a doubled slash in its path: " + url);
        }
    }
}
