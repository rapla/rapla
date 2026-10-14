package org.rapla.server.spring.web;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 131 D7/D12 — tier-3 leak matrix of the act-as filter: a cookie request must say who it acts as
 * ({@code X-Rapla-Act-As}), a permitted id turns it into the impersonation shape, a Bearer request never may.
 * Fixture: {@link ImpersonationRightsFixture} (alice may act as export only; homer is the global admin).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ActAsFilterTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ActAsFilterTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RaplaFacade facade;

    @Autowired
    CachableStorageOperator operator;

    @BeforeEach
    void seed() throws Exception
    {
        ImpersonationRightsFixture.seed(facade, operator);
    }

    private ListAppender<ILoggingEvent> audit;

    @BeforeEach
    void attachAudit()
    {
        audit = new ListAppender<>();
        audit.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("rapla")).addAppender(audit);
    }

    @AfterEach
    void detachAudit()
    {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("rapla")).detachAppender(audit);
        audit.stop();
    }

    private List<String> actAsLines()
    {
        return audit.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("Impersonation (act-as)")).toList();
    }

    @Test
    void actAsIsAuditedOncePerActorTokenAndTarget() throws Exception
    {
        String alice = login("alice");
        String export = idOf("export");
        assertEquals(200, cookieMe(alice, export).getResponse().getStatus());
        assertEquals(List.of("Impersonation (act-as): actor=alice (uuid=" + idOf("alice") + ") target=export (uuid=" + export + ")"),
                actAsLines());
        assertTrue(audit.list.stream().noneMatch(e -> e.getFormattedMessage().contains(alice)), "no token in the log");

        assertEquals(200, cookieMe(alice, export).getResponse().getStatus());
        assertEquals(1, actAsLines().size(), "same token and target: no second line");

        String homer = loginHomer();
        cookieMe(homer, idOf("monty"));
        cookieMe(homer, idOf("alice"));
        cookieMe(homer, idOf("alice"));
        assertEquals(3, actAsLines().size(), "a different target, and a different token, log again");
        cookieMe(login("alice"), export);
        assertEquals(4, actAsLines().size(), "a new actor token logs again");
    }

    @Test
    void aRejectedActAsNeverLogs() throws Exception
    {
        String alice = login("alice");
        cookieMe(alice, "does-not-exist");
        cookieMe(alice, idOf("outsider"));
        cookieMe(alice, "self");
        assertEquals(List.of(), actAsLines());
    }

    private String login(String username) throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, username, ImpersonationRightsFixture.PASSWORD);
    }

    private String loginHomer() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
    }

    private String idOf(String username) throws Exception
    {
        return operator.getUser(username).getId();
    }

    private static MockHttpServletRequestBuilder cookie(MockHttpServletRequestBuilder request, String token)
    {
        return request.cookie(new Cookie("access_token", token));
    }

    private MvcResult cookieMe(String token, String actAs) throws Exception
    {
        MockHttpServletRequestBuilder request = cookie(get("/api/auth/me"), token);
        if (actAs != null)
        {
            request.header("X-Rapla-Act-As", actAs);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String fingerprint(MvcResult result)
    {
        MockHttpServletResponse response = result.getResponse();
        TreeMap<String, List<String>> headers = new TreeMap<>();
        for (String name : response.getHeaderNames())
        {
            if (!name.equalsIgnoreCase("Set-Cookie"))
            {
                headers.put(name.toLowerCase(), response.getHeaders(name));
            }
        }
        try
        {
            return response.getStatus() + "|" + headers + "|" + response.getContentAsString();
        }
        catch (java.io.UnsupportedEncodingException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void cookieRequestWithoutTheHeaderIs400AndNeverRunsAsTheActor() throws Exception
    {
        MvcResult result = cookieMe(loginHomer(), null);
        assertEquals(400, result.getResponse().getStatus());
        assertEquals("", result.getResponse().getContentAsString(), "no identity in the body");
    }

    @Test
    void selfRunsAsTheActor() throws Exception
    {
        mockMvc.perform(cookie(get("/api/auth/me"), loginHomer()).header("X-Rapla-Act-As", "self"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"))
                .andExpect(jsonPath("$.impersonating").value(false));
    }

    @Test
    void permittedIdRunsAsTheTargetWithTheActorNamed() throws Exception
    {
        mockMvc.perform(cookie(get("/api/auth/me"), login("alice")).header("X-Rapla-Act-As", idOf("export")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("export"))
                .andExpect(jsonPath("$.impersonating").value(true))
                .andExpect(jsonPath("$.actor").value("alice"))
                .andExpect(jsonPath("$.target").value("export"));
    }

    @Test
    void adminActsAsAnyUserInHisScope() throws Exception
    {
        mockMvc.perform(cookie(get("/api/auth/me"), loginHomer()).header("X-Rapla-Act-As", idOf("monty")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("monty"))
                .andExpect(jsonPath("$.actor").value("homer"));
    }

    @Test
    void notPermittedIdAnswersExactlyLikeAnUnknownOne() throws Exception
    {
        String alice = login("alice");
        String unknown = fingerprint(cookieMe(alice, "does-not-exist"));
        assertTrue(unknown.startsWith("403|"), unknown);
        for (String notPermitted : List.of("outsider", "adminfa", "homer", "monty", "alice"))
        {
            assertEquals(unknown, fingerprint(cookieMe(alice, idOf(notPermitted))), notPermitted);
        }
        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        assertEquals(unknown, fingerprint(cookieMe(monty, idOf("homer"))));
    }

    @Test
    void anActAsOnABearerRequestIsRejected() throws Exception
    {
        String alice = login("alice");
        for (String actAs : List.of("self", idOf("export"), "does-not-exist"))
        {
            MvcResult result = mockMvc.perform(get("/api/auth/me")
                    .header("Authorization", "Bearer " + alice)
                    .header("X-Rapla-Act-As", actAs)).andReturn();
            assertEquals(400, result.getResponse().getStatus(), actAs);
        }
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void anApiKeyNeverImpersonates() throws Exception
    {
        String adminAccess = loginHomer();
        MvcResult created = mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType("application/json")
                        .content("{\"label\":\"actas\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String apiKey = JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("key").asString();

        assertEquals(400, mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Rapla-Act-As", idOf("monty"))).andReturn().getResponse().getStatus());
        assertEquals(400, mockMvc.perform(cookie(get("/api/auth/me"), apiKey)
                .header("X-Rapla-Act-As", idOf("monty"))).andReturn().getResponse().getStatus(),
                "an api key smuggled into the cookie does not impersonate either");
    }

    @Test
    void aTokenThatAlreadyCarriesActRejectsAnActAsId() throws Exception
    {
        String bearerImpersonation = JsonMapper.builder().build().readTree(mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + loginHomer())
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=monty"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("access_token").asString();

        assertEquals(400, cookieMe(bearerImpersonation, idOf("alice")).getResponse().getStatus());
        assertEquals(400, mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + bearerImpersonation)
                .header("X-Rapla-Act-As", "self")).andReturn().getResponse().getStatus());
    }

    @Test
    void theActAsQueryParameterIsRejectedOutsideTheDocumentGet() throws Exception
    {
        String homer = loginHomer();
        assertEquals(400, mockMvc.perform(cookie(get("/api/auth/me?actAs=self"), homer).header("X-Rapla-Act-As", "self"))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mockMvc.perform(get("/api/auth/me?actAs=self").header("Authorization", "Bearer " + homer))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mockMvc.perform(cookie(post("/api/documents/preview?actAs=self"), homer)
                        .header("X-Rapla-Act-As", "self").with(csrf())
                        .contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus(), "only GET /api/documents/** takes it");
    }

    @Test
    void aDocumentGetWithoutActAsRunsAsTheActorAndWithAPermittedOneAsTheTarget() throws Exception
    {
        String homer = loginHomer();
        int asActor = mockMvc.perform(cookie(get("/api/documents/nope/source"), homer)).andReturn().getResponse().getStatus();
        assertEquals(404, asActor, "an admin actor reaches the (missing) document");
        assertEquals(404, mockMvc.perform(cookie(get("/api/documents/nope/source?actAs=self"), homer)).andReturn().getResponse().getStatus());

        int asTarget = mockMvc.perform(cookie(get("/api/documents/nope/source?actAs=" + idOf("alice")), homer))
                .andReturn().getResponse().getStatus();
        assertNotEquals(404, asTarget, "alice is no author, so the request ran as alice");
    }

    @Test
    void aDocumentGetWithANotPermittedActAsAnswersLikeAnUnknownOne() throws Exception
    {
        String alice = login("alice");
        String unknown = fingerprint(mockMvc.perform(cookie(get("/api/documents/nope/source?actAs=does-not-exist"), alice)).andReturn());
        assertTrue(unknown.startsWith("403|"), unknown);
        assertEquals(unknown, fingerprint(mockMvc.perform(cookie(get("/api/documents/nope/source?actAs=" + idOf("outsider")), alice)).andReturn()));
    }

    @Test
    void aDocumentGetWithBothTheHeaderAndTheParameterIsAmbiguousAndRejected() throws Exception
    {
        assertEquals(400, mockMvc.perform(cookie(get("/api/documents/nope/source?actAs=self"), loginHomer())
                .header("X-Rapla-Act-As", "self")).andReturn().getResponse().getStatus());
    }

    @Test
    void aDocumentXhrWithTheHeaderIsHonouredToo() throws Exception
    {
        int asTarget = mockMvc.perform(cookie(get("/api/documents/nope/source"), loginHomer())
                .header("X-Rapla-Act-As", idOf("alice"))).andReturn().getResponse().getStatus();
        assertNotEquals(404, asTarget);
    }

    @Test
    void theAnonymousRequestIsNotTheFiltersBusiness() throws Exception
    {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicSpecNavigationsKeepWorkingWithACookie() throws Exception
    {
        String homer = loginHomer();
        assertEquals(200, mockMvc.perform(cookie(get("/api/graphql/schema"), homer)).andReturn().getResponse().getStatus());
    }
}
