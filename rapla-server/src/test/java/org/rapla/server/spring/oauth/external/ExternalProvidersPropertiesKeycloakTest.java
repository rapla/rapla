package org.rapla.server.spring.oauth.external;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 036 Phase 2.1 + Phase 3 — Keycloak as an external OIDC provider, now via
 * the {@code Map<String, ProviderDef>} registration map. Tier-2: no Spring.
 * Locks the {@code base-url + realm} URL derivation, required-field validation,
 * {@code enabledProviders()} inclusion, multiple-of-a-type, type inference, and
 * fail-fast on an undeterminable type.
 */
class ExternalProvidersPropertiesKeycloakTest
{
    private ProviderDef enabledKeycloak()
    {
        ProviderDef kc = new ProviderDef();
        kc.setEnabled(true);
        kc.setType("keycloak");
        kc.setBaseUrl("http://localhost:8080");
        kc.setRealm("rapla");
        kc.setClientId("rapla-app");
        return kc;
    }

    private ExternalProvidersProperties props(Map<String, ProviderDef> entries)
    {
        ExternalProvidersProperties props = new ExternalProvidersProperties();
        props.setExternal(entries);
        return props;
    }

    @Test
    void noProvidersByDefault()
    {
        ExternalProvidersProperties props = new ExternalProvidersProperties();
        assertTrue(props.getExternal().isEmpty());
        assertTrue(props.enabledProviders().isEmpty());
    }

    @Test
    void enabledKeycloakDerivesOidcUrlsFromBaseUrlAndRealm()
    {
        ProviderConfig cfg = enabledKeycloak().toProviderConfig("keycloak");

        assertEquals(ExternalProviderId.KEYCLOAK, cfg.type());
        assertEquals("keycloak", cfg.id());
        assertEquals("http://localhost:8080/realms/rapla", cfg.issuer());
        assertEquals("http://localhost:8080/realms/rapla/protocol/openid-connect/auth", cfg.authorizeUrl());
        assertEquals("http://localhost:8080/realms/rapla/protocol/openid-connect/token", cfg.tokenUrl());
        assertEquals("http://localhost:8080/realms/rapla/protocol/openid-connect/certs", cfg.jwksUrl());
        assertEquals("http://localhost:8080/realms/rapla/protocol/openid-connect/logout", cfg.endSessionUrl());
    }

    @Test
    void registrationIdIsTheMapKeyNotTheType()
    {
        // Phase 3: id() is the registrationId, decoupled from the type.
        ProviderConfig cfg = enabledKeycloak().toProviderConfig("dhbw");
        assertEquals("dhbw", cfg.id());
        assertEquals(ExternalProviderId.KEYCLOAK, cfg.type());
    }

    @Test
    void trailingSlashInBaseUrlIsStripped()
    {
        ProviderDef kc = enabledKeycloak();
        kc.setBaseUrl("http://localhost:8080/");
        assertEquals("http://localhost:8080/realms/rapla", kc.toProviderConfig("keycloak").issuer());
    }

    @Test
    void defaultClaimsAreOidcStandard()
    {
        ProviderConfig cfg = enabledKeycloak().toProviderConfig("keycloak");
        assertEquals("preferred_username", cfg.usernameClaim());
        assertEquals("email", cfg.emailClaim());
        assertEquals("sub", cfg.externalIdClaim());
    }

    @Test
    void publicClientHasNoSecretAndIsNotMultiTenant()
    {
        ProviderConfig cfg = enabledKeycloak().toProviderConfig("keycloak");
        assertTrue(cfg.clientSecret().isEmpty());
        assertFalse(cfg.isMultiTenant());
        assertTrue(cfg.matchesIssuer("http://localhost:8080/realms/rapla"));
        assertFalse(cfg.matchesIssuer("http://localhost:8080/realms/other"));
    }

    @Test
    void enabledRequiresBaseUrl()
    {
        ProviderDef kc = enabledKeycloak();
        kc.setBaseUrl("");
        assertThrows(IllegalStateException.class, () -> kc.toProviderConfig("keycloak"));
    }

    @Test
    void enabledRequiresRealm()
    {
        ProviderDef kc = enabledKeycloak();
        kc.setRealm("");
        assertThrows(IllegalStateException.class, () -> kc.toProviderConfig("keycloak"));
    }

    @Test
    void enabledRequiresClientId()
    {
        ProviderDef kc = enabledKeycloak();
        kc.setClientId("");
        assertThrows(IllegalStateException.class, () -> kc.toProviderConfig("keycloak"));
    }

    @Test
    void enabledProvidersIncludesKeycloakAlongsideOthers()
    {
        ProviderDef google = new ProviderDef();
        google.setEnabled(true);
        google.setType("google");
        google.setClientId("g-client");

        Map<String, ProviderDef> entries = new LinkedHashMap<>();
        entries.put("keycloak", enabledKeycloak());
        entries.put("google", google);
        ExternalProvidersProperties props = props(entries);

        List<ProviderConfig> enabled = props.enabledProviders();
        assertEquals(2, enabled.size());
        assertTrue(enabled.stream().anyMatch(p -> p.type() == ExternalProviderId.KEYCLOAK));
        assertTrue(props.byId("keycloak").isPresent());
        assertTrue(props.byIssuer("http://localhost:8080/realms/rapla").isPresent());
    }

    @Test
    void twoKeycloaksRegisterUnderDistinctIdsWithDistinctIssuers()
    {
        // Phase 3 core case: DHBW Mosbach + a second realm, both type keycloak.
        ProviderDef mos = new ProviderDef();
        mos.setEnabled(true);
        mos.setType("keycloak");
        mos.setBaseUrl("https://login.mosbach.dhbw.de");
        mos.setRealm("dhbwmos-lehre");
        mos.setClientId("rapla-app");

        Map<String, ProviderDef> entries = new LinkedHashMap<>();
        entries.put("keycloak", mos);
        entries.put("dhbw", enabledKeycloak());
        ExternalProvidersProperties props = props(entries);

        List<ProviderConfig> enabled = props.enabledProviders();
        assertEquals(2, enabled.size());
        assertTrue(props.byId("keycloak").isPresent());
        assertTrue(props.byId("dhbw").isPresent());
        assertEquals("https://login.mosbach.dhbw.de/realms/dhbwmos-lehre", props.byId("keycloak").get().issuer());
        assertEquals("http://localhost:8080/realms/rapla", props.byId("dhbw").get().issuer());
    }

    @Test
    void typeIsInferredFromTheKeyWhenOmitted()
    {
        // Back-compat: a legacy fixed-key block with no explicit type still binds.
        ProviderDef kc = enabledKeycloak();
        kc.setType("");
        ProviderConfig cfg = kc.toProviderConfig("keycloak");
        assertEquals(ExternalProviderId.KEYCLOAK, cfg.type());
    }

    @Test
    void failsFastWhenTypeCanBeNeitherParsedNorInferred()
    {
        // Arbitrary key + no type → must throw, never silently drop (D-3.3).
        ProviderDef kc = enabledKeycloak();
        kc.setType("");
        assertThrows(IllegalStateException.class, () -> kc.toProviderConfig("dhbw"));
    }
}
