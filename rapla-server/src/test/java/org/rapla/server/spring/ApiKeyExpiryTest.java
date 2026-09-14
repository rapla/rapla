package org.rapla.server.spring;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.internal.RaplaKeyStorageImpl;
import org.rapla.server.spring.web.ApiKeyController;
import org.rapla.test.util.FacadeTestSupport;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PRD 076 Phase 3 / D9 — the effective expiry is the EARLIEST present {@code exp}: the decoder
 * honours the STORED entry's {@code exp} in addition to the signed JWT's, so the server can
 * tighten (shorten) a key's lifetime — but never extend it. A MISSING stored {@code exp} is
 * ignored (the load-bearing backward-compat rule: a pre-PRD never-expiring key must keep working,
 * never be read as already-expired).
 */
class ApiKeyExpiryTest extends FacadeTestSupport
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private RaplaKeyStorage keyStore;
    private User homer;
    private final JwtDecoder throwingDelegate = token ->
    {
        throw new BadJwtException("delegate must not be reached for api_key tokens");
    };

    @BeforeEach
    void setUpKeyStore() throws Exception
    {
        keyStore = new RaplaKeyStorageImpl(facade);
        homer = facade.getUser("homer");
        assertNotNull(homer);
    }

    /** Registers a key: stores the public JWK entry (with the given scopes/exp) and returns a
     *  signed JWT carrying its own {@code jwtExpMillis} (null = no JWT exp). */
    private String registerKey(Long storedExpMillis, Long jwtExpMillis) throws Exception
    {
        RSAKey keypair = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        String kid = keypair.getKeyID();
        long now = System.currentTimeMillis();

        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("kid", kid);
        entry.put("jwk", keypair.toPublicJWK().toJSONString());
        entry.put("alg", "RS256");
        entry.put("iat", now);
        if (storedExpMillis != null) entry.put("exp", storedExpMillis);
        entry.putArray("scopes").add("read");
        keyStore.storeAPIKey(homer, kid, entry.toString());

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(homer.getId())
                .issueTime(new Date(now))
                .claim("typ", ApiKeyController.API_KEY_TYP);
        if (jwtExpMillis != null) claims.expirationTime(new Date(jwtExpMillis));
        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(kid).build(),
                claims.build());
        signed.sign(new RSASSASigner(keypair.toRSAPrivateKey()));
        return signed.serialize();
    }

    @Test
    void storedExpInThePastRejectsEvenWhenJwtExpIsFuture() throws Exception
    {
        long past = System.currentTimeMillis() - 60_000L;
        long future = System.currentTimeMillis() + 3_600_000L;
        String jwt = registerKey(past, future);
        ApiKeyJwtDecoder decoder = new ApiKeyJwtDecoder(throwingDelegate, keyStore, facade);
        assertThrows(BadJwtException.class, () -> decoder.decode(jwt),
                "server-shortened stored exp (past) must reject despite a future JWT exp");
    }

    @Test
    void missingStoredExpIsIgnoredNeverExpiringKeyStillWorks() throws Exception
    {
        // D8/D9 backward-compat: legacy entry has no exp; JWT has no exp → never expires.
        String jwt = registerKey(null, null);
        ApiKeyJwtDecoder decoder = new ApiKeyJwtDecoder(throwingDelegate, keyStore, facade);
        Jwt decoded = decoder.decode(jwt);
        assertEquals(homer.getId(), decoded.getSubject());
    }

    @Test
    void bothExpInFutureStillValid() throws Exception
    {
        long future = System.currentTimeMillis() + 3_600_000L;
        long furtherFuture = future + 3_600_000L;
        String jwt = registerKey(future, furtherFuture);
        ApiKeyJwtDecoder decoder = new ApiKeyJwtDecoder(throwingDelegate, keyStore, facade);
        Jwt decoded = decoder.decode(jwt);
        assertEquals(homer.getId(), decoded.getSubject());
    }
}
