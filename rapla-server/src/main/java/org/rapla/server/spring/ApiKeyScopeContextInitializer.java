package org.rapla.server.spring;

import jakarta.annotation.PostConstruct;
import org.rapla.server.ApiKeyScopeContext;
import org.rapla.server.ApiKeyScopes;
import org.rapla.server.spring.web.ApiKeyController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Registers the {@link ApiKeyScopeContext} scope source (PRD 076 Phase 2). The storage-layer
 * write chokepoint stays Spring-free; this Spring-tier component supplies the bridge: read the
 * current {@code SecurityContextHolder} and, when the authenticated principal is an
 * {@code typ=api_key} JWT, return its {@code scopes} claim (placed there by
 * {@code ApiKeyJwtDecoder} — authoritative from the stored entry, D8). Any other principal
 * (interactive access token, external IdP, none) ⇒ {@code null} ⇒ unrestricted.
 */
@Component
public class ApiKeyScopeContextInitializer
{
    @PostConstruct
    public void register()
    {
        ApiKeyScopeContext.setSource(ApiKeyScopeContextInitializer::currentApiKeyScopes);
    }

    public static boolean isApiKeyPrincipal()
    {
        return currentApiKeyScopes() != null;
    }

    static Set<String> currentApiKeyScopes()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth))
        {
            return null;
        }
        Jwt jwt = jwtAuth.getToken();
        if (!ApiKeyController.API_KEY_TYP.equals(jwt.getClaimAsString("typ")))
        {
            return null;
        }
        List<String> scopes = jwt.getClaimAsStringList("scopes");
        // An api_key JWT always carries scopes post-decode; a missing/empty claim resolves to the
        // least-privilege default {read} (no legacy write_all fallback — pre-scopes keys are
        // read-only; the one legacy writer, dualis, is exempted via callUnrestricted).
        return (scopes == null || scopes.isEmpty()) ? ApiKeyScopes.DEFAULT : Set.copyOf(scopes);
    }
}
