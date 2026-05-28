package org.rapla.server.spring.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.framework.RaplaException;
import org.rapla.server.IdentityClaims;
import org.rapla.server.UserProvisioner;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ExternalUserResolver;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BFF (backend-for-frontend) token-exchange proxy for external OIDC providers
 * (Microsoft Entra, Google, Keycloak). Receives the standard OAuth form body
 * from the Angular SPA after its IdP redirect, adds the server-held
 * {@code client_secret} for providers that require it, and forwards to the
 * real IdP token endpoint.
 *
 * <p><b>Why a BFF?</b> Google's "Web application" OAuth client demands a
 * {@code client_secret} on the token endpoint even when PKCE is in play.
 * Sending that secret to the SPA would defeat the purpose of calling it
 * secret. Routing the token exchange through this endpoint keeps the secret
 * server-side; the SPA holds only the public {@code client_id} and the PKCE
 * verifier (which is per-flow ephemeral). Same endpoint handles
 * {@code grant_type=authorization_code} (initial exchange) and
 * {@code grant_type=refresh_token} (subsequent refreshes).
 *
 * <p><b>PRD 050 Phase 7 — at-login provisioning seam.</b> On every successful
 * IdP response, decode the returned {@code id_token} (or {@code access_token}
 * fallback) via the configured {@link JwtDecoder}, translate to
 * {@link IdentityClaims} via {@link ExternalUserResolver#claimsFor}, and hand
 * to {@link UserProvisioner#provision}. Runs once per code-grant + once per
 * refresh-grant. The resource-server filter path no longer mutates User —
 * this is the single write seam for OIDC-authenticated users (AGENTS.md §16).
 *
 * <p>If provisioning fails (hosted-domain rejection, claims invalid, storage
 * error), we still return the IdP token to the SPA. The first authenticated
 * call from the SPA will then 401 with the same reason from
 * {@link ExternalUserResolver#resolve}, producing the SPA's "Sign-in rejected"
 * dialog. Consistent UX with the previous behaviour, but localised to a
 * single seam.
 *
 * <p>Swing does not use this — it talks to rapla's embedded Spring
 * Authorization Server directly (PRD 036).
 */
@RestController
@RequestMapping(value = "/api/auth/oauth", produces = "application/json")
public class OAuthExchangeController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthExchangeController.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private final ExternalProvidersProperties externalProviders;
    private final HttpClient httpClient;
    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final ObjectProvider<ExternalUserResolver> externalUserResolverProvider;
    private final ObjectProvider<UserProvisioner> userProvisionerProvider;

    public OAuthExchangeController(ExternalProvidersProperties externalProviders,
                                   ObjectProvider<JwtDecoder> jwtDecoderProvider,
                                   ObjectProvider<ExternalUserResolver> externalUserResolverProvider,
                                   ObjectProvider<UserProvisioner> userProvisionerProvider)
    {
        this.externalProviders = externalProviders;
        this.jwtDecoderProvider = jwtDecoderProvider;
        this.externalUserResolverProvider = externalUserResolverProvider;
        this.userProvisionerProvider = userProvisionerProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @PostMapping(value = "/exchange/{providerId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> exchange(@PathVariable("providerId") String providerId,
                                           @RequestBody MultiValueMap<String, String> formData)
    {
        ProviderConfig provider = externalProviders.byId(providerId).orElse(null);
        if (provider == null)
        {
            return ResponseEntity.badRequest()
                    .body("{\"error\":\"invalid_request\",\"error_description\":\"Unknown provider: " + providerId + "\"}");
        }

        // Build outgoing form: copy received params + add client_secret if configured.
        // We keep client_id (the SPA sent it) but override/inject client_secret
        // server-side — never trust the SPA to supply a real secret.
        MultiValueMap<String, String> outgoing = new LinkedMultiValueMap<>(formData);
        if (!provider.clientSecret().isEmpty())
        {
            outgoing.set("client_secret", provider.clientSecret());
        }
        String body = urlEncode(outgoing);

        HttpResponse<String> response;
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(provider.tokenUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception e)
        {
            LOGGER.warn("BFF token exchange to {} ({}) failed: {}", provider.id(), provider.tokenUrl(), e.getMessage());
            return ResponseEntity.status(502)
                    .body("{\"error\":\"server_error\",\"error_description\":\"Token exchange proxy failure\"}");
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300)
        {
            provisionFromResponse(provider, response.body());
        }

        return ResponseEntity.status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    /**
     * PRD 050 Phase 7 — once per token issuance: decode the returned token,
     * translate to {@link IdentityClaims}, hand to {@link UserProvisioner}.
     * Failures are logged and swallowed — the SPA still receives the IdP
     * token, and the first authenticated API call will surface the same
     * reason via {@link ExternalUserResolver#resolve}.
     */
    private void provisionFromResponse(ProviderConfig provider, String responseBody)
    {
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        ExternalUserResolver resolver = externalUserResolverProvider.getIfAvailable();
        UserProvisioner provisioner = userProvisionerProvider.getIfAvailable();
        if (decoder == null || resolver == null || provisioner == null)
        {
            // No external IdP wired (single-tenant rapla-SAS only) → nothing
            // to provision from this endpoint. Defensive; in production these
            // beans always exist when the controller is loaded.
            return;
        }

        String token;
        try
        {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode idToken = root.get("id_token");
            JsonNode accessToken = root.get("access_token");
            // Prefer id_token (always a JWT for OIDC). Fall back to access_token
            // for providers that emit JWT access tokens (Keycloak, Entra).
            // Google's access_token is opaque — provisioning won't run for Google
            // unless 'openid' scope was requested (giving us an id_token).
            if (idToken != null && idToken.isTextual())
            {
                token = idToken.asText();
            }
            else if (accessToken != null && accessToken.isTextual() && looksLikeJwt(accessToken.asText()))
            {
                token = accessToken.asText();
            }
            else
            {
                LOGGER.debug("Exchange for {} returned no JWT token; skipping provisioning", provider.id());
                return;
            }
        }
        catch (Exception e)
        {
            LOGGER.warn("Could not parse IdP token response for provisioning (provider={}): {}", provider.id(), e.getMessage());
            return;
        }

        try
        {
            Jwt jwt = decoder.decode(token);
            IdentityClaims claims = resolver.claimsFor(jwt, provider);
            provisioner.provision(claims);
        }
        catch (JwtException jwtEx)
        {
            LOGGER.warn("Could not decode IdP token for provisioning (provider={}): {}", provider.id(), jwtEx.getMessage());
        }
        catch (RaplaException rapEx)
        {
            LOGGER.warn("Provisioning failed for {} login: {}", provider.id(), rapEx.getMessage());
        }
    }

    private static boolean looksLikeJwt(String token)
    {
        // Three dot-separated base64url segments — cheap rule-out for opaque
        // tokens (Google access tokens, refresh tokens, etc.).
        int firstDot = token.indexOf('.');
        if (firstDot < 0) return false;
        int secondDot = token.indexOf('.', firstDot + 1);
        return secondDot > firstDot;
    }

    private static String urlEncode(MultiValueMap<String, String> form)
    {
        return form.entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v -> Map.entry(e.getKey(), v == null ? "" : v)))
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
