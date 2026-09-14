package org.rapla.server.spring.web;

import com.nimbusds.jose.JOSEException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.IdentityClaims;
import org.rapla.server.UserProvisioner;
import org.rapla.server.spring.RefreshSessionService;
import org.rapla.server.spring.oauth.external.ExternalIdTokenVerifier;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ExternalUserResolver;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * Identity-broker token endpoints for external OIDC providers (Microsoft Entra,
 * Google, Keycloak). Under the PRD 072 M2 model rapla <b>re-mints its own
 * access token</b> for every external login — the external IdP token is verified
 * once and then consumed, never reaching {@code /api}. {@code /api} validates a
 * single issuer (rapla's own key).
 *
 * <p>Two surfaces:
 * <ul>
 *   <li>{@link #exchange} — the BFF code-grant: the SPA forwards the standard
 *       {@code authorization_code} form body; rapla adds the server-held
 *       {@code client_secret}, calls the IdP token endpoint, verifies the
 *       returned {@code id_token}, provisions the rapla user, and returns a
 *       <b>rapla</b> token (NOT the raw IdP token — PRD 072 Phase 6). The
 *       {@code refresh_token} grant is rejected: rapla owns the session and does
 *       not relay IdP refresh tokens (#7=a).</li>
 *   <li>{@link #tokenExchange} — RFC 8693: the caller already holds a finished
 *       external {@code id_token} (identity assertion) and asks rapla to mint a
 *       rapla token for {@code /api}. No upstream code exchange — rapla verifies
 *       the id_token directly.</li>
 * </ul>
 *
 * <p>Both paths verify the external id_token via {@link ExternalIdTokenVerifier}
 * (its own JWKS trust chain + an {@code aud}-pin to rapla's client_id), NOT the
 * resource-server {@code jwtDecoder} (which after the cutover trusts only rapla
 * tokens). On verification/resolution failure both return a 401 with an
 * OAuth-style error body and never leak the failure reason or token claims.
 *
 * <p>Swing does not use this — it talks to rapla's embedded Spring
 * Authorization Server directly (PRD 036 / 072 Phase 5).
 */
@RestController
@RequestMapping(value = "/api/auth/oauth", produces = "application/json")
public class OAuthExchangeController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthExchangeController.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private final ExternalProvidersProperties externalProviders;
    private final HttpClient httpClient;
    private final ObjectProvider<ExternalIdTokenVerifier> idTokenVerifierProvider;
    private final ObjectProvider<ExternalUserResolver> externalUserResolverProvider;
    private final ObjectProvider<UserProvisioner> userProvisionerProvider;
    private final ObjectProvider<RefreshSessionService> refreshSessionServiceProvider;

    public OAuthExchangeController(ExternalProvidersProperties externalProviders,
                                   ObjectProvider<ExternalIdTokenVerifier> idTokenVerifierProvider,
                                   ObjectProvider<ExternalUserResolver> externalUserResolverProvider,
                                   ObjectProvider<UserProvisioner> userProvisionerProvider,
                                   ObjectProvider<RefreshSessionService> refreshSessionServiceProvider)
    {
        this.externalProviders = externalProviders;
        this.idTokenVerifierProvider = idTokenVerifierProvider;
        this.externalUserResolverProvider = externalUserResolverProvider;
        this.userProvisionerProvider = userProvisionerProvider;
        this.refreshSessionServiceProvider = refreshSessionServiceProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * BFF code-grant. The SPA forwards its {@code authorization_code} form body;
     * rapla adds the server-held secret, exchanges at the IdP, verifies the
     * returned {@code id_token}, provisions the user, and returns a <b>rapla</b>
     * token. {@code grant_type=refresh_token} is rejected (rapla owns the
     * session; refresh against rapla's own {@code /api/auth/session/refresh} or
     * {@code /oauth2/token}).
     */
    @PostMapping(value = "/exchange/{providerId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> exchange(@PathVariable("providerId") String providerId,
                                           @RequestBody MultiValueMap<String, String> formData)
    {
        ProviderConfig provider = externalProviders.byId(providerId).orElse(null);
        if (provider == null)
        {
            return error(HttpStatus.BAD_REQUEST, "invalid_request", "Unknown provider: " + providerId);
        }

        String grantType = formData.getFirst("grant_type");
        if ("refresh_token".equals(grantType))
        {
            // M2: rapla owns the session and does NOT relay IdP refresh tokens
            // (#7=a). A rapla token from this endpoint is refreshed against
            // rapla's own endpoints, not via the IdP.
            return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                    "rapla brokers the session; refresh a rapla token via /api/auth/session/refresh or /oauth2/token, "
                            + "not via the IdP exchange");
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
            return error(HttpStatus.BAD_GATEWAY, "server_error", "Token exchange proxy failure");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            // Surface the IdP's own error status; the body is the IdP's OAuth
            // error JSON (no rapla claims leaked).
            LOGGER.debug("IdP {} rejected the code exchange (status {})", provider.id(), response.statusCode());
            return ResponseEntity.status(response.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.body());
        }

        String idToken = extractIdToken(provider, response.body());
        if (idToken == null)
        {
            return error(HttpStatus.UNAUTHORIZED, "invalid_token",
                    "IdP response carried no id_token to broker");
        }
        return mintRaplaToken(provider, idToken);
    }

    /**
     * RFC 8693 token-exchange: external {@code id_token} → rapla token. The
     * caller already holds a finished provider id_token (identity assertion,
     * {@code aud}=rapla) and wants a rapla token valid at {@code /api}. rapla
     * verifies the id_token on the provider's own trust chain (signature + iss +
     * exp + aud-pin), provisions/resolves the rapla user, and mints a rapla
     * access + refresh token.
     *
     * <p>Input is the form param {@code id_token} (matches the form-encoded
     * shape of {@link #exchange}). On any verification/resolution failure → 401
     * with an OAuth error body; no stack/claim leakage.
     */
    @PostMapping(value = "/token-exchange/{providerId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> tokenExchange(@PathVariable("providerId") String providerId,
                                                @RequestParam("id_token") String idToken)
    {
        ProviderConfig provider = externalProviders.byId(providerId).orElse(null);
        if (provider == null)
        {
            return error(HttpStatus.BAD_REQUEST, "invalid_request", "Unknown provider: " + providerId);
        }
        return mintRaplaToken(provider, idToken);
    }

    /**
     * The shared M2 tail: verify the external {@code id_token}, provision/resolve
     * the rapla {@link User}, mint a rapla access + refresh token. Returns the
     * rapla token JSON ({@code access_token}, {@code refresh_token},
     * {@code token_type}, {@code expires_in}) on success, or a 401 OAuth error on
     * any verification/resolution/mint failure — never the external token, never
     * the failure detail.
     */
    private ResponseEntity<String> mintRaplaToken(ProviderConfig provider, String idToken)
    {
        ExternalIdTokenVerifier verifier = idTokenVerifierProvider.getIfAvailable();
        ExternalUserResolver resolver = externalUserResolverProvider.getIfAvailable();
        UserProvisioner provisioner = userProvisionerProvider.getIfAvailable();
        RefreshSessionService refreshSession = refreshSessionServiceProvider.getIfAvailable();
        if (verifier == null || resolver == null || provisioner == null || refreshSession == null)
        {
            LOGGER.warn("External IdP brokering requested but the broker beans are not wired (provider={})", provider.id());
            return error(HttpStatus.UNAUTHORIZED, "invalid_token", "External login is not enabled");
        }

        User user;
        try
        {
            Jwt jwt = verifier.verify(idToken, provider);
            IdentityClaims claims = resolver.claimsFor(jwt, provider);
            user = provisioner.provision(claims, provider.autoProvision());
        }
        catch (JwtException jwtEx)
        {
            LOGGER.warn("Rejected {} id_token (verification failed): {}", provider.id(), jwtEx.getMessage());
            return error(HttpStatus.UNAUTHORIZED, "invalid_token", "The external id_token could not be verified");
        }
        catch (RaplaException rapEx)
        {
            LOGGER.warn("Could not resolve/provision rapla user for {} login: {}", provider.id(), rapEx.getMessage());
            return error(HttpStatus.UNAUTHORIZED, "invalid_grant", "No rapla user for the external identity");
        }

        try
        {
            RefreshSessionService.IssuedTokens tokens = refreshSession.issueAndPersist(user);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(tokenJson(tokens));
        }
        catch (RaplaException | JOSEException e)
        {
            LOGGER.warn("Could not mint a rapla token for {} login: {}", provider.id(), e.getMessage());
            return error(HttpStatus.UNAUTHORIZED, "invalid_grant", "Could not establish a rapla session");
        }
    }

    /**
     * Pull the {@code id_token} (always a JWT for OIDC) from the IdP token
     * response. Falls back to a JWT-shaped {@code access_token} for providers
     * that emit JWT access tokens (Keycloak, Entra). Google's access_token is
     * opaque → only the id_token path applies. Returns null if neither is a JWT.
     */
    private static String extractIdToken(ProviderConfig provider, String responseBody)
    {
        try
        {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode idToken = root.get("id_token");
            JsonNode accessToken = root.get("access_token");
            if (idToken != null && idToken.isTextual())
            {
                return idToken.asText();
            }
            if (accessToken != null && accessToken.isTextual() && looksLikeJwt(accessToken.asText()))
            {
                return accessToken.asText();
            }
            LOGGER.debug("Exchange for {} returned no JWT token", provider.id());
            return null;
        }
        catch (Exception e)
        {
            LOGGER.warn("Could not parse IdP token response (provider={}): {}", provider.id(), e.getMessage());
            return null;
        }
    }

    private static String tokenJson(RefreshSessionService.IssuedTokens tokens)
    {
        // Both tokens are base64url JWTs (no quotes/backslashes), so direct
        // concatenation produces valid JSON.
        return "{\"access_token\":\"" + tokens.accessToken()
                + "\",\"refresh_token\":\"" + tokens.refreshToken()
                + "\",\"token_type\":\"Bearer\""
                + ",\"expires_in\":" + tokens.expiresIn() + "}";
    }

    private static ResponseEntity<String> error(HttpStatus status, String error, String description)
    {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + error + "\",\"error_description\":\"" + description + "\"}");
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
