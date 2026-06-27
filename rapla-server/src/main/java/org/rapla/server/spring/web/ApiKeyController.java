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
import org.rapla.server.ApiKeyScopeContext;
import org.rapla.server.ApiKeyScopes;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.spring.DatasourceConfiguredCondition;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import java.util.Set;

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
    // PRD 076 Phase 6 / D7 — rotation grace window (minutes) + successor lifetime.
    private static final long DEFAULT_GRACE_MINUTES = 180;
    private static final long MAX_GRACE_MINUTES = 2880; // 2 days — server cap
    private static final int SUCCESSOR_TTL_DAYS = 180;
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
        // D10 — the generic create endpoint is for interactive sessions only. An api-key
        // principal must NOT mint keys here (it could escalate to write_all); a rotate_self key
        // gets a same-scope successor via /{id}/rotate instead.
        if (isApiKeyPrincipal(principal))
        {
            throw new RaplaSecurityException("api keys cannot mint keys; use /{id}/rotate");
        }
        User user = resolveUser(principal);
        // Validate + default the scope set: null/empty ⇒ least-privilege {read} (D5);
        // unknown token ⇒ IllegalArgumentException ⇒ HTTP 400.
        Set<String> scopes = ApiKeyScopes.normaliseForNewKey(req.scopes);
        long now = System.currentTimeMillis();
        Long expiresAtMillis = (req.expiresInDays == null)
                ? null
                : now + req.expiresInDays * 86_400_000L;
        CreateResponse created = mintAndStore(user, req.label, scopes, expiresAtMillis, null);
        compactExpired(user); // D11 — opportunistic prune of any expired entries on write
        return created;
    }

    /**
     * Self-rotation (PRD 076 D7/D10): a {@code rotate_self} key replaces itself with a
     * same-scope successor (D3 no escalation) and gets a short server-side grace TTL on the old
     * key (D9 — the decoder enforces {@code min(jwt.exp, stored.exp)}, so shortening the stored
     * {@code exp} expires the old key after the window). Endpoint-bound: only the api-key that
     * owns {@code id} may call it, only with the {@code rotate_self} scope, and it can rotate
     * ITSELF only — never another key, never the generic create endpoint.
     */
    @PostMapping("/{id}/rotate")
    public CreateResponse rotate(@AuthenticationPrincipal Jwt principal,
                                 @PathVariable("id") String id,
                                 @RequestParam(value = "graceMinutes", required = false) Long graceMinutes)
            throws RaplaException, JOSEException
    {
        // PRD 076 D12 — one endpoint, two principal types. Resolve the owner first; the key must
        // live in THEIR keystore (findEntryByKid only searches the caller's own keys), so a foreign
        // or nonexistent id is indistinguishable — no existence leak (§12).
        User user = resolveUser(principal);
        var oldEntry = findEntryByKid(user, id);
        if (oldEntry == null)
        {
            throw new RaplaSecurityException("key not found");
        }
        // D15 (Model B) — rotation requires the TARGET key to hold rotate_self, uniformly for human
        // AND machine callers. A read-only key is NOT rotatable (delete + create a fresh one). The
        // scope is read from the authoritative STORED entry (D8), not the JWT claim. Combined with
        // D13 (the old key sheds rotate_self on rotation), this means a grace predecessor is no
        // longer rotatable — "only the latest token in a chain can rotate" holds via the UI too.
        if (!ApiKeyScopes.resolveStored(readScopes(oldEntry)).contains(ApiKeyScopes.ROTATE_SELF))
        {
            throw new RaplaSecurityException("key is not rotatable — it has no rotate_self scope");
        }
        if (isApiKeyPrincipal(principal))
        {
            // Machine self-rotation: an api-key may rotate ONLY itself (its kid must equal {id});
            // rotate_self on the target — i.e. itself — was just verified above (D3/D10 no escalation).
            Object callerKid = principal.getHeaders().get("kid");
            if (callerKid == null || !callerKid.equals(id))
            {
                throw new RaplaSecurityException("a key may only rotate itself");
            }
        }

        long minutes = graceMinutes == null ? DEFAULT_GRACE_MINUTES : Math.max(0L, graceMinutes);
        if (minutes > MAX_GRACE_MINUTES)
        {
            // D7 cap — a "grace" window must not become an unbounded second lifetime. → HTTP 400.
            throw new IllegalArgumentException("graceMinutes may not exceed " + MAX_GRACE_MINUTES + " (2 days)");
        }
        // D14 sprawl guardrail (NOT a compromise control): at most 2 live tokens per rotation chain —
        // the active key + its one grace predecessor. If the key being rotated still has a STILL-VALID
        // predecessor (a grace token from a previous rotation that hasn't expired or been deleted),
        // refuse — otherwise rapid re-rotation with a small grace could pile up unbounded live tokens.
        // Routine slow rotation is unaffected: by the next cycle the predecessor has long expired (and
        // been compacted), so it no longer blocks.
        if (oldEntry.has("prev"))
        {
            var prevNode = findEntryByKid(user, oldEntry.get("prev").asText());
            if (prevNode != null && !isExpired(prevNode.toString()))
            {
                throw new PreviousKeyStillActiveException();
            }
        }
        // Successor inherits the predecessor's scope set EXACTLY — including rotate_self, so the NEW
        // key can itself be rotated again later (D3 no-escalation; D13). Fresh default lifetime
        // (D7 update — was never-expiring).
        Set<String> successorScopes = ApiKeyScopes.resolveStored(readScopes(oldEntry));
        String label = oldEntry.has("label") ? oldEntry.get("label").asText() : null;
        long now = System.currentTimeMillis();
        long oldExp = now + minutes * 60_000L;
        long successorExp = now + (long) SUCCESSOR_TTL_DAYS * 86_400_000L;
        CreateResponse successor;
        try
        {
            // Key storage lives in the user's Preferences; persisting the successor + shortening
            // the old entry are privileged management writes the scope guard must NOT block (a
            // read/rotate_self key has no write_all). Suspend enforcement for just these writes.
            successor = ApiKeyScopeContext.callUnrestricted(() ->
            {
                CreateResponse s = mintAndStore(user, label, successorScopes, successorExp, id);
                shortenStoredExp(user, id, oldExp);
                return s;
            });
        }
        catch (RaplaException | JOSEException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RaplaException("rotation failed: " + e.getMessage(), e);
        }
        compactExpired(user); // D11 — prune now-dead entries (not the just-grace-shortened old key)
        return successor;
    }

    /** Mints a fresh keypair, signs one api-key JWT, stores the public-key-only entry, returns once. */
    private CreateResponse mintAndStore(User user, String label, Set<String> scopes, Long expiresAtMillis,
                                        String prevKid)
            throws JOSEException, RaplaException
    {
        long now = System.currentTimeMillis();
        // Generate keypair; kid := RFC 7638 thumbprint of the public JWK.
        RSAKey keypair = new RSAKeyGenerator(RSA_KEY_SIZE)
                .keyIDFromThumbprint(true)
                .generate();
        String thumbprint = keypair.getKeyID();

        // Sign one JWT. Header carries only kid — NOT the public JWK; the server's stored copy is
        // the trust anchor, not anything embedded in the JWT itself.
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(user.getId())
                .issueTime(new Date(now))
                .claim("typ", API_KEY_TYP);
        if (label != null && !label.isBlank())
        {
            claims.claim("name", label);
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

        // Persist public-key-only metadata. The full JWT is NEVER stored. Scopes live HERE
        // (the stored entry), not in the signed JWT — server-side authoritative + migratable (D8).
        String storedEntry = serialiseEntry(keypair.toPublicJWK(), label, now, expiresAtMillis, scopes, prevKid);
        keyStore.storeAPIKey(user, thumbprint, storedEntry);
        // keypair (and the private key) goes out of scope here.

        return new CreateResponse(
                thumbprint,
                label,
                JWSAlgorithm.RS256.getName(),
                thumbprint,
                Instant.ofEpochMilli(now).toString(),
                expiresAtMillis == null ? null : Instant.ofEpochMilli(expiresAtMillis).toString(),
                new ArrayList<>(scopes),
                jwt);
    }

    private static boolean isApiKeyPrincipal(Jwt principal)
    {
        return principal != null && API_KEY_TYP.equals(principal.getClaimAsString("typ"));
    }

    /** The stored entry JSON node whose {@code kid} matches, or {@code null}. */
    private tools.jackson.databind.JsonNode findEntryByKid(User user, String kid) throws RaplaException
    {
        for (String entry : keyStore.getAPIKeys(user))
        {
            try
            {
                var node = MAPPER.readTree(entry);
                var kidNode = node.get("kid");
                if (kidNode != null && kid.equals(kidNode.asText())) return node;
            }
            catch (Exception ignored)
            {
                // skip malformed / legacy entries
            }
        }
        return null;
    }

    /**
     * Grace-degrades the rotated-away OLD key: shortens its {@code exp} to the grace deadline
     * (D7/D9) AND strips {@code rotate_self} from its stored scopes (D13). Scope lives in the stored
     * entry (D8), so the decoder reads the reduced set on the old key's next call — within the grace
     * window the old key still authenticates, but it can no longer self-rotate (a possibly-compromised
     * key can't mint its own successor and re-establish itself). The NEW key keeps rotate_self.
     */
    private void shortenStoredExp(User user, String kid, long expMillis) throws RaplaException
    {
        var node = findEntryByKid(user, kid);
        if (node == null) return;
        ObjectNode updated = (ObjectNode) node;
        updated.put("exp", expMillis);
        if (updated.has("scopes") && updated.get("scopes").isArray())
        {
            var reduced = MAPPER.createArrayNode();
            for (var s : updated.get("scopes"))
            {
                if (!ApiKeyScopes.ROTATE_SELF.equals(s.asText()))
                {
                    reduced.add(s.asText());
                }
            }
            updated.set("scopes", reduced);
        }
        keyStore.storeAPIKey(user, kid, updated.toString());
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
            // D11 — expired keys are dead (decoder rejects them); never surface them in the UI.
            if (meta != null && !isExpired(entry))
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
        compactExpired(user); // D11 — opportunistic prune of any expired entries on write
        return ResponseEntity.noContent().build();
    }

    /** True if the stored entry carries an {@code exp} already in the past (D11). */
    private static boolean isExpired(String entry)
    {
        try
        {
            var node = MAPPER.readTree(entry);
            return node.has("exp") && node.get("exp").isNumber()
                    && node.get("exp").asLong() < System.currentTimeMillis();
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * D11 — opportunistic compaction: drop stored entries whose {@code exp} is already past. Called
     * on every keystore write (create / rotate / revoke), never on the read/verify path (§16 — the
     * decoder stays side-effect-free). The removes are privileged management writes, so they run
     * unrestricted (a low-privilege api-key doing a self-delete has no {@code write_all}).
     */
    private void compactExpired(User user) throws RaplaException
    {
        try
        {
            ApiKeyScopeContext.callUnrestricted(() ->
            {
                long now = System.currentTimeMillis();
                for (String entry : new ArrayList<>(keyStore.getAPIKeys(user)))
                {
                    try
                    {
                        var node = MAPPER.readTree(entry);
                        if (node.has("exp") && node.get("exp").isNumber() && node.get("exp").asLong() < now)
                        {
                            keyStore.removeAPIKey(user, node.get("kid").asText());
                        }
                    }
                    catch (Exception ignored)
                    {
                        // skip malformed entries
                    }
                }
                return null;
            });
        }
        catch (RaplaException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RaplaException("api-key compaction failed: " + e.getMessage(), e);
        }
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
                                         long createdAt, Long expiresAt, Set<String> scopes, String prevKid)
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("kid", publicJwk.getKeyID());
        node.put("jwk", publicJwk.toJSONString());
        node.put("alg", JWSAlgorithm.RS256.getName());
        if (label != null) node.put("label", label);
        node.put("iat", createdAt);
        if (expiresAt != null) node.put("exp", expiresAt);
        // D14 — a rotation successor records its predecessor's kid so the next rotation can enforce
        // the max-2-per-chain sprawl guardrail. Absent on freshly-created (non-rotated) keys.
        if (prevKid != null) node.put("prev", prevKid);
        var scopeArray = node.putArray("scopes");
        for (String scope : scopes) scopeArray.add(scope);
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
            // Missing "scopes" ⇒ legacy full-power entry (D8); surface it as such in the listing.
            List<String> stored = readScopes(node);
            List<String> scopes = new ArrayList<>(ApiKeyScopes.resolveStored(stored));
            return new KeyMetadata(
                    kid,
                    label,
                    alg,
                    kid,
                    Instant.ofEpochMilli(iat).toString(),
                    exp == null ? null : Instant.ofEpochMilli(exp).toString(),
                    scopes);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** Reads the {@code scopes} array from a stored entry node, or {@code null} if absent (legacy). */
    private static List<String> readScopes(tools.jackson.databind.JsonNode node)
    {
        if (node == null || !node.has("scopes") || !node.get("scopes").isArray())
        {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (var s : node.get("scopes")) out.add(s.asText());
        return out;
    }

    public static class CreateRequest
    {
        public String label;
        public Long expiresInDays;
        public List<String> scopes;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Long getExpiresInDays() { return expiresInDays; }
        public void setExpiresInDays(Long expiresInDays) { this.expiresInDays = expiresInDays; }
        public List<String> getScopes() { return scopes; }
        public void setScopes(List<String> scopes) { this.scopes = scopes; }
    }

    public record CreateResponse(
            String id,
            String label,
            String alg,
            String thumbprint,
            String createdAt,
            String expiresAt,
            List<String> scopes,
            String key)
    {
    }

    public record KeyMetadata(
            String id,
            String label,
            String alg,
            String thumbprint,
            String createdAt,
            String expiresAt,
            List<String> scopes)
    {
    }

    /**
     * 409 — D14 sprawl guardrail: the key being rotated still has a live grace predecessor, so
     * rotating again would exceed 2 live tokens in the chain. Delete the old grace token first.
     * NOT a security control (a compromise is contained by revoking the active key, not by this).
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    static final class PreviousKeyStillActiveException extends RuntimeException
    {
        PreviousKeyStillActiveException()
        {
            super("previous key in this rotation chain is still active — delete it before rotating again");
        }
    }
}
