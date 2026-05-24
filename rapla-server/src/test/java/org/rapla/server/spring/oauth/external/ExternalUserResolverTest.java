package org.rapla.server.spring.oauth.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identity model: username is the rapla user identity. The resolver maps an
 * external OIDC token to a rapla user by matching the token's username claims
 * (upn → preferred_username → email) case-insensitively against
 * {@code user.getUsername()}. No external-id preference, no active-provider
 * preference — both removed 2026-05-21 because they (a) made first-login
 * fragile (the import-from-CSV / hand-created rapla user has no pref and the
 * stored email often disagreed with the IdP's email claim), and (b) silently
 * broke when an IdP rotated realms (the stored sub becomes meaningless;
 * username remains valid).
 *
 * <p>Tradeoff documented: an IdP-side username rename produces an orphaned
 * rapla user + a new auto-provisioned one. Same operator burden as the legacy
 * LDAP path — admin renames the rapla user manually.
 */
class ExternalUserResolverTest extends FacadeTestSupport
{
    private ExternalUserResolver resolver;
    private ProviderConfig microsoft;
    private ProviderConfig google;

    @BeforeEach
    void setUp()
    {
        resolver = new ExternalUserResolver(facade);
        // Defaults to autoProvision=false so each test opts in deliberately.
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
        // Token's `upn` is the AD UserPrincipalName form (e.g. the user
        // imported from AD with their UPN as rapla username), while
        // `preferred_username` is the bare login. Lookup tries upn first so
        // an AD-federated Keycloak finds the existing pre-imported user.
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

        // The email claim is the only one matching a stored username — that
        // can happen when an institution uses email-as-username and the IdP
        // doesn't emit upn.
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
    void throwsWhenNoMatchAndAutoProvisionOff()
    {
        Jwt jwt = entraJwt("nobody@example.org", "nobody@example.org");

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(jwt, microsoft));
        assertTrue(ex.getMessage().contains("auto-provision is disabled"));
    }

    @Test
    void googleRequiresEmailVerifiedForEmailFallback() throws Exception
    {
        User existing = pickUserWithEmail();

        // No upn / preferred_username match — only the email lookup could
        // possibly link this token to the existing user. email_verified=false
        // must veto that path (Google can lie about an unverified email).
        Jwt unverified = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", google.issuer())
                .claim("preferred_username", "no.such.handle")
                .claim("email", existing.getUsername())
                .claim("email_verified", false)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(unverified, google));
        assertTrue(ex.getMessage().contains("auto-provision is disabled"),
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

    @Test
    void autoProvisionPrefersUpnOverConfiguredUsernameClaim() throws Exception
    {
        // AD-federated Keycloak emits both `upn` (the AD UserPrincipalName,
        // e.g. "Pat.Test@adcorp.example.org") and `preferred_username` (the
        // bare login name, "pat.test"). Existing rapla deployments key users
        // by UPN, so auto-provisioning must prefer it over the configured
        // `usernameClaim`. The chosen value is lowercased before storage.
        ProviderConfig keycloakAuto = new ProviderConfig(
                ExternalProviderId.KEYCLOAK, "display", "icon", 15, true,
                "client-id", "", "https://kc.example.com/realms/r",
                "https://kc.example.com/auth", "https://kc.example.com/token",
                "https://kc.example.com/jwks", "",
                "",
                List.of("openid", "profile", "email"),
                new LinkedHashMap<>(),
                "preferred_username", "email", "sub",
                "", true, false);

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", keycloakAuto.issuer())
                .claim("sub", "kc-sub-upn-1")
                .claim("upn", "Pat.Test@adcorp.example.org")
                .claim("preferred_username", "pat.test")
                .claim("email", "pat.test@corp.example.org")
                .claim("name", "Pat Test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, keycloakAuto);
        assertEquals("pat.test@adcorp.example.org", resolved.getUsername(),
                "UPN must take precedence over preferred_username when both are present, "
                        + "lowercased so the stored form is case-canonical");
    }

    @Test
    void autoProvisionLowercasesUsernameRegardlessOfSource() throws Exception
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "email", true, "");

        Jwt mixedCase = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("preferred_username", "Alice.Doe@Example.COM")
                .claim("email", "Alice.Doe@Example.COM")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(mixedCase, microsoftAuto);
        assertEquals("alice.doe@example.com", resolved.getUsername());
    }

    @Test
    void autoProvisionCreatesRaplaUserOnFirstLogin() throws Exception
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "email", true, "");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("preferred_username", "newhire@example.com")
                .claim("email", "newhire@example.com")
                .claim("name", "New Hire")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, microsoftAuto);

        assertNotNull(resolved);
        assertEquals("newhire@example.com", resolved.getUsername());
        assertEquals("New Hire", resolved.getName());
        assertEquals("newhire@example.com", resolved.getEmail());
        // facade.newUser() seeds default groups; the exact set isn't pinned.
        assertNotNull(resolved.getGroupList());
        assertTrue(resolved.getGroupList().size() > 0,
                "auto-provisioned user must have facade.newUser()'s default groups");
    }

    @Test
    void autoProvisionFallsBackToUsernameClaimWhenUpnAbsent() throws Exception
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "email", true, "");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("preferred_username", "carol@example.com")
                .claim("email", "carol@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, microsoftAuto);
        assertEquals("carol@example.com", resolved.getUsername());
    }

    @Test
    void autoProvisionFallsBackToEmailWhenUsernameClaimMissing() throws Exception
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "email", true, "");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("email", "alt@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, microsoftAuto);
        assertEquals("alt@example.com", resolved.getUsername());
    }

    @Test
    void autoProvisionThrowsWhenNoUsernameSourcePresent()
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "email", true, "");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(jwt, microsoftAuto));
        assertTrue(ex.getMessage().contains("Cannot auto-provision"));
    }

    @Test
    void returningUserResolvesByUsernameWithoutAutoProvisioningAgain() throws Exception
    {
        // Identity is username-based: a returning user is found by their
        // stored username regardless of whether the IdP's `sub` changed
        // (realm rotation / IdP swap). No external-id preference is needed.
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "email", true, "");

        Jwt first = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("preferred_username", "alice@example.com")
                .claim("email", "alice@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        User firstUser = resolver.resolve(first, microsoftAuto);

        // Second login: same username, different email (rare but possible
        // if the IdP changes the routable-email form). Same human; rapla
        // must return the same user, not auto-provision a duplicate.
        Jwt second = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("preferred_username", "alice@example.com")
                .claim("email", "alice.renamed@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        User secondUser = resolver.resolve(second, microsoftAuto);

        assertEquals(firstUser.getId(), secondUser.getId(),
                "stable username must pin identity across email changes");
    }

    private ProviderConfig providerConfig(ExternalProviderId id, String emailClaim,
                                          boolean autoProvision, String hostedDomain)
    {
        String issuer = id == ExternalProviderId.MICROSOFT
                ? "https://login.microsoftonline.com/test-tenant/v2.0"
                : "https://accounts.google.com";
        // externalIdClaim kept in the ctor signature (unused by the resolver
        // since the pref-based lookup was removed) — passed as "" so a future
        // grep for "external-id" in tests turns up empty.
        return new ProviderConfig(
                id, "display", "icon", 10, true,
                "client-id", "", issuer,
                "https://idp/authorize", "https://idp/token", "https://idp/jwks", "",
                "",
                List.of("openid", "profile", "email"),
                new LinkedHashMap<>(),
                "preferred_username", emailClaim, "",
                hostedDomain, autoProvision, false);
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
        // sanity check: at least one char must differ in case if input had letters
        if (sb.toString().equalsIgnoreCase(s) && !sb.toString().equals(s)) return sb.toString();
        return sb.toString().toLowerCase(Locale.ROOT).equals(s) ? sb.toString().toUpperCase(Locale.ROOT) : sb.toString();
    }
}
