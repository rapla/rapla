package org.rapla.server.spring.web;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.RefreshSessionService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 051 — tier-3 MockMvc coverage for {@code POST /api/auth/impersonate}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Global admin can impersonate a non-admin user (homer → monty).
 *       Response shape is {@code {access_token, token_type, expires_in}}
 *       with NO {@code refresh_token} field, and the access token has
 *       {@code sub=target.id}, {@code username=target.username},
 *       {@code act.sub=admin.id}, {@code act.username=admin.username}.
 *   <li>401 when no Bearer is presented.
 *   <li>403 when the actor is not authorised to admin the target
 *       (group-admin trying to impersonate the global admin).
 *   <li>404 when {@code target_username} is unknown.
 *   <li>Authorization is re-checked on every call — calling impersonate
 *       a second time with the same target succeeds again (regression
 *       guard for "issuance is stateless").
 *   <li>Negative: no preference is written to the target user as a
 *       side effect of impersonation.
 * </ul>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ImpersonationControllerTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ImpersonationControllerTest.class.getResourceAsStream("/testdefault.xml"))
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
    RaplaFacade raplaFacade;

    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void attachAuditAppender()
    {
        // Audit lines are emitted by the rapla Logger bean (Slf4j-backed,
        // category "rapla"). Logback's root logger receives propagated events,
        // so attaching the ListAppender there captures the audit lines too.
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

    private String loginAs(String username, String password) throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, username, password);
    }

    @Test
    void globalAdminCanImpersonateNonAdmin() throws Exception
    {
        String adminToken = loginAs("homer", "duffs");

        MvcResult mvc = mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=monty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();

        JsonNode body = JsonMapper.builder().build().readTree(mvc.getResponse().getContentAsString());
        String impersonationToken = body.get("access_token").asString();

        JWTClaimsSet claims = JWTParser.parse(impersonationToken).getJWTClaimsSet();
        assertNotNull(claims.getSubject(), "sub claim must be present (target user's UUID)");
        assertEquals("monty", claims.getStringClaim("username"));
        Map<String, Object> act = claims.getJSONObjectClaim("act");
        assertNotNull(act, "act claim must be present");
        assertNotNull(act.get("sub"), "act.sub must name the actor (admin)");
        assertEquals("homer", act.get("username"));
        assertEquals("access", claims.getStringClaim("typ"));
    }

    @Test
    void anonymousReturns401() throws Exception
    {
        mockMvc.perform(post("/api/auth/impersonate")
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=monty"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void groupAdminCannotImpersonateGlobalAdmin() throws Exception
    {
        // monty is a group-admin via powerplant-admins (can_admin_parent=true),
        // but cannot impersonate homer because homer is isAdmin=true.
        String groupAdminToken = loginAs("monty", "burns");

        mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + groupAdminToken)
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=homer"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownTargetReturns404() throws Exception
    {
        String adminToken = loginAs("homer", "duffs");

        mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void renewalIsStatelessSameTargetTwice() throws Exception
    {
        // PRD 051 design: no server-side impersonation session record;
        // every call re-evaluates canAdminUser and mints a fresh token.
        String adminToken = loginAs("homer", "duffs");

        String firstToken = mintFor(adminToken, "monty");
        String secondToken = mintFor(adminToken, "monty");

        assertFalse(firstToken.equals(secondToken),
                "each call must mint a fresh JWT (different jti / iat)");

        JWTClaimsSet first = JWTParser.parse(firstToken).getJWTClaimsSet();
        JWTClaimsSet second = JWTParser.parse(secondToken).getJWTClaimsSet();
        assertEquals(first.getSubject(), second.getSubject(),
                "both tokens identify the same target");
        assertEquals(first.getStringClaim("username"), second.getStringClaim("username"));
        // jti is unique per JWT
        assertFalse(first.getJWTID().equals(second.getJWTID()),
                "each token has a fresh jti");
    }

    private String mintFor(String adminToken, String targetUsername) throws Exception
    {
        MvcResult mvc = mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=" + targetUsername))
                .andExpect(status().isOk())
                .andReturn();
        return JsonMapper.builder().build().readTree(mvc.getResponse().getContentAsString())
                .get("access_token").asString();
    }

    @Test
    void targetReservedSelfImpersonationDoesNotError() throws Exception
    {
        // homer (global admin) impersonating himself is a harmless degenerate
        // case — canAdminUser returns true (isAdmin), so the endpoint mints
        // a token. Exercises the "actor == target" branch without crashing.
        String adminToken = loginAs("homer", "duffs");

        mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=homer"))
                .andExpect(status().isOk());
    }

    @Test
    void impersonationTokenValidatesAsRegularBearer() throws Exception
    {
        // The minted token must be accepted by rapla's resource-server JWT
        // filter as any other access token. Verified by calling an
        // arbitrary authenticated endpoint with the impersonation Bearer.
        String adminToken = loginAs("homer", "duffs");
        String impersonationToken = mintFor(adminToken, "monty");

        // /api/auth/oauth/config is permitAll, so any 200 alone isn't enough
        // to prove auth worked — use /api/auth/api-keys which requires a
        // valid Bearer.
        mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + impersonationToken)
                        .contentType("application/json")
                        .content("{\"label\":\"impersonation-smoke-test\"}"))
                .andExpect(status().isOk());
        // (the key is created against the impersonated user's identity —
        // that's fine; the test asserts only that the token is honoured.)
    }

    @Test
    void auditLogEmittedForEachIssuanceIncludingRenewal() throws Exception
    {
        // PRD 051 § "Audit log": one INFO line per /api/auth/impersonate
        // call, including renewals. Verifies the audit cadence == the
        // renewal cadence (an admin running an hour-long session
        // produces one initial line + one renewal line / hour).
        String adminToken = loginAs("homer", "duffs");
        mintFor(adminToken, "monty");
        mintFor(adminToken, "monty");

        List<String> auditLines = auditAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("Impersonation:"))
                .toList();
        assertEquals(2, auditLines.size(),
                "expected exactly one audit line per impersonate call, got " + auditLines);
        for (String line : auditLines)
        {
            assertTrue(line.contains("actor=homer"), "audit line must name the actor by username: " + line);
            assertTrue(line.contains("target=monty"), "audit line must name the target by username: " + line);
            assertTrue(line.contains("uuid="), "audit line must include UUIDs for forensic correlation: " + line);
        }
    }

    @Test
    void authorizationFailureDoesNotProduceSuccessAuditLine() throws Exception
    {
        // 403 path: monty (group admin) cannot impersonate homer (global
        // admin). The success-path INFO audit line must NOT be emitted.
        // Negative regression: previously a misplaced log statement could
        // record an attempt as if it had succeeded.
        String groupAdminToken = loginAs("monty", "burns");
        mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + groupAdminToken)
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=homer"))
                .andExpect(status().isForbidden());

        boolean anySuccessLine = auditAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.startsWith("Impersonation:"));
        assertFalse(anySuccessLine,
                "no 'Impersonation:' audit line must appear on a 403 path");
    }

    @Test
    void noSessionPreferenceWrittenForTargetAsSideEffect() throws Exception
    {
        // PRD 051 § "What's NOT stored anywhere": impersonation must not
        // touch the target user's org.rapla.auth.session preference. If
        // it did, the target's own refresh-token slot would be clobbered
        // (single-token-per-user model — see RefreshSessionService).
        User target = raplaFacade.getUser("monty");
        Preferences before = raplaFacade.getPreferences(target);
        String sessionBefore = before.getEntryAsString(RefreshSessionService.SESSION, null);

        String adminToken = loginAs("homer", "duffs");
        mintFor(adminToken, "monty");
        mintFor(adminToken, "monty");

        // Re-read post-impersonation. The session slot may legitimately
        // be null (testdefault.xml monty has never logged in via OAuth in
        // this run); either way, it must equal its pre-impersonation
        // value — impersonation cannot have written to it.
        Preferences after = raplaFacade.getPreferences(target);
        String sessionAfter = after.getEntryAsString(RefreshSessionService.SESSION, null);
        if (sessionBefore == null)
        {
            assertNull(sessionAfter,
                    "impersonation must not write a refresh-token slot for the target");
        }
        else
        {
            assertEquals(sessionBefore, sessionAfter,
                    "impersonation must not alter the target's refresh-token slot");
        }
    }
    @Test
    void apiKeyPrincipalCannotImpersonate() throws Exception
    {
        // An api_key JWT (even read-scoped) must not mint an
        // unscoped impersonation token — that would escalate a scoped credential to full access.
        String adminToken = loginAs("homer", "duffs");
        MvcResult created = mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"label\":\"audit\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String apiKey = JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("key").asText();

        mockMvc.perform(post("/api/auth/impersonate")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType("application/x-www-form-urlencoded")
                        .content("target_username=homer"))
                .andExpect(status().isForbidden());
    }

}
