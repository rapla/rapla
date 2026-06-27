package org.rapla.server.spring.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRD 072 follow-up — tier-1 coverage for the {@code prompt}-forwarding resolver.
 *
 * <p>In the M2 broker model rapla's logout is rapla-local and never propagates to
 * the upstream IdP, so the next SSO login would silently re-authenticate against
 * the still-live Keycloak SSO session. The rapla {@code /login} page appends
 * {@code ?prompt=login} to the SSO links only after an explicit logout; this
 * resolver forwards that (whitelisted) parameter into the OIDC authorize request
 * so Keycloak re-prompts. Ordinary logins (no {@code prompt}) keep silent SSO.
 */
class RaplaOAuth2AuthorizationRequestResolverTest
{
    private final RaplaOAuth2AuthorizationRequestResolver resolver =
            new RaplaOAuth2AuthorizationRequestResolver(
                    new InMemoryClientRegistrationRepository(keycloakRegistration()));

    private static ClientRegistration keycloakRegistration()
    {
        return ClientRegistration.withRegistrationId("keycloak")
                .clientId("rapla-app")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("https://kc.example.org/realms/rapla/protocol/openid-connect/auth")
                .tokenUri("https://kc.example.org/realms/rapla/protocol/openid-connect/token")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .clientName("Sign in with university SSO")
                .build();
    }

    private static MockHttpServletRequest authorizeRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        request.setServletPath("/oauth2/authorization/keycloak");
        return request;
    }

    @Test
    void ordinaryLoginCarriesNoPrompt()
    {
        OAuth2AuthorizationRequest req = resolver.resolve(authorizeRequest());
        assertNotNull(req, "resolver must match /oauth2/authorization/keycloak");
        assertFalse(req.getAdditionalParameters().containsKey("prompt"),
                "no prompt param on request → silent SSO (no prompt forwarded)");
    }

    @Test
    void promptLoginIsForwarded()
    {
        MockHttpServletRequest request = authorizeRequest();
        request.setParameter("prompt", "login");
        OAuth2AuthorizationRequest req = resolver.resolve(request);
        assertNotNull(req);
        assertEquals("login", req.getAdditionalParameters().get("prompt"));
    }

    @Test
    void promptSelectAccountIsForwarded()
    {
        MockHttpServletRequest request = authorizeRequest();
        request.setParameter("prompt", "select_account");
        OAuth2AuthorizationRequest req = resolver.resolve(request);
        assertNotNull(req);
        assertEquals("select_account", req.getAdditionalParameters().get("prompt"));
    }

    @Test
    void unknownPromptValueIsIgnored()
    {
        MockHttpServletRequest request = authorizeRequest();
        request.setParameter("prompt", "consent; rm -rf");
        OAuth2AuthorizationRequest req = resolver.resolve(request);
        assertNotNull(req);
        assertFalse(req.getAdditionalParameters().containsKey("prompt"),
                "only login / select_account are whitelisted; anything else is dropped");
    }
}
