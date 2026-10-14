package org.rapla.server.spring.web;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 131 D8–D11 — tier-3 MockMvc coverage of the impersonation right of a non-admin: the Bearer endpoint
 * ({@code POST /api/auth/impersonate}), the cookie endpoint ({@code POST /api/auth/impersonate/switch}) and the
 * candidate list ({@code GET /api/users}). Fixture: {@link ImpersonationRightsFixture} (alice may switch to export only).
 * AGENTS.md §12: a target the actor may not switch to answers exactly like an unknown one.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ImpersonationRightsControllerTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ImpersonationRightsControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void seedAndAttachAuditAppender() throws Exception
    {
        ImpersonationRightsFixture.seed(facade, operator);
        auditAppender = new ListAppender<>();
        auditAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("rapla")).addAppender(auditAppender);
    }

    @AfterEach
    void detachAuditAppender()
    {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("rapla")).detachAppender(auditAppender);
        auditAppender.stop();
    }

    private String login(String username) throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, username, ImpersonationRightsFixture.PASSWORD);
    }

    private String loginMonty() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
    }

    private String loginHomer() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
    }

    private MvcResult bearerImpersonate(String token, String target) throws Exception
    {
        return mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=" + target))
                .andReturn();
    }

    private MvcResult cookieSwitch(String accessCookie, String target) throws Exception
    {
        return cookieSwitch(accessCookie, target, "self");
    }

    private MvcResult cookieSwitch(String accessCookie, String target, String actAs) throws Exception
    {
        return mockMvc.perform(post("/api/auth/impersonate/switch")
                        .with(csrf())
                        .cookie(new Cookie("access_token", accessCookie))
                        .header("X-Rapla-Act-As", actAs)
                        .param("target_username", target))
                .andReturn();
    }

    private String tokenOf(MvcResult result) throws Exception
    {
        return JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("access_token").asString();
    }

    private static String fingerprint(MvcResult result) throws Exception
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
        return response.getStatus() + "|" + headers + "|" + response.getCookies().length + "|" + response.getContentAsString();
    }

    private List<String> usernames(MvcResult result) throws Exception
    {
        JsonNode tree = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        List<String> out = new ArrayList<>();
        for (JsonNode entry : tree)
        {
            out.add(entry.get("username").asString());
        }
        return out;
    }

    // === Bearer endpoint ======================================================

    @Test
    void memberWithTheAnnotationRightMaySwitchToTheFunctionalAccount() throws Exception
    {
        MvcResult result = bearerImpersonate(login("alice"), "export");
        assertEquals(200, result.getResponse().getStatus());

        JWTClaimsSet claims = JWTParser.parse(tokenOf(result)).getJWTClaimsSet();
        assertEquals("export", claims.getStringClaim("username"));
        assertEquals("alice", claims.getJSONObjectClaim("act").get("username"));
        assertTrue(auditAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
                        .anyMatch(m -> m.startsWith("Impersonation:") && m.contains("actor=alice") && m.contains("target=export")),
                "the audit line names actor and target");
    }

    @Test
    void notPermittedTargetAnswersExactlyLikeAnUnknownOne() throws Exception
    {
        String alice = login("alice");
        String unknown = fingerprint(bearerImpersonate(alice, "does-not-exist"));
        assertTrue(unknown.startsWith("403|"), unknown);
        for (String notPermitted : List.of("outsider", "adminfa", "homer", "monty", "alice"))
        {
            assertEquals(unknown, fingerprint(bearerImpersonate(alice, notPermitted)), notPermitted);
        }

        String monty = loginMonty();
        String montyUnknown = fingerprint(bearerImpersonate(monty, "does-not-exist"));
        assertEquals(montyUnknown, fingerprint(bearerImpersonate(monty, "homer")));
        assertEquals(unknown, montyUnknown);
    }

    @Test
    void adminTargetIsNotReachableViaTheAnnotation() throws Exception
    {
        assertEquals(403, bearerImpersonate(login("alice"), "adminfa").getResponse().getStatus());
    }

    @Test
    void anImpersonationTokenCannotStartAnotherImpersonation() throws Exception
    {
        String montyAsHomer = tokenOf(bearerImpersonate(loginHomer(), "monty"));
        assertEquals(200, bearerImpersonate(loginMonty(), "staffer").getResponse().getStatus(),
                "precondition: monty may switch to staffer with his own token");
        assertEquals(403, bearerImpersonate(montyAsHomer, "staffer").getResponse().getStatus(),
                "the act token must be refused although its effective user could switch");

        String exportAsAlice = tokenOf(bearerImpersonate(login("alice"), "export"));
        assertEquals(403, bearerImpersonate(exportAsAlice, "export").getResponse().getStatus());
    }

    // === cookie endpoint ======================================================

    @Test
    void cookieSwitchHonoursTheAnnotationRightAndAuditsOnceNotPerRequest() throws Exception
    {
        String alice = login("alice");
        MvcResult switched = cookieSwitch(alice, "export");
        assertEquals(200, switched.getResponse().getStatus());
        assertNull(switched.getResponse().getCookie("access_token"), "no impersonation cookie any more");
        String exportId = operator.getUser("export").getId();
        assertEquals(exportId, JsonMapper.builder().build().readTree(switched.getResponse().getContentAsString()).get("id").asString());

        for (int i = 0; i < 3; i++)
        {
            mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", alice)).header("X-Rapla-Act-As", exportId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("export"))
                    .andExpect(jsonPath("$.actor").value("alice"));
        }
        assertEquals(1, auditAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
                        .filter(m -> m.startsWith("Impersonation:") && m.contains("actor=alice") && m.contains("target=export")).count(),
                "the audit line is written at the switch call only");
    }

    @Test
    void cookieNotPermittedTargetAnswersExactlyLikeAnUnknownOne() throws Exception
    {
        String alice = login("alice");
        String unknown = fingerprint(cookieSwitch(alice, "does-not-exist"));
        assertTrue(unknown.startsWith("403|"), unknown);
        for (String notPermitted : List.of("outsider", "adminfa", "homer", "monty", "alice"))
        {
            assertEquals(unknown, fingerprint(cookieSwitch(alice, notPermitted)), notPermitted);
        }
        assertEquals(unknown, fingerprint(cookieSwitch(loginMonty(), "homer")));
    }

    @Test
    void switchWhileImpersonatingIsCheckedAgainstTheRealActor() throws Exception
    {
        String alice = login("alice");
        String exportId = operator.getUser("export").getId();

        assertEquals(200, cookieSwitch(alice, "export", exportId).getResponse().getStatus(),
                "alice -> export -> export is checked as alice -> export");
        assertEquals(403, cookieSwitch(alice, "outsider", exportId).getResponse().getStatus(),
                "alice -> export -> outsider is checked as alice -> outsider");
    }

    // === candidate list =======================================================

    @Test
    void candidateListOfAnImpersonatorHoldsExactlyThePermittedTargets() throws Exception
    {
        MvcResult own = mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + login("alice"))).andReturn();
        assertEquals(200, own.getResponse().getStatus());
        assertEquals(List.of("export"), usernames(own), "no admin, no peer, no self, no unrelated user");
    }

    @Test
    void candidateListWhileImpersonatingIsComputedForTheRealActor() throws Exception
    {
        MvcResult list = mockMvc.perform(get("/api/users").cookie(new Cookie("access_token", login("alice")))
                .header("X-Rapla-Act-As", operator.getUser("export").getId())).andReturn();
        assertEquals(List.of("export"), usernames(list));
    }

    @Test
    void candidateListOfAUserWithoutAnyRightStaysEmpty() throws Exception
    {
        MvcResult list = mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + login("outsider"))).andReturn();
        assertEquals(200, list.getResponse().getStatus());
        assertEquals("[]", list.getResponse().getContentAsString());
        assertFalse(usernames(mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + login("export"))).andReturn()).contains("alice"));
    }

    @Test
    void candidateListOfAnAdminIsUnchanged() throws Exception
    {
        List<String> names = usernames(mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + loginHomer())).andReturn());
        assertTrue(names.containsAll(List.of("homer", "monty", "alice", "export", "adminfa", "outsider", "staffer")), names.toString());
    }
}
