package org.rapla.server.spring;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.plugin.archiver.server.ArchiverServiceImpl;
import org.rapla.plugin.archiver.server.ArchiverServiceTask;
import org.rapla.plugin.exchangeconnector.server.ExchangeSchedulerTrigger;
import org.rapla.plugin.notification.server.NotificationService;
import org.rapla.server.spring.document.DocumentCatalogService;
import org.rapla.server.spring.graphql.ViewCatalogService;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.patch.ArtifactPatchLoader;
import org.rapla.server.spring.web.Export2iCalController;
import org.rapla.server.spring.web.ExternalEventImportController;
import org.rapla.server.spring.web.ICalImportController;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.rapla.server.spring.web.JNDIConfigController;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PRD 118 D8-1 — the {@code demo} profile ({@code application-demo.yml}). Every switch is asserted
 * by its effective behaviour; {@link DemoProfileTest} runs the checks under {@code demo},
 * {@link DemoProfileDefaultTest} under the default profile. The store is the server-created default
 * system (user {@code admin}, empty password) like the plain demo seed. Login goes through the form
 * login + auth cookie the SPA uses, not the password grant (off under {@code demo} from D8-3 on).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@Tag("e2e")
abstract class DemoProfileChecks extends IsolatedDefaultDatasetTest
{
    @Autowired MockMvc mockMvc;
    @Autowired ApplicationContext ctx;
    @Autowired CachableStorageOperator operator;
    @Autowired ViewCatalogService views;
    @Autowired DocumentCatalogService documents;

    abstract boolean demo();

    private Cookie adminCookie() throws Exception
    {
        Cookie access = mockMvc.perform(post("/login").with(csrf()).param("username", "admin").param("password", ""))
                .andReturn().getResponse().getCookie("access_token");
        assertNotNull(access, "form login must set the access_token cookie");
        return new Cookie("access_token", access.getValue());
    }

    private MockHttpServletResponse asAdmin(MockHttpServletRequestBuilder request) throws Exception
    {
        return mockMvc.perform(request.cookie(adminCookie())).andReturn().getResponse();
    }

    private int beans(Class<?> type)
    {
        return ctx.getBeanNamesForType(type).length;
    }

    @Test
    void adminPasswordIsFixed() throws Exception
    {
        User admin = operator.getUser("admin");
        if (demo())
        {
            RaplaSecurityException refused = assertThrows(RaplaSecurityException.class,
                    () -> operator.changePassword(admin, new char[0], "changed".toCharArray()));
            assertTrue(refused.getMessage().contains("rapla.fix-admin-password"), refused.getMessage());
        }
        else
        {
            operator.changePassword(admin, new char[0], "changed".toCharArray());
            operator.changePassword(operator.getUser("admin"), "changed".toCharArray(), new char[0]);
        }
    }

    @Test
    void impersonationIsSwitchedOff() throws Exception
    {
        MockHttpServletResponse response = asAdmin(post("/api/auth/impersonate").with(csrf())
                .contentType("application/x-www-form-urlencoded").content("target_username=admin"));
        if (demo())
        {
            assertTrue(response.getStatus() == 403 || response.getStatus() == 404, response.getStatus() + " " + response.getContentAsString());
            assertEquals("", response.getContentAsString());
        }
        else
        {
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertTrue(response.getContentAsString().contains("access_token"), response.getContentAsString());
        }
    }

    @Test
    void onlyTheLocalLoginProviderIsOffered() throws Exception
    {
        assertEquals(List.of(), ctx.getBean(ExternalProvidersProperties.class).enabledProviders());
    }

    @Test
    void exchangeSyncAndExternalEventImportAreOff()
    {
        assertEquals(0, beans(ExchangeSchedulerTrigger.class));
        assertEquals(0, beans(ExternalEventImportController.class));
    }

    @Test
    void notificationArchiverJndiAndICalImportPluginsAreOff() throws Exception
    {
        for (Class<?> type : List.of(NotificationService.class, ArchiverServiceTask.class, ArchiverServiceImpl.class,
                JNDIConfigController.class, ICalImportController.class))
        {
            assertEquals(!demo(), beans(type) > 0, type.getSimpleName());
        }
        for (String path : List.of("/api/jndi", "/api/archiver"))
        {
            int status = asAdmin(get(path)).getStatus();
            if (demo())
            {
                assertEquals(404, status, path);
            }
            else
            {
                assertNotEquals(404, status, path);
            }
        }
    }

    @Test
    void iCalExportAndPatchLoaderStayOn()
    {
        assertEquals(1, beans(Export2iCalController.class));
        assertEquals(1, beans(ArtifactPatchLoader.class));
    }

    @Test
    void serverStatusPageIsOff() throws Exception
    {
        int status = mockMvc.perform(get("/server")).andReturn().getResponse().getStatus();
        assertEquals(demo() ? 404 : 200, status);
    }

    @Test
    void authorScriptsAreStripped() throws Exception
    {
        User admin = operator.getUser("admin");
        String view = "demo_scripts_view";
        assertEquals(List.of(), views.saveView(view, "query %s @view(title: \"Scripts\") { serverTime }".formatted(view), true, List.of(), null, admin));
        assertEquals(List.of(), documents.save("demo_scripts", view, "<p>{{serverTime}}</p><script>window.print()</script>", false, List.of(), null, admin));
        MockHttpServletResponse response = asAdmin(get("/api/documents/demo_scripts"));
        assertEquals(200, response.getStatus());
        assertFalse(response.getContentAsString().contains("<script>"), response.getContentAsString());
    }

    static final String BANNER = "Demo — data resets nightly at 04:15";

    @Test
    void loginPageShowsTheDemoBannerAndTheDemoLoginNote() throws Exception
    {
        String page = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        assertEquals(demo(), page.contains(org.springframework.web.util.HtmlUtils.htmlEscape(BANNER)), page);
        assertEquals(demo(), page.contains("Demo login: <code>admin</code>"), page);
        assertEquals(!demo(), page.contains("Dev default: <code>admin</code> with empty password."), page);
    }

    @Test
    void identityCarriesTheDemoBannerForTheSpaShell() throws Exception
    {
        String me = asAdmin(get("/api/auth/me")).getContentAsString();
        if (demo())
        {
            assertTrue(me.contains("\"demoBanner\":\"" + BANNER + "\""), me);
        }
        else
        {
            assertFalse(me.contains(BANNER), me);
        }
    }

    @Test
    void graphqlQueryWindowIsCapped() throws Exception
    {
        String query = "{\"query\":\"{ reservations(filter: {from: \\\"2026-01-01T00:00:00\\\", to: \\\"2027-03-01T00:00:00\\\"}) { id } }\"}";
        String body = asAdmin(post("/api/graphql").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(query)).getContentAsString();
        assertEquals(demo(), body.contains("Time window > 400 days"), body);
    }
}
