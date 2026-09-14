package org.rapla.server.spring.web;

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
import org.rapla.server.spring.oauth.external.ExternalIdTokenVerifier;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 072 Phase 6 — tier-3 proof of the RFC 8693 token-exchange endpoint
 * ({@code POST /api/auth/oauth/token-exchange/{providerId}}): an external
 * id_token → a rapla token that actually authorizes {@code /api}.
 *
 * <p>A live IdP / JWKS endpoint is not available in a unit test, so the
 * {@link ExternalIdTokenVerifier} bean is replaced (AGENTS.md §13: a hand-rolled
 * {@code @TestConfiguration} stub bean, NOT {@code @MockBean}) with one whose
 * per-provider decoder validates the signature against a throwaway test RSA key
 * — while keeping the PRODUCTION validator stack
 * ({@link ExternalIdTokenVerifier#validatorsFor}, i.e. the real iss + exp + aud
 * pin). So this proves the wiring + the aud-pin + the mint-and-authorize round
 * trip; the only stubbed piece is "where the signing key comes from".
 *
 * <p>Residual gap (documented): the real upstream HTTP code-exchange in
 * {@code OAuthExchangeController.exchange} (the {@code authorization_code} →
 * IdP token-endpoint call) needs a live IdP and is not covered here — that is a
 * tier-7 concern. The verify→provision→mint tail it shares with this endpoint
 * IS covered.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class OAuthTokenExchangeApiTest
{
    private static final String ISSUER = "https://keycloak.example.com/realms/dhbw";
    private static final String RAPLA_CLIENT_ID = "rapla-client";

    @TempDir static Path tempDir;
    static Path dataFile;
    static RSAKey signingKey;

    @BeforeAll
    static void setup() throws Exception
    {
        signingKey = new RSAKeyGenerator(2048).keyID("idp-key").generate();
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = OAuthTokenExchangeApiTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.oauth.external.keycloak.enabled", () -> "true");
        registry.add("rapla.oauth.external.keycloak.base-url", () -> "https://keycloak.example.com");
        registry.add("rapla.oauth.external.keycloak.realm", () -> "dhbw");
        registry.add("rapla.oauth.external.keycloak.client-id", () -> RAPLA_CLIENT_ID);
    }

    @TestConfiguration
    static class TestVerifierConfig
    {
        @Bean
        @Primary
        ExternalIdTokenVerifier testVerifier()
        {
            return new ExternalIdTokenVerifier()
            {
                @Override
                protected JwtDecoder decoderFor(ProviderConfig provider)
                {
                    NimbusJwtDecoder dec;
                    try
                    {
                        dec = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
                    }
                    catch (Exception e)
                    {
                        throw new IllegalStateException(e);
                    }
                    dec.setJwtValidator(ExternalIdTokenVerifier.validatorsFor(provider));
                    return dec;
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void validIdTokenYieldsRaplaTokenThatAuthorizesApi() throws Exception
    {
        String idToken = idToken(ISSUER, List.of(RAPLA_CLIENT_ID), "homer", 3600);

        MvcResult result = mockMvc.perform(post("/api/auth/oauth/token-exchange/keycloak")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andReturn();

        JsonNode body = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        String raplaAccessToken = body.get("access_token").asString();

        // The rapla access token actually authorizes /api as the rapla user.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + raplaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
    }

    @Test
    void wrongAudienceIdTokenIsRejected() throws Exception
    {
        // Signature + issuer valid but minted for a DIFFERENT relying party.
        String idToken = idToken(ISSUER, List.of("some-other-client"), "homer", 3600);
        mockMvc.perform(post("/api/auth/oauth/token-exchange/keycloak")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void expiredIdTokenIsRejected() throws Exception
    {
        String idToken = idToken(ISSUER, List.of(RAPLA_CLIENT_ID), "homer", -120);
        mockMvc.perform(post("/api/auth/oauth/token-exchange/keycloak")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void unknownProviderIsBadRequest() throws Exception
    {
        String idToken = idToken(ISSUER, List.of(RAPLA_CLIENT_ID), "homer", 3600);
        mockMvc.perform(post("/api/auth/oauth/token-exchange/does-not-exist")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    private static String idToken(String issuer, List<String> audience, String username, long expiresInSeconds)
    {
        try
        {
            long now = System.currentTimeMillis();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("external-" + username)
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(new Date(now))
                    .expirationTime(new Date(now + expiresInSeconds * 1000))
                    .claim("preferred_username", username)
                    .claim("name", "Test User")
                    .claim("email", username + "@example.com")
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
            return jwt.serialize();
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }
}
