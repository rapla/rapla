package org.rapla.server.spring;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.plugin.mail.server.MailInterface;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * PRD 118 D8-3 — under {@code demo} every {@code /api} request and the Swing/JNLP paths answer 404
 * unless allowlisted (fail-closed). The inventory below is the decision record: a new endpoint fails
 * {@link #everyApiMappingIsDecided()} until someone files it as open or closed.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(DemoApiAllowlistTest.CountingMail.class)
@Tag("e2e")
class DemoApiAllowlistTest extends IsolatedDefaultDatasetTest
{
    static final Set<String> OPEN = Set.of(
            "DELETE /api/auth/api-keys/{id}", "GET /api/auth/api-keys", "POST /api/auth/api-keys", "POST /api/auth/api-keys/{id}/rotate",
            "GET /api/auth/me", "POST /api/auth/session/logout", "POST /api/auth/session/refresh",
            "POST /api/auth/impersonate", "POST /api/auth/impersonate/end", "POST /api/auth/impersonate/switch", // D8-13
            "DELETE /api/documents/{name}", "GET /api/documents", "GET /api/documents/{name}", "GET /api/documents/{name}/csv",
            "GET /api/documents/{name}/source", "POST /api/documents/preview", "POST /api/documents/result-shape", "PUT /api/documents/{name}",
            "DELETE /api/favorites/{id}", "GET /api/favorites", "POST /api/favorites",
            "DELETE /api/recents", "GET /api/recents", "POST /api/recents",
            "GET /api/users", "GET /api/users/me",
            "POST /api/graphql",
            "POST /api/storage/change/name", "GET /api/storage/profile/capabilities");

    static final Set<String> CLOSED = Set.of(
            "GET /api/__test/throw-new-version", "GET /api/_smoketest/echo/{name}", "GET /api/_smoketest/greet", "GET /api/_smoketest/ping",
            "POST /api/_smoketest/upper",
            "GET /api/admin/panels", "GET /api/admin/panels/{id}", "POST /api/admin/panels/{id}/action/{actionId}", "POST /api/admin/panels/{id}/save",
            "GET /api/admin/permission-migration/findings", "POST /api/admin/permission-migration/{allocatableId}/resolve",
            "GET /api/auth/oauth/config", "POST /api/auth/oauth/exchange/{providerId}", "POST /api/auth/oauth/token-exchange/{providerId}",
            "GET /api/eventtimecalculator/system-config", "GET /api/eventtimecalculator/user-config",
            "GET /api/exchange/config/default", "GET /api/exchange/config/timezones", "GET /api/exchange/config/user", "GET /api/exchange/connect",
            "POST /api/exchange/connect", "POST /api/exchange/connect/refreshMailboxes", "POST /api/exchange/connect/remove",
            "POST /api/exchange/connect/synchronize",
            "POST /api/edit/check-conflicts", "POST /api/edit/expand-blocks", "POST /api/edit/validate-recurrence",
            "POST /api/externalids/resolve",
            "GET /api/ical/config", "GET /api/ical/config/default", "GET /api/ical/config/user", "GET /api/ical/timezones",
            "GET /api/ical/timezones/default",
            "GET /api/locale/{id}", "POST /api/locale",
            "GET /api/mail/config", "GET /api/mail/config/external", "POST /api/mail/config",
            "GET /api/plugins", "GET /api/plugins/{id}", "PUT /api/plugins/{id}/enabled",
            "GET /api/settings/calendar", "GET /api/settings/me", "GET /api/settings/system",
            "PUT /api/settings/calendar", "PUT /api/settings/me", "PUT /api/settings/system",
            "GET /api/storage/conflicts", "GET /api/storage/resources", "GET /api/storage/user",
            "POST /api/storage/allocatable/bindings/all", "POST /api/storage/allocatable/bindings/first", "POST /api/storage/allocatable/date/next",
            "POST /api/storage/change/email", "POST /api/storage/change/password", "POST /api/storage/confirm/email",
            "POST /api/storage/dispatch", "POST /api/storage/entity/dependent", "POST /api/storage/entity/recursiveSync",
            "POST /api/storage/identifier", "POST /api/storage/merge", "POST /api/storage/queryAppointments", "POST /api/storage/refresh",
            "POST /api/storage/refreshAllEvents", "POST /api/storage/restart", "POST /api/storage/user/{userId}/disconnect-external-auth",
            "GET /api/table/columns/catalog", "GET /api/table/config", "POST /api/table/appointments", "POST /api/table/reservations",
            "POST /api/templateimport/importFromServer", "POST /api/urlencryption",
            "PUT /api/logger/{id}");

    static final String SWING_AUTHORIZE_QUERY = "response_type=code&client_id=rapla-client"
            + "&redirect_uri=http://127.0.0.1:53111/login/oauth2/code/rapla"
            + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&scope=openid&state=xyz";

    static final AtomicInteger SENT = new AtomicInteger();

    @TestConfiguration
    static class CountingMail
    {
        @Bean
        @Primary
        MailInterface countingMail()
        {
            return (sender, recipient, subject, body) -> SENT.incrementAndGet();
        }
    }

    @AfterAll
    static void noMailLeftTheServer()
    {
        assertEquals(0, SENT.get(), "mails sent under demo");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationContext ctx;

    private Set<String> apiMappings()
    {
        Set<String> out = new TreeSet<>();
        for (RequestMappingHandlerMapping mapping : ctx.getBeansOfType(RequestMappingHandlerMapping.class).values())
        {
            for (RequestMappingInfo info : mapping.getHandlerMethods().keySet())
            {
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (String pattern : info.getPatternValues())
                {
                    if (!pattern.startsWith("/api"))
                    {
                        continue;
                    }
                    if (methods.isEmpty())
                    {
                        out.add("GET " + pattern);
                    }
                    methods.forEach(m -> out.add(m.name() + " " + pattern));
                }
            }
        }
        out.add("POST /api/graphql");
        return out;
    }

    private MockHttpServletResponse call(String mapping) throws Exception
    {
        String[] parts = mapping.split(" ", 2);
        String path = parts[1].replaceAll("\\{[^}]+}", "x").replace("/**", "/x");
        return mockMvc.perform(request(HttpMethod.valueOf(parts[0]), path)).andReturn().getResponse();
    }

    private static boolean isClosedAnswer(MockHttpServletResponse response) throws Exception
    {
        return response.getStatus() == 404 && response.getContentAsString().isEmpty();
    }

    private void assertClosed(MockHttpServletResponse response, String what) throws Exception
    {
        assertEquals(404, response.getStatus(), what);
        assertEquals("", response.getContentAsString(), what);
    }

    @Test
    void everyApiMappingIsDecided()
    {
        Set<String> undecided = new TreeSet<>(apiMappings());
        undecided.removeAll(OPEN);
        undecided.removeAll(CLOSED);
        assertEquals(Set.of(), undecided, "file each new /api mapping under OPEN or CLOSED");
        Set<String> stale = new TreeSet<>(OPEN);
        stale.addAll(CLOSED);
        stale.removeAll(apiMappings());
        assertEquals(Set.of(), stale, "inventory lists mappings that no longer exist");
    }

    @Test
    void closedMappingsAnswerLikeAMissingPath() throws Exception
    {
        assertClosed(mockMvc.perform(get("/api/no-such-endpoint-d8-3")).andReturn().getResponse(), "missing path");
        assertClosed(mockMvc.perform(post("/api/no-such-endpoint-d8-3")).andReturn().getResponse(), "missing path POST");
        List<String> failures = new ArrayList<>();
        for (String mapping : CLOSED)
        {
            MockHttpServletResponse response = call(mapping);
            if (!isClosedAnswer(response))
            {
                failures.add(mapping + " -> " + response.getStatus());
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void openMappingsPassTheFilter() throws Exception
    {
        List<String> failures = new ArrayList<>();
        for (String mapping : OPEN)
        {
            MockHttpServletResponse response = call(mapping);
            if (isClosedAnswer(response))
            {
                failures.add(mapping);
            }
        }
        assertEquals(List.of(), failures);
        assertEquals(200, mockMvc.perform(get("/api/graphql/schema")).andReturn().getResponse().getStatus());
    }

    @Test
    void traversalAndEncodingTricksDoNotReachAClosedPath() throws Exception
    {
        for (String uri : List.of("/api/documents/..;/storage/dispatch", "/api/documents/%2e%2e/storage/dispatch",
                "/api/users;x=1/../storage/dispatch", "/api//storage/dispatch", "/api/documents/%2fstorage%2fdispatch"))
        {
            MockHttpServletResponse response = mockMvc.perform(post(uri)).andReturn().getResponse();
            assertTrue(isClosedAnswer(response) || response.getStatus() == 400, uri + " -> " + response.getStatus());
        }
    }

    @Test
    void swingAndJnlpPathsAreClosed() throws Exception
    {
        for (String path : List.of("/raplaclient", "/raplaclient.jnlp", "/webclient/rapla.jar"))
        {
            assertClosed(mockMvc.perform(get(path)).andReturn().getResponse(), path);
        }
    }

    @Test
    void passwordGrantIsRefused() throws Exception
    {
        MockHttpServletResponse response = mockMvc.perform(post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=password&username=admin&password=&client_id=rapla-client")).andReturn().getResponse();
        assertEquals(400, response.getStatus(), response.getContentAsString());
        assertTrue(response.getContentAsString().contains("unsupported_grant_type"), response.getContentAsString());
    }

    @Test
    void swingOAuthRedirectIsRefused() throws Exception
    {
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc
                .perform(post("/login").with(csrf()).param("username", "admin").param("password", ""))
                .andReturn().getRequest().getSession(false);
        assertNotNull(session);
        MockHttpServletResponse swing = mockMvc.perform(get("/oauth2/authorize?" + SWING_AUTHORIZE_QUERY)
                .accept(MediaType.TEXT_HTML).session(session)).andReturn().getResponse();
        String location = String.valueOf(swing.getHeader("Location"));
        assertEquals(400, swing.getStatus(), location + " " + swing.getContentAsString());
        assertFalse(location.startsWith("http://127.0.0.1"), location);
    }

    private Cookie adminCookie() throws Exception
    {
        Cookie access = mockMvc.perform(post("/login").with(csrf()).param("username", "admin").param("password", ""))
                .andReturn().getResponse().getCookie("access_token");
        assertNotNull(access, "form login must set the access_token cookie");
        return new Cookie("access_token", access.getValue());
    }

    private String graphql(Cookie cookie, String query, String variables) throws Exception
    {
        String body = "{\"query\":" + quote(query) + (variables == null ? "" : ",\"variables\":" + variables) + "}";
        return mockMvc.perform(post("/api/graphql").cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString();
    }

    private static String quote(String s)
    {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }

    @Test
    void adminStillEditsTypesResourcesEventsViewsAndDocuments() throws Exception
    {
        Cookie cookie = adminCookie();
        String type = graphql(cookie, "mutation { saveDynamicType(input: { key: \"demo_room\", name: { default: \"Room\" }, classificationType: RESOURCE,"
                + " attributes: [{ key: \"name\", name: { default: \"Name\" }, valueType: STRING, multiplicity: SINGLE, required: false }] }) { id } }", null);
        assertFalse(type.contains("\"errors\""), type);

        String resourceId = "d8300000-0000-4000-8000-000000000001";
        String resource = graphql(cookie, "mutation { createResource(input: { id: \"" + resourceId
                + "\", typeKey: \"resource\", classification: { resource: { name: \"Room 1\" } } }) { id } }", null);
        assertFalse(resource.contains("\"errors\""), resource);

        String event = graphql(cookie, "mutation ($input: ReservationInput!) { createReservation(input: $input) { id } }",
                "{\"input\":{\"id\":\"d8300000-0000-4000-8000-000000000002\",\"typeKey\":\"event\","
                        + "\"classification\":{\"event\":{\"name\":\"Lecture\"}},"
                        + "\"appointments\":[{\"id\":\"d8300000-0000-4000-8000-000000000003\",\"start\":\"2031-10-05T10:00:00\",\"end\":\"2031-10-05T11:00:00\",\"allDay\":false}],"
                        + "\"allocations\":[{\"resourceId\":\"" + resourceId + "\"}]}}");
        assertFalse(event.contains("\"errors\""), event);

        String view = graphql(cookie, "mutation { saveView(name: \"DemoView\", query: \"query DemoView @view(title: \\\"Demo\\\") { serverTime }\") { __typename } }", null);
        assertFalse(view.contains("\"errors\""), view);

        MockHttpServletResponse document = mockMvc.perform(put("/api/documents/demo_doc").cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewName\":\"DemoView\",\"template\":\"<p>{{serverTime}}</p>\",\"isPublic\":false,\"groups\":[]}"))
                .andReturn().getResponse();
        assertTrue(document.getStatus() < 300, document.getStatus() + " " + document.getContentAsString());
    }

    @Test
    void mailPathsAreClosedForTheAdminToo() throws Exception
    {
        Cookie cookie = adminCookie();
        for (MockHttpServletRequestBuilder mail : List.of(
                post("/api/storage/confirm/email").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"newEmail\":\"x@example.org\"}"),
                post("/api/storage/change/email").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"newEmail\":\"x@example.org\"}")))
        {
            assertClosed(mockMvc.perform(mail.cookie(cookie).with(csrf())).andReturn().getResponse(), "mail path");
        }
        assertEquals(0, SENT.get());
    }
}
