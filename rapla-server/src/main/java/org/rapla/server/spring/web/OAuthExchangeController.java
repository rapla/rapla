package org.rapla.server.spring.web;

import org.rapla.logger.Logger;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * (Microsoft Entra, Google). Receives the standard OAuth form body from the
 * Angular SPA after its IdP redirect, adds the server-held {@code client_secret}
 * for providers that require it, and forwards to the real IdP token endpoint.
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
 * <p>Swing does not use this — it talks to rapla's embedded Spring
 * Authorization Server directly (PRD 036).
 */
@RestController
@RequestMapping(value = "/api/auth/oauth", produces = "application/json")
public class OAuthExchangeController
{
    private final ExternalProvidersProperties externalProviders;
    private final Logger logger;
    private final HttpClient httpClient;

    public OAuthExchangeController(ExternalProvidersProperties externalProviders, Logger logger)
    {
        this.externalProviders = externalProviders;
        this.logger = logger;
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

        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(provider.tokenUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.body());
        }
        catch (Exception e)
        {
            logger.warn("BFF token exchange to " + provider.id() + " (" + provider.tokenUrl()
                    + ") failed: " + e.getMessage());
            return ResponseEntity.status(502)
                    .body("{\"error\":\"server_error\",\"error_description\":\"Token exchange proxy failure\"}");
        }
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
