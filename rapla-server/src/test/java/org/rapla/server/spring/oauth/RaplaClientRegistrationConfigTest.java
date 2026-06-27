package org.rapla.server.spring.oauth;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ProviderDef;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 036 Phase 3 — the legacy-callback bridge is per-provider. A keycloak with
 * {@code legacy-callback: true} sends the legacy {@code /app/auth/callback}
 * redirect_uri; a second keycloak (no flag) keeps its conformant
 * {@code /login/oauth2/code/{id}}. Tier-2: no Spring context.
 */
class RaplaClientRegistrationConfigTest
{
    private ProviderDef keycloak(String baseUrl, String realm, boolean legacy)
    {
        ProviderDef kc = new ProviderDef();
        kc.setEnabled(true);
        kc.setType("keycloak");
        kc.setBaseUrl(baseUrl);
        kc.setRealm(realm);
        kc.setClientId("rapla-app");
        kc.setLegacyCallback(legacy);
        return kc;
    }

    @Test
    void legacyKeycloakSendsAppCallbackTheOtherStaysConformant()
    {
        ExternalProvidersProperties props = new ExternalProvidersProperties();
        props.getExternal().put("keycloak", keycloak("https://login.mosbach.dhbw.de", "dhbwmos-lehre", true));
        props.getExternal().put("dhbw", keycloak("http://localhost:8080", "rapla", false));

        ClientRegistrationRepository repo =
                new RaplaClientRegistrationConfig().clientRegistrationRepository(props);

        ClientRegistration mosbach = repo.findByRegistrationId("keycloak");
        ClientRegistration local = repo.findByRegistrationId("dhbw");

        assertEquals("{baseUrl}/app/auth/callback", mosbach.getRedirectUri(),
                "the legacy-callback keycloak must send /app/auth/callback");
        assertEquals("{baseUrl}/login/oauth2/code/{registrationId}", local.getRedirectUri(),
                "a keycloak without legacy-callback keeps the conformant per-provider callback");
    }
}
