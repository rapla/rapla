package org.rapla.server.spring.oauth.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.server.IdentityClaims;
import org.rapla.server.internal.DefaultUserProvisioner;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identity model: username is the rapla user identity. The resolver maps an
 * external OIDC token to a rapla user by matching the token's username claims
 * (upn → preferred_username → email) case-insensitively against
 * {@code user.getUsername()}. No external-id preference, no active-provider
 * preference — both removed 2026-05-21.
 *
 * <p><strong>PRD 050 Phase 7 — resolve is pure.</strong> Auto-provisioning
 * moved out of {@code ExternalUserResolver.resolve} and into the OAuth
 * exchange seam ({@code OAuthExchangeController}), which builds claims via
 * {@link ExternalUserResolver#claimsFor} and hands them to
 * {@link DefaultUserProvisioner}. The tests below split accordingly:
 * resolve-side tests assert pure lookup behaviour + no storage writes;
 * provisioning-side tests exercise claimsFor + provisioner together.
 */
class ExternalUserResolverTest extends FacadeTestSupport
{
    private ExternalUserResolver resolver;
    private DefaultUserProvisioner provisioner;
    private ProviderConfig microsoft;
    private ProviderConfig google;

    @BeforeEach
    void setUp()
    {
        resolver = new ExternalUserResolver(operator);
        provisioner = new DefaultUserProvisioner(operator);
        microsoft = providerConfig(ExternalProviderId.MICROSOFT, "email", false, "");
        google = providerConfig(ExternalProviderId.GOOGLE, "email", false, "");
    }

    @Test
    void resolvesByExactUsernameMatch() throws Exception
    {
        User existing = pickAnyExistingUser();

        Jwt jwt = entraJwt(existing.getUsername(), existing.getEmail());

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    @Test
    void resolvesByUsernameCaseInsensitively() throws Exception
    {
        User existing = pickAnyExistingUser();
        String mixed = mixCase(existing.getUsername());

        Jwt jwt = entraJwt(mixed, existing.getEmail());

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(existing.getUsername(), resolved.getUsername(),
                "case differences between token and stored username must not block the match");
    }

    @Test
    void prefersUpnOverPreferredUsernameForLookup() throws Exception
    {
        User existing = pickAnyExistingUser();

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("upn", existing.getUsername())
                .claim("preferred_username", "some.other.handle")
                .claim("email", "unrelated@example.org")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    @Test
    void fallsBackToEmailWhenUpnAndPreferredUsernameDoNotMatch() throws Exception
    {
        User existing = pickUserWithEmail();

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", "no.such.handle")
                .claim("email", existing.getUsername())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    @Test
    void throwsWhenNoMatch()
    {
        Jwt jwt = entraJwt("nobody@example.org", "nobody@example.org");

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(jwt, microsoft));
        assertTrue(ex.getMessage().contains("No rapla user matched"),
                "PRD 050 Phase 7: resolve is pure — no auto-provision; missing user → 'No rapla user matched'");
    }

    @Test
    void googleRequiresEmailVerifiedForEmailFallback() throws Exception
    {
        User existing = pickUserWithEmail();

        Jwt unverified = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", google.issuer())
                .claim("preferred_username", "no.such.handle")
                .claim("email", existing.getUsername())
                .claim("email_verified", false)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(unverified, google),
                "email_verified=false must block the email-fallback path");
    }

    @Test
    void googleAcceptsEmailFallbackWhenEmailVerifiedTrue() throws Exception
    {
        User existing = pickUserWithEmail();

        Jwt verified = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", google.issuer())
                .claim("preferred_username", "no.such.handle")
                .claim("email", existing.getUsername())
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(verified, google);
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    @Test
    void entraAcceptsEmailFallbackWithoutEmailVerifiedClaim() throws Exception
    {
        User existing = pickUserWithEmail();

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", "no.such.handle")
                .claim("email", existing.getUsername())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(existing.getUsername(), resolved.getUsername(),
                "Entra often doesn't ship email_verified; treat absent as trusted");
    }

    @Test
    void hostedDomainRejectsMismatchingHd()
    {
        ProviderConfig googleWithHd = providerConfig(
                ExternalProviderId.GOOGLE, "email", false, "example.com");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", googleWithHd.issuer())
                .claim("preferred_username", "alice@evil.com")
                .claim("email", "alice@evil.com")
                .claim("hd", "evil.com")
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(jwt, googleWithHd));
        assertTrue(ex.getMessage().contains("hosted-domain"));
    }

    /**
     * PRD 050 Phase 7 — resolve must never write. Even when an IdP login
     * presents claims that disagree with the rapla-stored state, resolve
     * returns the matched user untouched. Provisioning happens elsewhere.
     */
    @Test
    void resolveDoesNotWriteEvenWhenClaimsDiffer() throws Exception
    {
        User existing = pickAnyExistingUser();
        LocalDateTime beforeLastChanged = existing.getLastChanged();
        String beforeName = existing.getName();
        String beforeEmail = existing.getEmail();
        String beforeSource = existing.getAuthenticationSource();

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", existing.getUsername())
                .claim("name", "Completely Different Name")
                .claim("email", "completely-different@example.org")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, microsoft);

        // Re-read from operator to defeat any local-instance staleness.
        User reread = operator.getUser(existing.getUsername());
        assertEquals(beforeLastChanged, reread.getLastChanged(),
                "resolve must be side-effect-free — lastChanged unchanged after a login that would have synced");
        assertEquals(beforeName, reread.getName());
        assertEquals(beforeEmail, reread.getEmail());
        assertEquals(beforeSource, reread.getAuthenticationSource());
        assertSame(resolved.getId(), reread.getId());
    }

    /**
     * PRD 050 Phase 8 — claimsFor extracts the IdentityClaims blob the
     * provisioner needs, mirroring the resolve() priority order
     * (upn → preferred_username → email). Pure: no writes.
     */
    @Test
    void claimsForPrefersUpnLowercased() throws Exception
    {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("upn", "Pat.Test@adcorp.example.org")
                .claim("preferred_username", "pat.test")
                .claim("email", "pat.test@corp.example.org")
                .claim("name", "Pat Test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        IdentityClaims claims = resolver.claimsFor(jwt, microsoft);
        assertEquals("pat.test@adcorp.example.org", claims.username(),
                "upn precedence; lowercased");
        assertEquals("Pat Test", claims.displayName());
        assertEquals("pat.test@corp.example.org", claims.email());
        assertEquals(microsoft.id(), claims.sourceId());
    }

    @Test
    void claimsForFallsBackToUsernameClaimWhenUpnAbsent() throws Exception
    {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", "carol@example.com")
                .claim("email", "carol@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        IdentityClaims claims = resolver.claimsFor(jwt, microsoft);
        assertEquals("carol@example.com", claims.username());
    }

    @Test
    void claimsForFallsBackToEmailWhenUsernameClaimMissing() throws Exception
    {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("email", "alt@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        IdentityClaims claims = resolver.claimsFor(jwt, microsoft);
        assertEquals("alt@example.com", claims.username());
    }

    @Test
    void claimsForThrowsWhenNoUsernameSourcePresent()
    {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.claimsFor(jwt, microsoft));
        assertTrue(ex.getMessage().contains("Cannot extract identity claims"));
    }

    @Test
    void claimsForLowercasesUsernameRegardlessOfSource() throws Exception
    {
        Jwt mixedCase = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", "Alice.Doe@Example.COM")
                .claim("email", "Alice.Doe@Example.COM")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        IdentityClaims claims = resolver.claimsFor(mixedCase, microsoft);
        assertEquals("alice.doe@example.com", claims.username());
    }

    /**
     * End-to-end: claimsFor + provisioner — auto-provisions a new rapla user
     * on first IdP login at the exchange seam. Equivalent to the pre-Phase-7
     * "resolve auto-provisions" behaviour but driven by the at-login seam,
     * not the resource-server resolve path.
     */
    @Test
    void claimsForPlusProvisionerAutoProvisionsOnFirstLogin() throws Exception
    {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", "newhire@example.com")
                .claim("email", "newhire@example.com")
                .claim("name", "New Hire")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        IdentityClaims claims = resolver.claimsFor(jwt, microsoft);
        User provisioned = provisioner.provision(claims);

        assertNotNull(provisioned);
        assertEquals("newhire@example.com", provisioned.getUsername());
        assertEquals("New Hire", provisioned.getName());
        assertEquals("newhire@example.com", provisioned.getEmail());
        assertEquals(microsoft.id(), provisioned.getAuthenticationSource());
        assertTrue(provisioned.getGroupList().size() > 0,
                "auto-provisioned user must have default groups");

        // resolve should now find the new user without writing again.
        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(provisioned.getId(), resolved.getId());
    }

    /**
     * Second login is idempotent — provisioner sees nothing to update
     * (claims match stored values) and returns without a write.
     */
    @Test
    void secondLoginIsIdempotent() throws Exception
    {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", "alice@example.com")
                .claim("email", "alice@example.com")
                .claim("name", "Alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        IdentityClaims claims = resolver.claimsFor(jwt, microsoft);
        User first = provisioner.provision(claims);
        LocalDateTime firstLastChanged = first.getLastChanged();

        User second = provisioner.provision(claims);

        assertEquals(first.getId(), second.getId());
        assertEquals(firstLastChanged, second.getLastChanged(),
                "idempotent — second provision with identical claims must not bump lastChanged");
        // Ensure the resolver agrees the cache wasn't churned by an unnecessary write.
        User reread = operator.getUser("alice@example.com");
        assertEquals(firstLastChanged, reread.getLastChanged());
        assertFalse(reread.getName().isEmpty());
    }

    /**
     * Returning user is found via resolve() (no provisioner call needed for
     * lookup). Email changes between logins don't fragment identity — the
     * username is the stable key.
     */
    @Test
    void returningUserResolvesByUsernameAcrossEmailChange() throws Exception
    {
        Jwt firstJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", "alice@example.com")
                .claim("email", "alice@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        IdentityClaims firstClaims = resolver.claimsFor(firstJwt, microsoft);
        User firstUser = provisioner.provision(firstClaims);

        Jwt secondJwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", "alice@example.com")
                .claim("email", "alice.renamed@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        // Pure resolve — same user, no auto-provision.
        User secondUser = resolver.resolve(secondJwt, microsoft);

        assertEquals(firstUser.getId(), secondUser.getId(),
                "stable username must pin identity across email changes");
    }

    private ProviderConfig providerConfig(ExternalProviderId id, String emailClaim,
                                          boolean autoProvision, String hostedDomain)
    {
        String issuer = id == ExternalProviderId.MICROSOFT
                ? "https://login.microsoftonline.com/test-tenant/v2.0"
                : "https://accounts.google.com";
        return new ProviderConfig(
                id.id(), id, "display", "icon", 10, true,
                "client-id", "", issuer,
                "https://idp/authorize", "https://idp/token", "https://idp/jwks", "",
                "",
                List.of("openid", "profile", "email"),
                new LinkedHashMap<>(),
                "preferred_username", emailClaim, "",
                hostedDomain, autoProvision, false, false);
    }

    private Jwt entraJwt(String preferredUsername, String email)
    {
        Jwt.Builder b = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("preferred_username", preferredUsername == null ? "alice" : preferredUsername)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (email != null) b = b.claim("email", email);
        return b.build();
    }

    private User pickAnyExistingUser() throws Exception
    {
        for (User u : facade.getUsers())
        {
            return u;
        }
        throw new IllegalStateException("fixture has no users");
    }

    private User pickUserWithEmail() throws Exception
    {
        for (User u : facade.getUsers())
        {
            String email = u.getEmail();
            if (email != null && !email.isEmpty()) return u;
        }
        for (User u : facade.getUsers())
        {
            User editable = facade.edit(u);
            editable.setEmail("test-" + u.getUsername() + "@example.com");
            facade.store(editable);
            return facade.getUser(u.getUsername());
        }
        throw new IllegalStateException("fixture has no users");
    }

    private static String mixCase(String s)
    {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            sb.append((i & 1) == 0
                    ? Character.toUpperCase(c)
                    : Character.toLowerCase(c));
        }
        if (sb.toString().equalsIgnoreCase(s) && !sb.toString().equals(s)) return sb.toString();
        return sb.toString().toLowerCase(Locale.ROOT).equals(s) ? sb.toString().toUpperCase(Locale.ROOT) : sb.toString();
    }
    /** The provisioning path must apply the same rule as {@code resolve()}: an identity derived
     *  from the email claim is only trusted when the IdP vouches for it ({@code email_verified}). */
    @Test
    void claimsForRejectsEmailDerivedUsernameWhenEmailVerifiedFalse()
    {
        Jwt unverified = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", google.issuer())
                .claim("email", "victim@example.com")
                .claim("email_verified", false)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThrows(RaplaSecurityException.class, () -> resolver.claimsFor(unverified, google),
                "email_verified=false must not yield a provisionable identity");
    }

    @Test
    void claimsForAcceptsEmailDerivedUsernameWhenEmailVerifiedTrue() throws Exception
    {
        Jwt verified = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", google.issuer())
                .claim("email", "Someone@example.com")
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertEquals("someone@example.com", resolver.claimsFor(verified, google).username());
    }

}
