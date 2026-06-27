package org.rapla.server.spring.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wraps Spring's {@link DefaultOAuth2AuthorizationRequestResolver} to forward a
 * whitelisted {@code prompt} request parameter into the upstream IdP authorize
 * request (PRD 072 follow-up).
 *
 * <p>Motivation: in the M2 broker model rapla's logout is rapla-local — it never
 * propagates to the upstream IdP (no RP-initiated single logout is wired). Without
 * a re-prompt the next SSO login silently re-authenticates against the still-live
 * Keycloak SSO session: "sign out → instantly signed back in" as the same user,
 * with no way to pick a different account. The rapla {@code /login} page appends
 * {@code ?prompt=login} to the SSO links ONLY after an explicit logout
 * ({@code /login?logout}); this resolver forwards it as the OIDC {@code prompt}
 * authorize parameter so Keycloak re-prompts for credentials.
 *
 * <p>One-shot by construction: the parameter rides the post-logout authorize
 * request only — ordinary first-visit / token-expiry logins carry no
 * {@code prompt} and keep silent SSO (so a page refresh never forces a login).
 * Only {@code login} and {@code select_account} are forwarded; any other value
 * is ignored (don't reflect arbitrary client input into the IdP request).
 */
public class RaplaOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver
{
    /** Spring's default kickoff base URI: {@code /oauth2/authorization/{registrationId}}. */
    static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    private static final Set<String> ALLOWED_PROMPTS = Set.of("login", "select_account");

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public RaplaOAuth2AuthorizationRequestResolver(ClientRegistrationRepository repo)
    {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, AUTHORIZATION_REQUEST_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request)
    {
        return withPrompt(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId)
    {
        return withPrompt(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest withPrompt(OAuth2AuthorizationRequest req, HttpServletRequest request)
    {
        if (req == null)
        {
            return null;
        }
        String prompt = request.getParameter("prompt");
        if (prompt == null || !ALLOWED_PROMPTS.contains(prompt))
        {
            return req;
        }
        Map<String, Object> params = new HashMap<>(req.getAdditionalParameters());
        params.put("prompt", prompt);
        return OAuth2AuthorizationRequest.from(req).additionalParameters(params).build();
    }
}
