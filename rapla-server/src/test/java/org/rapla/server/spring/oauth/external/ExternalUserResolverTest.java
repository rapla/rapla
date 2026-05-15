package org.rapla.server.spring.oauth.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalUserResolverTest extends FacadeTestSupport
{
    private ExternalUserResolver resolver;
    private ProviderConfig microsoft;
    private ProviderConfig google;

    @BeforeEach
    void setUp()
    {
        resolver = new ExternalUserResolver(facade, logger);
        // Tests deliberately default to autoProvision=false to exercise the
        // pre-provisioned / email-match paths in isolation; auto-provision-on
        // tests build their own providerConfig.
        microsoft = providerConfig(ExternalProviderId.MICROSOFT, "oid", "email", false, "");
        google = providerConfig(ExternalProviderId.GOOGLE, "sub", "email", false, "");
    }

    @Test
    void resolvesByExternalIdPreferenceWhenSet() throws Exception
    {
        User existing = pickAnyExistingUser();
        attachExternalIdPreference(existing, microsoft.externalIdPreferenceKey(), "entra-oid-abc");

        Jwt jwt = entraJwt("entra-oid-abc", existing.getEmail());

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    @Test
    void resolvesByEmailFallbackAndSetsExternalIdPreference() throws Exception
    {
        User existing = pickUserWithEmail();
        String email = existing.getEmail();

        Jwt jwt = entraJwt("entra-oid-fresh", email);

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(existing.getUsername(), resolved.getUsername());

        TypedComponentRole<String> key = new TypedComponentRole<>(microsoft.externalIdPreferenceKey());
        String stored = facade.getPreferences(resolved).getEntryAsString(key, null);
        assertEquals("entra-oid-fresh", stored,
                "first-time email match must persist the external-id back to preferences");

        TypedComponentRole<String> activeProvider = ExternalUserResolver.ACTIVE_PROVIDER_PREFERENCE;
        String storedProvider = facade.getPreferences(resolved).getEntryAsString(activeProvider, null);
        assertEquals("microsoft", storedProvider);
    }

    @Test
    void throwsWhenAutoProvisionOffAndNoMatch()
    {
        Jwt jwt = entraJwt("entra-oid-unknown", "no.such.email@example.com");

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(jwt, microsoft));
        assertTrue(ex.getMessage().contains("auto-provision is disabled"));
    }

    @Test
    void throwsWhenExternalIdClaimMissing()
    {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("email", "alice@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(jwt, microsoft));
        assertTrue(ex.getMessage().contains("oid"));
    }

    @Test
    void googleRequiresEmailVerifiedForEmailMatch() throws Exception
    {
        User existing = pickUserWithEmail();

        Jwt unverified = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", google.issuer())
                .claim("sub", "google-sub-456")
                .claim("email", existing.getEmail())
                .claim("email_verified", false)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(unverified, google));
        assertTrue(ex.getMessage().contains("auto-provision is disabled"),
                "email_verified=false must block the email-match path even with auto-provision off");
    }

    @Test
    void googleAcceptsEmailMatchWhenEmailVerifiedTrue() throws Exception
    {
        User existing = pickUserWithEmail();

        Jwt verified = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", google.issuer())
                .claim("sub", "google-sub-789")
                .claim("email", existing.getEmail())
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(verified, google);
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    @Test
    void entraAcceptsEmailMatchWithoutEmailVerifiedClaim() throws Exception
    {
        User existing = pickUserWithEmail();

        Jwt jwt = entraJwt("entra-oid-noverify", existing.getEmail()); // no email_verified

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(existing.getUsername(), resolved.getUsername(),
                "Entra often doesn't ship email_verified; treat absent as trusted");
    }

    @Test
    void hostedDomainRejectsMismatchingHd()
    {
        ProviderConfig googleWithHd = providerConfig(
                ExternalProviderId.GOOGLE, "sub", "email", false, "example.com");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", googleWithHd.issuer())
                .claim("sub", "google-sub-xyz")
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
    void hostedDomainAcceptsMatchingHd() throws Exception
    {
        User existing = pickUserWithEmail();
        ProviderConfig googleWithHd = providerConfig(
                ExternalProviderId.GOOGLE, "sub", "email", false, domainOf(existing.getEmail()));

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", googleWithHd.issuer())
                .claim("sub", "google-sub-match")
                .claim("email", existing.getEmail())
                .claim("hd", domainOf(existing.getEmail()))
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, googleWithHd);
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    @Test
    void externalIdMatchTakesPrecedenceOverEmail() throws Exception
    {
        User userA = pickAnyExistingUser();
        User userB = pickUserWithEmail();
        // userA has the external-id pref; userB matches by email — userA should win.
        attachExternalIdPreference(userA, microsoft.externalIdPreferenceKey(), "entra-oid-stable");

        Jwt jwt = entraJwt("entra-oid-stable", userB.getEmail());

        User resolved = resolver.resolve(jwt, microsoft);
        assertEquals(userA.getUsername(), resolved.getUsername());
    }

    @Test
    void autoProvisionCreatesRaplaUserOnFirstLogin() throws Exception
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "oid", "email", true, "");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("oid", "entra-oid-new-1")
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

        // External-id preference must be persisted so the next login takes the
        // fast path (no email match needed).
        TypedComponentRole<String> key = new TypedComponentRole<>(microsoftAuto.externalIdPreferenceKey());
        assertEquals("entra-oid-new-1",
                facade.getPreferences(facade.getUser("newhire@example.com")).getEntryAsString(key, null));

        // User has rapla default groups (FacadeImpl.newUser): can read events
        // from others, can create events, can modify preferences. We don't
        // assert the exact set here — just that there's *some* group, since
        // group sync from token claims is deferred (PRD 036 OQ §13).
        assertNotNull(resolved.getGroupList());
        assertTrue(resolved.getGroupList().size() > 0,
                "auto-provisioned user must have facade.newUser()'s default groups");
    }

    @Test
    void autoProvisionFallsBackToEmailWhenUsernameClaimMissing() throws Exception
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "oid", "email", true, "");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("oid", "entra-oid-no-username")
                .claim("email", "alt@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        User resolved = resolver.resolve(jwt, microsoftAuto);

        assertEquals("alt@example.com", resolved.getUsername());
    }

    @Test
    void autoProvisionThrowsWhenNeitherUsernameNorEmailClaimPresent()
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "oid", "email", true, "");

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("oid", "entra-oid-empty")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        RaplaSecurityException ex = assertThrows(RaplaSecurityException.class,
                () -> resolver.resolve(jwt, microsoftAuto));
        assertTrue(ex.getMessage().contains("Cannot auto-provision"));
    }

    @Test
    void autoProvisionedUserResolvesByExternalIdOnNextLogin() throws Exception
    {
        ProviderConfig microsoftAuto = providerConfig(
                ExternalProviderId.MICROSOFT, "oid", "email", true, "");

        Jwt firstLogin = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("oid", "entra-oid-returning")
                .claim("preferred_username", "alice@example.com")
                .claim("email", "alice@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        User first = resolver.resolve(firstLogin, microsoftAuto);

        // Second login with a changed email — should still resolve to the same
        // rapla user via the external-id preference, not auto-provision again.
        Jwt secondLogin = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoftAuto.issuer())
                .claim("oid", "entra-oid-returning")
                .claim("preferred_username", "alice.renamed@example.com")
                .claim("email", "alice.renamed@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        User second = resolver.resolve(secondLogin, microsoftAuto);

        assertEquals(first.getId(), second.getId(),
                "external-id should pin the user across email changes");
    }

    private ProviderConfig providerConfig(ExternalProviderId id, String externalIdClaim,
                                          String emailClaim, boolean autoProvision, String hostedDomain)
    {
        String issuer = id == ExternalProviderId.MICROSOFT
                ? "https://login.microsoftonline.com/test-tenant/v2.0"
                : "https://accounts.google.com";
        return new ProviderConfig(
                id, "display", "icon", 10, true,
                "client-id", "", issuer,
                "https://idp/authorize", "https://idp/token", "https://idp/jwks", "",
                "",
                List.of("openid", "profile", "email"),
                new LinkedHashMap<>(),
                "preferred_username", emailClaim, externalIdClaim,
                hostedDomain, autoProvision, false);
    }

    private Jwt entraJwt(String oid, String email)
    {
        Jwt.Builder b = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", microsoft.issuer())
                .claim("oid", oid)
                .claim("preferred_username", email == null ? "alice" : email)
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
        // fallback: set one
        for (User u : facade.getUsers())
        {
            User editable = facade.edit(u);
            editable.setEmail("test-" + u.getUsername() + "@example.com");
            facade.store(editable);
            return facade.getUser(u.getUsername());
        }
        throw new IllegalStateException("fixture has no users");
    }

    private void attachExternalIdPreference(User user, String prefKey, String value) throws Exception
    {
        TypedComponentRole<String> role = new TypedComponentRole<>(prefKey);
        Preferences edit = facade.edit(facade.getPreferences(user));
        edit.putEntry(role, value);
        facade.store(edit);
    }

    private static String domainOf(String email)
    {
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(at + 1);
    }
}
