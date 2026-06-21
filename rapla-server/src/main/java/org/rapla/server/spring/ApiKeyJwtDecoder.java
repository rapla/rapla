package org.rapla.server.spring;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.ApiKeyScopes;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.spring.web.ApiKeyController;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link JwtDecoder} dispatching on the {@code typ} claim (PRD 043).
 *
 * <p>For {@code typ=api_key} JWTs the verification path is:
 * <ol>
 *   <li>Resolve the user from {@code sub}.</li>
 *   <li>Find the stored entry whose {@code kid} matches the JWT's
 *       header {@code kid} by iterating
 *       {@link RaplaKeyStorage#getAPIKeys(User)} and parsing each JSON
 *       blob. Absent ⇒ never-registered or revoked, both rejected.</li>
 *   <li>Verify the signature against the <b>stored</b> public JWK.
 *       The JWT's own header is not trusted for key material.</li>
 *   <li>Check {@code exp} if present.</li>
 * </ol>
 *
 * <p>All other JWTs ({@code typ=access}, {@code typ=refresh}, external
 * IdP tokens) fall through to the wrapped {@link #delegate}.
 */
public class ApiKeyJwtDecoder implements JwtDecoder
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final JwtDecoder delegate;
    private final RaplaKeyStorage keyStore;
    private final RaplaFacade facade;

    public ApiKeyJwtDecoder(JwtDecoder delegate, RaplaKeyStorage keyStore, RaplaFacade facade)
    {
        this.delegate = delegate;
        this.keyStore = keyStore;
        this.facade = facade;
    }

    @Override
    public Jwt decode(String token) throws JwtException
    {
        SignedJWT parsed;
        try
        {
            parsed = SignedJWT.parse(token);
        }
        catch (ParseException e)
        {
            return delegate.decode(token);
        }
        String typ;
        try
        {
            typ = parsed.getJWTClaimsSet().getStringClaim("typ");
        }
        catch (ParseException e)
        {
            return delegate.decode(token);
        }
        if (!ApiKeyController.API_KEY_TYP.equals(typ))
        {
            return delegate.decode(token);
        }
        return decodeApiKey(token, parsed);
    }

    private Jwt decodeApiKey(String token, SignedJWT parsed) throws JwtException
    {
        JWTClaimsSet claims;
        String kid;
        try
        {
            claims = parsed.getJWTClaimsSet();
            kid = parsed.getHeader().getKeyID();
        }
        catch (ParseException e)
        {
            throw new BadJwtException("api key: malformed", e);
        }
        if (kid == null || kid.isEmpty())
        {
            throw new BadJwtException("api key: missing kid");
        }
        String userId = claims.getSubject();
        if (userId == null)
        {
            throw new BadJwtException("api key: missing sub");
        }
        User user;
        try
        {
            user = facade.getOperator().tryResolve(userId, User.class);
        }
        catch (Exception e)
        {
            throw new BadJwtException("api key: user resolution failed", e);
        }
        if (user == null)
        {
            throw new BadJwtException("api key: not registered or revoked");
        }
        JsonNode storedEntry = findStoredEntry(user, kid);
        if (storedEntry == null)
        {
            throw new BadJwtException("api key: not registered or revoked");
        }
        JWK storedJwk;
        try
        {
            storedJwk = JWK.parse(storedEntry.get("jwk").asText());
        }
        catch (Exception e)
        {
            throw new BadJwtException("api key: stored key unparseable", e);
        }
        if (!(storedJwk instanceof RSAKey rsaKey))
        {
            throw new BadJwtException("api key: unsupported key type " + storedJwk.getKeyType());
        }
        try
        {
            JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            if (!parsed.verify(verifier))
            {
                throw new BadJwtException("api key: signature invalid");
            }
        }
        catch (JOSEException e)
        {
            throw new BadJwtException("api key: signature verification failed", e);
        }
        // D9 — effective expiry is the EARLIEST present exp across the signed JWT and the stored
        // entry. The server can only TIGHTEN (the JWT exp is the hard ceiling); a missing stored
        // exp is ignored (a never-expiring legacy key must keep working, never read as expired).
        Date exp = earliest(claims.getExpirationTime(), readStoredExp(storedEntry));
        if (exp != null && exp.toInstant().isBefore(Instant.now()))
        {
            throw new BadJwtException("api key: expired");
        }
        // Scopes are authoritative from the STORED entry, NOT the signed JWT (D8). A missing
        // "scopes" field means a pre-PRD-076 key ⇒ full write power (write_all), never read.
        List<String> scopes = new ArrayList<>(ApiKeyScopes.resolveStored(readStoredScopes(storedEntry)));
        Map<String, Object> headers = new HashMap<>(parsed.getHeader().toJSONObject());
        Map<String, Object> claimsMap = new HashMap<>(claims.toJSONObject());
        claimsMap.put("scopes", scopes);
        Instant iat = claims.getIssueTime() == null ? Instant.now() : claims.getIssueTime().toInstant();
        Instant expInstant = exp == null ? null : exp.toInstant();
        return Jwt.withTokenValue(token)
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claimsMap))
                .issuedAt(iat)
                .expiresAt(expInstant)
                .build();
    }

    /** The full stored JSON entry whose {@code kid} matches, or {@code null} if none/revoked. */
    private JsonNode findStoredEntry(User user, String kid)
    {
        Collection<String> entries;
        try
        {
            entries = keyStore.getAPIKeys(user);
        }
        catch (RaplaException e)
        {
            return null;
        }
        for (String entry : entries)
        {
            try
            {
                JsonNode node = MAPPER.readTree(entry);
                JsonNode kidNode = node.get("kid");
                if (kidNode == null || !kid.equals(kidNode.asText())) continue;
                if (node.get("jwk") == null) continue;
                return node;
            }
            catch (Exception e)
            {
                // Skip malformed / legacy entries (e.g. plain refresh-token strings
                // stored under "refreshToken" clientId by TokenHandler).
            }
        }
        return null;
    }

    private static List<String> readStoredScopes(JsonNode node)
    {
        if (node == null || !node.has("scopes") || !node.get("scopes").isArray())
        {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonNode s : node.get("scopes")) out.add(s.asText());
        return out;
    }

    /** The stored entry's {@code exp} (epoch millis) as a {@link Date}, or {@code null} if absent. */
    private static Date readStoredExp(JsonNode node)
    {
        if (node == null || !node.has("exp") || !node.get("exp").isNumber())
        {
            return null;
        }
        return new Date(node.get("exp").asLong());
    }

    /** The earlier of two expiry instants; {@code null} means "no limit" and is ignored. */
    private static Date earliest(Date a, Date b)
    {
        if (a == null) return b;
        if (b == null) return a;
        return a.before(b) ? a : b;
    }
}
