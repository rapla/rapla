package org.rapla.server.spring.web;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ApiKeyControllerIntegrationTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ApiKeyControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    private String loginAs(String username, String password) throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, username, password);
    }

    private JsonNode createKey(String accessToken, String label, Long expiresInDays) throws Exception
    {
        String body = "{\"label\":\"" + label + "\""
                + (expiresInDays == null ? "" : ",\"expiresInDays\":" + expiresInDays)
                + "}";
        MvcResult created = mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString());
    }

    @Test
    void createReturnsKeyOnceListOmitsKeyMaterial() throws Exception
    {
        String access = loginAs("homer", "duffs");
        JsonNode created = createKey(access, "CI deploy", 30L);

        assertNotNull(created.get("key"), "create response must include the JWT once");
        assertNotNull(created.get("id"));
        assertNotNull(created.get("thumbprint"));
        assertEquals("RS256", created.get("alg").asText());
        assertEquals("CI deploy", created.get("label").asText());
        String thumbprint = created.get("thumbprint").asText();
        assertEquals(created.get("id").asText(), thumbprint,
                "id should equal the JWK thumbprint");

        MvcResult listed = mockMvc.perform(get("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = MAPPER.readTree(listed.getResponse().getContentAsString());
        assertTrue(list.isArray());
        JsonNode mine = findById(list, thumbprint);
        assertNotNull(mine, "newly-created key must appear in listing");
        assertEquals("CI deploy", mine.get("label").asText());
        // listing must NOT include the JWT
        assertTrue(mine.get("key") == null || mine.get("key").isNull(),
                "listing must not expose key material");
    }

    @Test
    void apiKeyJwtWorksAsBearerOnProtectedEndpoint() throws Exception
    {
        String access = loginAs("homer", "duffs");
        String jwt = createKey(access, "spa", 30L).get("key").asText();

        // Use the api-key JWT (NOT the access token) on a protected endpoint.
        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    void revokingRemovesAccess() throws Exception
    {
        String access = loginAs("homer", "duffs");
        JsonNode created = createKey(access, "to-revoke", 30L);
        String jwt = created.get("key").asText();
        String id = created.get("id").asText();

        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/auth/api-keys/" + id)
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredKeyIsRejected() throws Exception
    {
        String access = loginAs("homer", "duffs");
        // expiresInDays=0 → exp = now (effectively past after the round-trip)
        String jwt = createKey(access, "instant-expire", 0L).get("key").asText();

        // Sleep briefly so exp is strictly in the past
        Thread.sleep(50);
        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void neverExpiresWhenExpiresInDaysOmitted() throws Exception
    {
        String access = loginAs("homer", "duffs");
        JsonNode created = createKey(access, "forever", null);
        assertTrue(created.get("expiresAt") == null || created.get("expiresAt").isNull(),
                "expiresAt must be null for never-expiring keys");
        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + created.get("key").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void multipleKeysCoexistAndIndependentRevocation() throws Exception
    {
        String access = loginAs("homer", "duffs");
        JsonNode c1 = createKey(access, "multi-k1", 30L);
        JsonNode c2 = createKey(access, "multi-k2", 30L);
        JsonNode c3 = createKey(access, "multi-k3", 30L);
        String k1 = c1.get("key").asText();
        String k2 = c2.get("key").asText();
        String k3 = c3.get("key").asText();
        String id2 = c2.get("id").asText();

        MvcResult listed = mockMvc.perform(get("/api/auth/api-keys").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = MAPPER.readTree(listed.getResponse().getContentAsString());
        // All three of OUR keys must be present (other tests in this class may
        // have left more behind — we only assert ours are visible).
        assertNotNull(findById(list, c1.get("id").asText()));
        assertNotNull(findById(list, c2.get("id").asText()));
        assertNotNull(findById(list, c3.get("id").asText()));

        // Revoke the middle one
        mockMvc.perform(delete("/api/auth/api-keys/" + id2)
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + k1))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + k2))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + k3))
                .andExpect(status().isOk());
    }

    @Test
    void forgedJwtWithUnregisteredKeyIsRejected() throws Exception
    {
        // Attacker mints their own keypair + signs a typ=api_key JWT for "homer".
        // The kid is from their JWK, so it cannot be in homer's registered set.
        RSAKey attacker = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        long now = System.currentTimeMillis();
        SignedJWT forged = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(attacker.getKeyID())
                        .jwk(attacker.toPublicJWK())
                        .build(),
                new JWTClaimsSet.Builder()
                        .subject(homerId())
                        .issueTime(new Date(now))
                        .expirationTime(new Date(now + 86_400_000L))
                        .claim("typ", "api_key")
                        .jwtID(UUID.randomUUID().toString())
                        .build());
        forged.sign(new RSASSASigner(attacker.toRSAPrivateKey()));

        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + forged.serialize()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void twoUsersIndependentKeys() throws Exception
    {
        // Each user mints a key under their own session. Both JWTs should
        // work as Bearer for the OWNING user's identity.
        String homerAccess = loginAs("homer", "duffs");
        String homerJwt = createKey(homerAccess, "homer-key", 30L).get("key").asText();

        String montyAccess = loginAs("monty", "burns");
        String montyJwt = createKey(montyAccess, "monty-key", 30L).get("key").asText();

        assertNotEquals(homerJwt, montyJwt);

        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + homerJwt))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + montyJwt))
                .andExpect(status().isOk());
    }

    /** Returns the entry whose {@code id} matches, or null. */
    private static JsonNode findById(JsonNode list, String id)
    {
        for (JsonNode node : list)
        {
            if (id.equals(node.get("id").asText())) return node;
        }
        return null;
    }

    /** Resolves the user id of {@code homer} from the data file via login. */
    private String homerId() throws Exception
    {
        // Decode the access token claims (no verification needed here) to get
        // the sub claim, which is the user id used as the JWT principal.
        String access = loginAs("homer", "duffs");
        String[] parts = access.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        return MAPPER.readTree(payload).get("sub").asText();
    }
}
