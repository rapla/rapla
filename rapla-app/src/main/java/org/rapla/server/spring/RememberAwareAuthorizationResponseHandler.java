package org.rapla.server.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.server.util.LoopbackUriCheck;
import org.rapla.server.util.WslBridgeUriCheck;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * PRD 131 R4/D6 — the authorization response of {@code /oauth2/authorize}: Spring's default redirect
 * ({@code code}, {@code state}) plus, for the Swing client's loopback listener, the stay-signed-in
 * choice the user made on {@code /login} ({@code remember=1|0}, from the {@code rapla-remember-choice}
 * cookie of this request). Swing feeds it to its token store instead of asking the user again.
 */
final class RememberAwareAuthorizationResponseHandler implements AuthenticationSuccessHandler
{
    static final String REMEMBER_PARAM = "remember";

    private final List<String> callbackPaths;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    RememberAwareAuthorizationResponseHandler(List<String> callbackPaths)
    {
        this.callbackPaths = callbackPaths;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException
    {
        OAuth2AuthorizationCodeRequestAuthenticationToken code = (OAuth2AuthorizationCodeRequestAuthenticationToken) authentication;
        String redirectUri = code.getRedirectUri();
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam(OAuth2ParameterNames.CODE, code.getAuthorizationCode().getTokenValue());
        if (StringUtils.hasText(code.getState()))
        {
            uri.queryParam(OAuth2ParameterNames.STATE, UriUtils.encode(code.getState(), StandardCharsets.UTF_8));
        }
        if (LoopbackUriCheck.isLoopbackRedirect(redirectUri, callbackPaths)
                || WslBridgeUriCheck.isWslBridgeRedirect(redirectUri, callbackPaths))
        {
            uri.queryParam(REMEMBER_PARAM, CookieAuthSupport.isRemembered(request) ? "1" : "0");
        }
        redirectStrategy.sendRedirect(request, response, uri.build(true).toUriString());
    }
}
