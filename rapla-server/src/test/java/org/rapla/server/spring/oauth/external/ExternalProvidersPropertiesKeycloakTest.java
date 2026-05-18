package org.rapla.server.spring.oauth.external;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 036 Phase 2.1 — Keycloak as a first-class external OIDC provider.
 * Tier-1: no Spring. Locks the {@code base-url + realm} URL derivation, the
 * required-field validation, and {@code enabledProviders()} inclusion.
 */
class ExternalProvidersPropertiesKeycloakTest
{
    private ExternalProvidersProperties.Keycloak enabledKeycloak()
    {
        ExternalProvidersProperties.Keycloak kc = new ExternalProvidersProperties.Keycloak();
        kc.setEnabled(true);
        kc.setBaseUrl("http://localhost:8080");
        kc.setRealm("rapla");
        kc.setClientId("rapla-app");
        return kc;
    }

    @Test
    void keycloakIsDisabledByDefault()
    {
        ExternalProvidersProperties props = new ExternalProvidersProperties();
        assertFalse(props.getKeycloak().isEnabled());
        assertTrue(props.enabledProviders().isEmpty());
    }

    @Test
    void enabledKeycloakDerivesOidcUrlsFromBaseUrlAndRealm()
    {
        ProviderConfig cfg = enabledKeycloak().toProviderConfig();

        assertEquals(ExternalProviderId.KEYCLOAK, cfg.provider());
        assertEquals("keycloak", cfg.id());
        assertEquals("http://localhost:8080/realms/rapla", cfg.issuer());
        assertEquals("http://localhost:8080/realms/rapla/protocol/openid-connect/auth", cfg.authorizeUrl());
        assertEquals("http://localhost:8080/realms/rapla/protocol/openid-connect/token", cfg.tokenUrl());
        assertEquals("http://localhost:8080/realms/rapla/protocol/openid-connect/certs", cfg.jwksUrl());
        assertEquals("http://localhost:8080/realms/rapla/protocol/openid-connect/logout", cfg.endSessionUrl());
    }

    @Test
    void trailingSlashInBaseUrlIsStripped()
    {
        ExternalProvidersProperties.Keycloak kc = enabledKeycloak();
        kc.setBaseUrl("http://localhost:8080/");
        assertEquals("http://localhost:8080/realms/rapla", kc.toProviderConfig().issuer());
    }

    @Test
    void defaultClaimsAreOidcStandard()
    {
        ProviderConfig cfg = enabledKeycloak().toProviderConfig();
        assertEquals("preferred_username", cfg.usernameClaim());
        assertEquals("email", cfg.emailClaim());
        assertEquals("sub", cfg.externalIdClaim());
    }

    @Test
    void publicClientHasNoSecretAndIsNotMultiTenant()
    {
        ProviderConfig cfg = enabledKeycloak().toProviderConfig();
        assertTrue(cfg.clientSecret().isEmpty());
        assertFalse(cfg.isMultiTenant());
        assertTrue(cfg.matchesIssuer("http://localhost:8080/realms/rapla"));
        assertFalse(cfg.matchesIssuer("http://localhost:8080/realms/other"));
    }

    @Test
    void enabledRequiresBaseUrl()
    {
        ExternalProvidersProperties.Keycloak kc = enabledKeycloak();
        kc.setBaseUrl("");
        assertThrows(IllegalStateException.class, kc::toProviderConfig);
    }

    @Test
    void enabledRequiresRealm()
    {
        ExternalProvidersProperties.Keycloak kc = enabledKeycloak();
        kc.setRealm("");
        assertThrows(IllegalStateException.class, kc::toProviderConfig);
    }

    @Test
    void enabledRequiresClientId()
    {
        ExternalProvidersProperties.Keycloak kc = enabledKeycloak();
        kc.setClientId("");
        assertThrows(IllegalStateException.class, kc::toProviderConfig);
    }

    @Test
    void enabledProvidersIncludesKeycloakAlongsideOthers()
    {
        ExternalProvidersProperties props = new ExternalProvidersProperties();
        props.setKeycloak(enabledKeycloak());

        ExternalProvidersProperties.Google google = new ExternalProvidersProperties.Google();
        google.setEnabled(true);
        google.setClientId("g-client");
        props.setGoogle(google);

        List<ProviderConfig> enabled = props.enabledProviders();
        assertEquals(2, enabled.size());
        assertTrue(enabled.stream().anyMatch(p -> p.provider() == ExternalProviderId.KEYCLOAK));
        assertTrue(props.byId("keycloak").isPresent());
        assertTrue(props.byIssuer("http://localhost:8080/realms/rapla").isPresent());
    }
}
