package org.rapla.server.spring.web;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.spring.DatasourceConfiguredCondition;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Server-mints-and-discards asymmetric API keys (PRD 043).
 *
 * <p>POST generates a fresh RSA-2048 keypair, signs one
 * {@code typ=api_key} JWT with the private half, stores the
 * <b>public JWK + metadata</b> via {@link RaplaKeyStorage#storeAPIKey}
 * (keyed by the RFC 7638 thumbprint), and returns the JWT once. The
 * private key reference goes out of scope at end-of-method and is
 * unreachable thereafter — neither the user nor the server can mint
 * another JWT against the same keypair.
 *
 * <p>The server stores ONLY the public key (plus label/iat/exp for
 * listing UX). The JWT is the client's bearer credential and is never
 * persisted server-side, so a data-file leak yields only public keys
 * — useless for impersonation.
 *
 * <p>GET parses the stored JSON entries to expose listing metadata.
 * DELETE removes by thumbprint (the storage {@code clientId}).
 */
@RestController
@Conditional(DatasourceConfiguredCondition.class)
@RequestMapping(value = "/api/auth/api-keys", produces = "application/json")
public class ApiKeyController
{
    public static final String API_KEY_TYP = "api_key";
    private static final int RSA_KEY_SIZE = 2048;
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final RaplaKeyStorage keyStore;
    private final RaplaFacade facade;

    public ApiKeyController(RaplaKeyStorage keyStore, RaplaFacade facade)
    {
        this.keyStore = keyStore;
        this.facade = facade;
    }

    @PostMapping
    public CreateResponse create(@AuthenticationPrincipal Jwt principal,
                                 @RequestBody CreateRequest req)
            throws RaplaException, JOSEException
    {
        User user = resolveUser(principal);
        long now = System.currentTimeMillis();
        Long expiresAtMillis = (req.expiresInDays == null)
                ? null
                : now + req.expiresInDays * 86_400_000L;

        // Generate keypair; kid := RFC 7638 thumbprint of the public JWK.
        RSAKey keypair = new RSAKeyGenerator(RSA_KEY_SIZE)
                .keyIDFromThumbprint(true)
                .generate();
        String thumbprint = keypair.getKeyID();

        // Sign one JWT. Header carries only kid — NOT the public JWK; the
        // server's stored copy is the trust anchor, not anything embedded
        // in the JWT itself.
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(user.getId())
                .issueTime(new Date(now))
                .claim("typ", API_KEY_TYP);
        if (req.label != null && !req.label.isBlank())
        {
            claims.claim("name", req.label);
        }
        if (expiresAtMillis != null)
        {
            claims.expirationTime(new Date(expiresAtMillis));
        }
        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(thumbprint)
                        .build(),
                claims.build());
        signed.sign(new RSASSASigner(keypair.toRSAPrivateKey()));
        String jwt = signed.serialize();

        // Persist public-key-only metadata. The full JWT is NEVER stored.
        String storedEntry = serialiseEntry(
                keypair.toPublicJWK(), req.label, now, expiresAtMillis);
        keyStore.storeAPIKey(user, thumbprint, storedEntry);
        // keypair (and the private key) goes out of scope here.

        return new CreateResponse(
                thumbprint,
                req.label,
                JWSAlgorithm.RS256.getName(),
                thumbprint,
                Instant.ofEpochMilli(now).toString(),
                expiresAtMillis == null ? null : Instant.ofEpochMilli(expiresAtMillis).toString(),
                jwt);
    }

    @GetMapping
    public List<KeyMetadata> list(@AuthenticationPrincipal Jwt principal) throws RaplaException
    {
        User user = resolveUser(principal);
        Collection<String> stored = keyStore.getAPIKeys(user);
        List<KeyMetadata> out = new ArrayList<>(stored.size());
        for (String entry : stored)
        {
            KeyMetadata meta = parseMetadata(entry);
            if (meta != null)
            {
                out.add(meta);
            }
        }
        return out;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal,
                                       @PathVariable("id") String id) throws RaplaException
    {
        User user = resolveUser(principal);
        keyStore.removeAPIKey(user, id);
        return ResponseEntity.noContent().build();
    }

    private User resolveUser(Jwt principal) throws RaplaException
    {
        if (principal == null || principal.getSubject() == null)
        {
            throw new RaplaSecurityException("not authenticated");
        }
        User user = facade.getOperator().tryResolve(principal.getSubject(), User.class);
        if (user == null)
        {
            throw new RaplaSecurityException("user not found");
        }
        return user;
    }

    private static String serialiseEntry(JWK publicJwk, String label,
                                         long createdAt, Long expiresAt)
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("kid", publicJwk.getKeyID());
        node.put("jwk", publicJwk.toJSONString());
        node.put("alg", JWSAlgorithm.RS256.getName());
        if (label != null) node.put("label", label);
        node.put("iat", createdAt);
        if (expiresAt != null) node.put("exp", expiresAt);
        return node.toString();
    }

    private static KeyMetadata parseMetadata(String entry)
    {
        try
        {
            var node = MAPPER.readTree(entry);
            String kid = node.get("kid").asText();
            String label = node.has("label") ? node.get("label").asText() : null;
            String alg = node.has("alg") ? node.get("alg").asText() : JWSAlgorithm.RS256.getName();
            long iat = node.get("iat").asLong();
            Long exp = node.has("exp") ? node.get("exp").asLong() : null;
            return new KeyMetadata(
                    kid,
                    label,
                    alg,
                    kid,
                    Instant.ofEpochMilli(iat).toString(),
                    exp == null ? null : Instant.ofEpochMilli(exp).toString());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static class CreateRequest
    {
        public String label;
        public Long expiresInDays;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Long getExpiresInDays() { return expiresInDays; }
        public void setExpiresInDays(Long expiresInDays) { this.expiresInDays = expiresInDays; }
    }

    public record CreateResponse(
            String id,
            String label,
            String alg,
            String thumbprint,
            String createdAt,
            String expiresAt,
            String key)
    {
    }

    public record KeyMetadata(
            String id,
            String label,
            String alg,
            String thumbprint,
            String createdAt,
            String expiresAt)
    {
    }
}
