package org.rapla.server.spring;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.internal.RaplaAuthentificationService;
import org.rapla.server.spring.oauth.RaplaOauthRedirectProperties;
import org.rapla.server.util.LoopbackUriCheck;
import org.rapla.server.util.SameOriginUriCheck;
import org.rapla.server.util.WslBridgeUriCheck;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Minimal proof-of-concept: Spring Authorization Server with code+PKCE,
 * one in-memory user (admin/admin), and one registered public client
 * (swagger-ui) declared via application.yml. Tokens signed by an
 * in-memory RSA keypair regenerated each startup.
 *
 * What this contains:
 *   - Order(1) filter chain for the OAuth2/OIDC endpoints (/oauth2/authorize,
 *     /oauth2/token, /oauth2/jwks, /.well-known/*). Built manually because
 *     OAuth2AuthorizationServerAutoConfiguration is @ConditionalOnDefaultWebSecurity —
 *     it backs off the moment any custom SecurityFilterChain exists (Rapla has one).
 *   - JWKSource: in-memory RSA keypair for signing.
 *   - JwtDecoder: validates tokens against the same JWK set.
 *   - UserDetailsService: in-memory admin/admin for login form.
 *
 * Production hardening still needed (see PRD 026 §5):
 *   - Persistent JWK set (KMS / file) so tokens survive restart
 *   - Bridge UserDetailsService to Rapla's RaplaFacade.getUser()
 *   - Custom login page (replace Spring's default form)
 *   - Issuer URI configured to deployment URL
 */
@Configuration
@EnableConfigurationProperties(RaplaOauthRedirectProperties.class)
public class AuthorizationServerConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerConfig.class);

    private final RaplaOauthRedirectProperties redirectProps;

    public AuthorizationServerConfig(RaplaOauthRedirectProperties redirectProps)
    {
        this.redirectProps = redirectProps;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      RememberMeServices rememberMeServices,
                                                                      OAuth2TokenGenerator<?> tokenGenerator,
                                                                      RegisteredClientRepository clientRepository,
                                                                      RefreshSessionService refreshSessionService,
                                                                      RaplaAuthentificationService raplaAuthService,
                                                                      RaplaFacade facade,
                                                                      org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder) throws Exception
    {
        OAuth2AuthorizationServerConfigurer authServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = authServerConfigurer.getEndpointsMatcher();

        authServerConfigurer.authorizationEndpoint(authorizationEndpoint ->
                authorizationEndpoint.authenticationProviders(providers -> providers.forEach(provider -> {
                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeRequestProvider)
                    {
                        codeRequestProvider.setAuthenticationValidator(redirectUriAndScopeValidator(
                                redirectProps.isAllowLoopbackRedirects(),
                                redirectProps.isAllowWslBridgeRedirects(),
                                redirectProps.isAllowSameOriginRedirects(),
                                redirectProps.getSameOriginCallbackPaths()));
                    }
                })));

        // Only redirect to the form-login page when the client EXPLICITLY accepts
        // text/html — i.e. an actual browser navigation to /oauth2/authorize.
        // Without setUseEquals + ignoring MediaType.ALL, `Accept: */*` (the
        // default for fetch / XMLHttpRequest / curl) matches "compatible with"
        // text/html, and programmatic POSTs to /oauth2/token would be 302'd to
        // /login instead of getting the proper OAuth JSON error. That broke the
        // angular-oauth2-oidc library's code-exchange round-trip — see PRD 026
        // notes on the "404 on /oauth2/token" symptom.
        MediaTypeRequestMatcher htmlMatcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        htmlMatcher.setUseEquals(true);
        htmlMatcher.setIgnoredMediaTypes(Collections.singleton(MediaType.ALL));

        http
                .securityMatcher(endpointsMatcher)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                // CORS for cross-origin SPA → OAuth endpoint calls. In dev the
                // SPA at :4200 POSTs /oauth2/token to :8051 direct (the proxy
                // explicitly does NOT forward OAuth paths — proxy.conf.js).
                // Mirrors what would happen with an external Keycloak: SPA →
                // separate IdP origin, CORS required. SecurityConfig already
                // provides a CorsConfigurationSource bean that allows *.
                .cors(Customizer.withDefaults())
                .with(authServerConfigurer, c -> {
                    // PRD 041 — wire custom token generator (issues JWT refresh tokens with
                    // typ=refresh, persists in user prefs via RefreshSessionService); custom
                    // client auth converter + provider so PUBLIC clients can authenticate on
                    // refresh_token AND password grants (Spring AS's stock
                    // PublicClientAuthenticationConverter only handles PKCE); custom
                    // refresh-token + password auth providers so issuance + validation goes
                    // through RefreshSessionService. Result: /oauth2/token is the single
                    // endpoint for all grants (code, refresh, password); tokens are
                    // interchangeable across endpoints.
                    c.tokenGenerator(tokenGenerator);
                    c.clientAuthentication(client -> {
                        client.authenticationConverter(new PublicClientRefreshTokenAuthenticationConverter());
                        client.authenticationProvider(new PublicClientRefreshTokenAuthenticationProvider(clientRepository));
                    });
                    c.tokenEndpoint(token -> {
                        token.accessTokenRequestConverter(new PasswordGrantAuthenticationConverter());
                        token.authenticationProvider(new RaplaRefreshTokenAuthenticationProvider(refreshSessionService));
                        token.authenticationProvider(new PasswordGrantAuthenticationProvider(refreshSessionService, raplaAuthService));
                    });
                    c.tokenRevocationEndpoint(revoke ->
                            revoke.authenticationProvider(new RaplaTokenRevocationAuthenticationProvider(refreshSessionService, facade, jwtDecoder)));
                    c.oidc(oidc -> oidc.logoutEndpoint(logout -> {
                    logout.authenticationProviders(providers -> providers.forEach(provider -> {
                        if (provider instanceof OidcLogoutAuthenticationProvider logoutProvider)
                        {
                            logoutProvider.setAuthenticationValidator(permissivePostLogoutRedirectUriValidator());
                        }
                    }));
                    // Spring SAS's default OidcLogoutAuthenticationSuccessHandler only
                    // clears the HttpSession + SecurityContext. It does NOT consult
                    // RememberMeServices, so the rapla-remember-me cookie survives a
                    // /connect/logout — and Spring's RememberMeAuthenticationFilter then
                    // silently re-authenticates the next /oauth2/authorize ("sign out →
                    // instantly signed back in" bug). Wrapping the success handler with
                    // a CompositeLogoutHandler that includes the bean-promoted
                    // RememberMeServices closes the gap: cookie cleared + persistent
                    // token removed in one shot.
                    OidcLogoutAuthenticationSuccessHandler successHandler = new OidcLogoutAuthenticationSuccessHandler();
                    successHandler.setLogoutHandler(new CompositeLogoutHandler(
                            new SecurityContextLogoutHandler(),
                            (LogoutHandler) rememberMeServices));
                    logout.logoutResponseHandler(successHandler);
                }));
                })
                .exceptionHandling(exc -> exc.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        htmlMatcher));
        return http.build();
    }

    /**
     * Wraps Spring's default redirect-URI + scope validation with three
     * fallback allowances. Each fires only when the default validator rejects
     * the URI; ordering is from broadest (any-path loopback) to narrowest
     * (path allowlist):
     * <ol>
     *   <li><b>Loopback any-port</b> — accept any URI on 127.0.0.1 or [::1]
     *       at any port, provided the path is in
     *       {@code rapla.oauth.same-origin-callback-paths}. Local processes
     *       binding loopback are trusted to bind any port; the path allowlist
     *       remains the consistent security gate across all three validators.
     *       Toggle via {@code rapla.oauth.allow-loopback-redirects=false}.</li>
     *   <li><b>WSL bridge</b> — accept any port for hosts in 172.16.0.0/12
     *       (Hyper-V WSL2 bridge), provided the path is in the same-origin
     *       callback-paths allowlist. Lets a developer run the Swing client
     *       in WSL2 without enabling mirrored networking. Toggle via
     *       {@code rapla.oauth.allow-wsl-bridge-redirects=false}.</li>
     *   <li><b>Same-origin</b> — accept any redirect URI whose scheme/host/port
     *       match the auth-server request's public origin (honoring
     *       X-Forwarded-*), provided the path is in
     *       {@code rapla.oauth.same-origin-callback-paths}. Zero-config
     *       Angular SPA deployment: the registered {@code /app/auth/callback}
     *       path is accepted at whatever public hostname rapla is serving on.
     *       Toggle via {@code rapla.oauth.allow-same-origin-redirects=false}.</li>
     * </ol>
     *
     * <p>This is a deliberate deviation from RFC 9700 (OAuth 2.0 Security BCP)
     * which mandates exact string matching. The relaxation is bounded by
     * mandatory PKCE ({@code require-proof-key: true}) and by trust in the
     * reverse-proxy's X-Forwarded-* hygiene. See {@code docs/authentication.md}
     * for the full rationale.
     */
    private static Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> redirectUriAndScopeValidator(
            boolean allowLoopback,
            boolean allowWslBridge,
            boolean allowSameOrigin,
            List<String> sameOriginCallbackPaths)
    {
        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> redirectValidator =
                OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR;
        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> scopeValidator =
                OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR;

        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> withAllowances = ctx -> {
            try
            {
                redirectValidator.accept(ctx);
            }
            catch (OAuth2AuthorizationCodeRequestAuthenticationException ex)
            {
                // Spring's default validator throws OAuth2ErrorCodes.INVALID_REQUEST
                // (general code) for redirect URI mismatches, not a dedicated
                // "invalid_redirect_uri". Filtering by error code is brittle — we
                // just attempt our fallback checks unconditionally. They themselves
                // are narrow (loopback host literals, WSL subnet + path allowlist,
                // or same-origin + path allowlist) so a request that doesn't match
                // any still re-throws.
                if (allowLoopback && isLoopbackRedirect(ctx, sameOriginCallbackPaths)) return;
                if (allowWslBridge && isWslBridgeRedirect(ctx, sameOriginCallbackPaths)) return;
                if (allowSameOrigin && isSameOriginRedirect(ctx, sameOriginCallbackPaths)) return;
                throw ex;
            }
        };
        return withAllowances.andThen(scopeValidator);
    }

    /**
     * Replaces Spring AS's default post-logout-redirect-uri validator
     * (which does exact {@code Set.contains()} match against the registered
     * client's {@code postLogoutRedirectUris}) with a permissive accept-any
     * validator. Why this is safe:
     * <ul>
     *   <li>Logout terminates credentials; it doesn't issue any. The worst a
     *       malicious redirect could do is land the user on an unexpected
     *       page after they're already signed out.</li>
     *   <li>The {@code id_token_hint} parameter is still validated by
     *       {@link OidcLogoutAuthenticationProvider} — only a token issued
     *       by this AS for this client gets past, so a third-party site
     *       can't trigger logouts without already holding a valid token.</li>
     * </ul>
     * Enumerating dev + prod variants of {@code /app/} in
     * {@code application.yml} would mirror the redirect-uris block and rot
     * the same way; this validator eliminates that maintenance.
     */
    private static Consumer<OidcLogoutAuthenticationContext> permissivePostLogoutRedirectUriValidator()
    {
        return ctx -> { /* accept any post_logout_redirect_uri */ };
    }

    private static boolean isLoopbackRedirect(OAuth2AuthorizationCodeRequestAuthenticationContext ctx,
                                               List<String> allowedPaths)
    {
        OAuth2AuthorizationCodeRequestAuthenticationToken auth = ctx.getAuthentication();
        String redirectUri = auth == null ? null : auth.getRedirectUri();
        return LoopbackUriCheck.isLoopbackRedirect(redirectUri, allowedPaths);
    }

    private static boolean isWslBridgeRedirect(OAuth2AuthorizationCodeRequestAuthenticationContext ctx,
                                                List<String> allowedPaths)
    {
        OAuth2AuthorizationCodeRequestAuthenticationToken auth = ctx.getAuthentication();
        String redirectUri = auth == null ? null : auth.getRedirectUri();
        return WslBridgeUriCheck.isWslBridgeRedirect(redirectUri, allowedPaths);
    }

    private static boolean isSameOriginRedirect(OAuth2AuthorizationCodeRequestAuthenticationContext ctx,
                                                 List<String> allowedPaths)
    {
        OAuth2AuthorizationCodeRequestAuthenticationToken auth = ctx.getAuthentication();
        String redirectUri = auth == null ? null : auth.getRedirectUri();
        RegisteredClient client = ctx.getRegisteredClient();
        if (client == null) return false;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return false;
        HttpServletRequest request = attrs.getRequest();
        String scheme = headerOr(request, "X-Forwarded-Proto", request.getScheme());
        String host;
        int port;
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isEmpty())
        {
            // X-Forwarded-Host can be "rapla.uni.de" or "rapla.uni.de:8080" or
            // a comma-separated chain "rapla.uni.de, internal.lb:80". Take the
            // first hop, which is the public-facing one.
            String first = forwardedHost.split(",")[0].trim();
            int colon = first.indexOf(':');
            if (colon >= 0)
            {
                host = first.substring(0, colon);
                try { port = Integer.parseInt(first.substring(colon + 1).trim()); }
                catch (NumberFormatException e) { port = defaultPort(scheme); }
            }
            else
            {
                host = first;
                String fp = request.getHeader("X-Forwarded-Port");
                int parsed = -1;
                if (fp != null && !fp.isEmpty())
                {
                    try { parsed = Integer.parseInt(fp.split(",")[0].trim()); }
                    catch (NumberFormatException ignore) { /* fall through */ }
                }
                port = parsed > 0 ? parsed : defaultPort(scheme);
            }
        }
        else
        {
            host = request.getServerName();
            port = request.getServerPort();
        }
        return SameOriginUriCheck.isSameOriginRedirect(redirectUri, scheme, host, port, allowedPaths);
    }

    private static String headerOr(HttpServletRequest request, String header, String fallback)
    {
        String v = request.getHeader(header);
        if (v == null || v.isEmpty()) return fallback;
        // X-Forwarded-* may be a chain; first hop is canonical
        return v.split(",")[0].trim();
    }

    private static int defaultPort(String scheme)
    {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    /**
     * Builds the JWK set from the persistent RSA keypair stored in
     * {@link RaplaKeyStorage} (rapla system preferences → data file). The
     * keypair is generated on the first server start and reused on every
     * subsequent start, so JWTs issued at this key survive server restarts.
     * Same key, same kid → consistent identity for the auth server.
     *
     * <p>Replaces the previous in-memory keypair regenerated each startup
     * (which invalidated every issued token on restart). Pre-Option-A this
     * was actually the property the legacy {@code /auth/login} HMAC path
     * had via {@code RaplaKeyStorage} — Option A regressed it by repointing
     * at the in-memory keypair, and this restores it for both paths.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(RaplaKeyStorage keyStorage)
    {
        try
        {
            RSAPrivateKey priv = decodePrivateKey(keyStorage.getRootKeyBase64());
            if (!(priv instanceof RSAPrivateCrtKey crt))
            {
                throw new IllegalStateException("RaplaKeyStorage private key is not RSAPrivateCrtKey; cannot derive public key");
            }
            RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            RSAKey rsaKey = new RSAKey.Builder(pub)
                    .privateKey(priv)
                    .keyID(stableKeyId(pub))
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to build JWK set from RaplaKeyStorage", e);
        }
    }

    /**
     * Single {@link OAuth2TokenCustomizer} that Spring Authorization
     * Server invokes immediately before signing every JWT it produces
     * — access tokens, id tokens, and (JWT-shaped) refresh tokens.
     * Spring AS allows only one bean of this type in the context, so
     * this is where ALL of rapla's per-token-type customizations live,
     * branched on {@code ctx.getTokenType()}:
     *
     * <ul>
     *   <li><b>Refresh tokens:</b> add {@code typ=refresh}. PRD 041 —
     *       lets the stateless refresh path distinguish refresh JWTs
     *       from access JWTs without consulting SAS's in-memory
     *       authorization state.</li>
     *   <li><b>Access + ID tokens:</b> resolve the principal UUID to a
     *       rapla {@link org.rapla.entities.User} and inject
     *       {@code preferred_username} (OIDC standard claim for a
     *       mutable display name) and {@code name}. PRD 051 — the SPA's
     *       toolbar reads these for the user chip; cross-issuer
     *       symmetry with Keycloak/Entra/Google tokens which already
     *       carry the same claims; audit consistency with impersonation
     *       tokens (which set their own {@code username} claim
     *       directly).</li>
     * </ul>
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(RaplaFacade facade)
    {
        return ctx -> {
            if (OAuth2TokenType.REFRESH_TOKEN.equals(ctx.getTokenType()))
            {
                ctx.getClaims().claim("typ", "refresh");
                return;
            }
            // Access + id token path: add preferred_username + name.
            String tokenTypeValue = ctx.getTokenType() == null ? null : ctx.getTokenType().getValue();
            boolean isAccessOrId = "id_token".equals(tokenTypeValue)
                    || OAuth2TokenType.ACCESS_TOKEN.equals(ctx.getTokenType());
            if (!isAccessOrId) return;
            String subject = ctx.getPrincipal() != null ? ctx.getPrincipal().getName() : null;
            if (subject == null || subject.isEmpty()) return;
            try
            {
                org.rapla.entities.User user = facade.getOperator().tryResolve(
                        subject, org.rapla.entities.User.class);
                if (user != null)
                {
                    ctx.getClaims().claim("preferred_username", user.getUsername());
                    if (user.getName() != null && !user.getName().isEmpty())
                    {
                        ctx.getClaims().claim("name", user.getName());
                    }
                }
            }
            catch (RuntimeException ignored)
            {
                // Operator lookup may throw if the subject isn't a UUID
                // (e.g. legacy code paths) — leave claims untouched.
            }
        };
    }

    private static RSAPrivateKey decodePrivateKey(String base64) throws Exception
    {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException e) { bytes = Base64.getUrlDecoder().decode(base64); }
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    /**
     * Derives a stable JWK key-id from the public key modulus so the
     * {@code kid} header on issued tokens is consistent across restarts.
     * Hash truncation matches Spring's typical kid length without disclosing
     * the full modulus.
     */
    private static String stableKeyId(RSAPublicKey pub) throws Exception
    {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(pub.getModulus().toByteArray());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 16));
    }

    // No JwtDecoder bean here: JwtConfig already provides one for the resource
    // server (HMAC, validating /auth/login tokens). Auth-server-issued RSA
    // tokens won't yet validate against the resource server until the two
    // signing schemes are unified — POC limitation, see PRD 026 §5.

    /**
     * Bridges Spring Security's form-login to Rapla's existing authentication.
     * Returns an Authentication whose principal name is the Rapla user UUID
     * (matching what /auth/login uses as JWT subject) so resource-server
     * controllers find the same user regardless of how the token was issued.
     *
     * Side effect: Rapla's password rules apply (including empty password
     * for the dev admin), not Spring's DelegatingPasswordEncoder rules.
     */
    @Bean
    public AuthenticationProvider raplaAuthenticationProvider(RaplaAuthentificationService authService)
    {
        return new AuthenticationProvider()
        {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException
            {
                String username = authentication.getName();
                String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
                // Spring delivers the password as a String already (request-scope,
                // short-lived). Cast to char[] for the LoginCredentials contract;
                // the String becomes GC-eligible at end of this request.
                LoginCredentials credentials = new LoginCredentials(username, password.toCharArray());
                try
                {
                    User user = authService.getUserFromCredentials(credentials);
                    if (user == null)
                    {
                        throw new BadCredentialsException("invalid credentials");
                    }
                    // FactorGrantedAuthority carries the authentication timestamp that
                    // Spring Authorization Server's JwtGenerator needs to populate the
                    // auth_time claim on OIDC ID tokens (scope=openid). Without it,
                    // token issuance fails with "authenticationTime cannot be null".
                    List<GrantedAuthority> authorities = Arrays.asList(
                            FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
                            new SimpleGrantedAuthority(user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")
                    );
                    UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                            user.getId(),
                            null,
                            authorities
                    );
                    token.setDetails(authentication.getDetails());
                    return token;
                }
                catch (BadCredentialsException e)
                {
                    throw e;
                }
                catch (RaplaSecurityException e)
                {
                    // Genuine auth failure surfaced by rapla (wrong password,
                    // disabled user, AuthenticationStore returned false). Map to
                    // Spring's "bad credentials" so OAuth2 surfaces invalid_grant.
                    throw new BadCredentialsException(e.getMessage(), e);
                }
                catch (Exception e)
                {
                    // Internal failure on the server AFTER credentials were
                    // already accepted (e.g. storeAndRemove rolled back on a DB
                    // integrity violation while stamping authenticationSource).
                    // BadCredentialsException would mislead the user into
                    // re-typing a valid password — surface as Spring's
                    // InternalAuthenticationServiceException so OAuth2 maps it to
                    // server_error and log the actual cause prominently.
                    LOGGER.error("Authentication for user '{}' failed due to a server-side error after credentials were accepted: {}",
                            username, e.getMessage(), e);
                    throw new InternalAuthenticationServiceException(
                            "Authentication for '" + username + "' failed on the server (not a credentials problem): "
                                    + e.getMessage(), e);
                }
            }

            @Override
            public boolean supports(Class<?> authenticationClass)
            {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authenticationClass);
            }
        };
    }

    /**
     * Looks up Rapla users for Spring Security's remember-me cookie validation.
     * The form-login flow stores {@code user.getId()} (a UUID) as the
     * authentication principal name — that's what gets persisted in the
     * remember-me token store and arrives back as the "username" argument here.
     * We try UUID resolution first and fall back to the human-readable username
     * for any path that authenticates by name (e.g. direct DaoAuthenticationProvider
     * usage, future API-key flows).
     *
     * The returned {@link UserDetails#getPassword()} is intentionally a placeholder:
     * remember-me uses {@link org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices},
     * which validates cookies via the server-side token repository rather than
     * recomputing a password-derived hash. Rapla's {@code operator.authenticate()}
     * is opaque (could be a local hash, LDAP, or an external AuthenticationStore),
     * so we have no encoded password to expose here.
     */
    @Bean
    public UserDetailsService raplaUserDetailsService(RaplaFacade facade)
    {
        return name -> {
            User user;
            try
            {
                user = facade.getOperator().tryResolve(name, User.class);
                if (user == null)
                {
                    user = facade.getUser(name);
                }
            }
            catch (RaplaException e)
            {
                throw new UsernameNotFoundException(name, e);
            }
            if (user == null)
            {
                throw new UsernameNotFoundException(name);
            }
            String role = user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
            // Include a FactorGrantedAuthority alongside the role so OIDC ID-token
            // issuance (JwtGenerator.getAuthenticationTime) can populate the auth_time
            // claim. Remember-me-restored Authentications would otherwise carry only
            // the SimpleGrantedAuthority for ROLE_*, and Spring AS would throw
            // "authenticationTime cannot be null" at /oauth2/token.
            //
            // issuedAt defaults to Instant.now() on .build(). That's an over-estimate
            // (real auth happened when the remember-me cookie was issued, possibly
            // weeks ago) but matches OIDC's intent of "the time the user authenticated
            // for this session" — the cookie presentation is a re-authentication
            // event in Spring's model.
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getId())
                    .password("N/A")
                    .authorities(
                            FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
                            new SimpleGrantedAuthority(role))
                    .build();
        };
    }

    /**
     * PRD 041 — token generator chain that issues JWT refresh tokens (signed
     * by the persistent RSA key) for PUBLIC clients (PKCE / NONE auth method).
     * Spring AS's stock {@code OAuth2RefreshTokenGenerator}:
     * <ul>
     *   <li>Issues opaque random tokens (not JWTs)</li>
     *   <li>Suppresses for public clients on authorization_code grant</li>
     * </ul>
     * Both don't fit rapla's design — see {@link JwtRefreshTokenGenerator}'s
     * Javadoc + PRD 031.
     */
    @Bean
    public OAuth2TokenGenerator<OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource,
                                                            OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer,
                                                            RefreshSessionService refreshSessionService,
                                                            RaplaFacade facade)
    {
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                accessTokenGenerator,
                new JwtRefreshTokenGenerator(refreshSessionService, facade));
    }

    /**
     * Issues JWT refresh tokens via {@link RefreshSessionService}.
     * <ul>
     *   <li>Signed by the persistent RSA key (same {@code JwtIssuer} the
     *       {@code /oauth2/token} password grant uses) → tokens survive server restart.</li>
     *   <li>Carries {@code typ=refresh} claim → distinguishable from access tokens.</li>
     *   <li>Hash persisted to user prefs ({@code RefreshSessionService.SESSION})
     *       → single-token-per-user revocation, restart-safe, rotate-when-stale.</li>
     *   <li>Issued for ALL clients including public ones (PKCE/NONE) — Spring AS's
     *       default suppression for public clients is overridden here. Trade-off
     *       documented in PRD 041.</li>
     * </ul>
     * Tokens issued here are interchangeable with those issued by the
     * {@code /oauth2/token} password grant — same JWT format, same
     * hash store, redeemable at the same {@code /oauth2/token} refresh endpoint.
     */
    private static final class JwtRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken>
    {
        private final RefreshSessionService refreshSessionService;
        private final RaplaFacade facade;

        JwtRefreshTokenGenerator(RefreshSessionService refreshSessionService, RaplaFacade facade)
        {
            this.refreshSessionService = refreshSessionService;
            this.facade = facade;
        }

        @Override
        public OAuth2RefreshToken generate(OAuth2TokenContext context)
        {
            if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) return null;
            String userId = context.getPrincipal().getName();
            try
            {
                User user = facade.getOperator().tryResolve(userId, User.class);
                if (user == null)
                {
                    throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                            OAuth2ErrorCodes.INVALID_GRANT,
                            "Cannot resolve user for refresh-token issuance: " + userId, null));
                }
                String jwt = refreshSessionService.issueAndPersistRefreshToken(user);
                Instant now = Instant.now();
                return new OAuth2RefreshToken(jwt, now,
                        now.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive()));
            }
            catch (Exception e)
            {
                if (e instanceof OAuth2AuthenticationException oae) throw oae;
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.SERVER_ERROR,
                        "Failed to issue refresh token: " + e.getMessage(), null));
            }
        }
    }

    /**
     * Authenticates public clients on requests that carry just {@code client_id}
     * (no {@code client_secret}, no PKCE {@code code_verifier}). Spring AS's
     * stock {@code PublicClientAuthenticationConverter} only matches PKCE token
     * requests (authorization_code with code_verifier); other public-client
     * requests — refresh_token grant, password grant, /oauth2/revoke, /oauth2/introspect —
     * have no built-in converter, so {@code OAuth2ClientAuthenticationFilter}
     * returns 401 before the endpoint logic runs.
     *
     * <p>Match rules — return {@code null} (defer to other converters) unless ALL of:
     * <ul>
     *   <li>{@code client_id} present</li>
     *   <li>NO {@code client_secret} (defer confidential clients to ClientSecret*Converter)</li>
     *   <li>NO {@code code_verifier} (defer PKCE flows to the stock PublicClientAuthenticationConverter)</li>
     * </ul>
     * Method/URL not checked — deferring is safe because Spring AS's other
     * converters take precedence when their conditions match.
     */
    private static final class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter
    {
        @Override
        public Authentication convert(HttpServletRequest request)
        {
            String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
            if (!StringUtils.hasText(clientId)) return null;
            if (request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null) return null;
            if (request.getParameter("code_verifier") != null) return null;
            return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, null);
        }
    }

    /**
     * Companion to {@link PublicClientRefreshTokenAuthenticationConverter} —
     * validates the unauthenticated {@link OAuth2ClientAuthenticationToken} it
     * produces. The stock {@code PublicClientAuthenticationProvider} hard-requires
     * {@code code_verifier}; this provider doesn't (refresh_token grant has no
     * PKCE parameters).
     *
     * <p>Validates: client_id resolves to a registered client; the registered
     * client allows {@link ClientAuthenticationMethod#NONE}.
     */
    private static final class PublicClientRefreshTokenAuthenticationProvider implements AuthenticationProvider
    {
        private final RegisteredClientRepository clientRepository;

        PublicClientRefreshTokenAuthenticationProvider(RegisteredClientRepository clientRepository)
        {
            this.clientRepository = clientRepository;
        }

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException
        {
            OAuth2ClientAuthenticationToken auth = (OAuth2ClientAuthenticationToken) authentication;
            // Defer if not a NONE-method token (other providers handle confidential clients)
            if (!ClientAuthenticationMethod.NONE.equals(auth.getClientAuthenticationMethod())) return null;
            // Defer if credentials present (PKCE code_verifier flows handled by stock provider)
            if (auth.getCredentials() != null) return null;
            String clientId = auth.getPrincipal().toString();
            org.springframework.security.oauth2.server.authorization.client.RegisteredClient client =
                    clientRepository.findByClientId(clientId);
            if (client == null
                    || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE))
            {
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.INVALID_CLIENT, "Unknown public client: " + clientId, null));
            }
            return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
        }

        @Override
        public boolean supports(Class<?> authentication)
        {
            return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }

    /**
     * Replaces Spring AS's stock {@code OAuth2RefreshTokenAuthenticationProvider}.
     * Validates the presented refresh token via {@link RefreshSessionService}
     * (JWT signature + {@code typ=refresh} + hash matches user-prefs entry)
     * instead of going through the in-memory {@code OAuth2AuthorizationService}
     * (which would lose state on restart and doesn't know about
     * password-grant-issued tokens). Result: refresh tokens issued by
     * ANY {@code /oauth2/token} grant are accepted there and survive restart.
     *
     * <p>Honors {@link RefreshSessionService#shouldRotate} — most refreshes
     * return the same refresh token (~1 prefs write per 30 days per active
     * session, see RefreshSessionService Javadoc).
     */
    private static final class RaplaRefreshTokenAuthenticationProvider implements AuthenticationProvider
    {
        private final RefreshSessionService refreshSessionService;

        RaplaRefreshTokenAuthenticationProvider(RefreshSessionService refreshSessionService)
        {
            this.refreshSessionService = refreshSessionService;
        }

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException
        {
            OAuth2RefreshTokenAuthenticationToken refreshAuth = (OAuth2RefreshTokenAuthenticationToken) authentication;
            OAuth2ClientAuthenticationToken clientAuth = (OAuth2ClientAuthenticationToken) refreshAuth.getPrincipal();
            String presentedRefreshToken = refreshAuth.getRefreshToken();

            RefreshSessionService.ValidatedRefresh validated;
            try
            {
                validated = refreshSessionService.validate(presentedRefreshToken);
            }
            catch (RaplaException e)
            {
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.INVALID_GRANT, e.getMessage(), null));
            }

            try
            {
                // Never rotate: the presented refresh token stays in circulation
                // until expiry (30d). Multi-tab share works trivially.
                Instant now = Instant.now();
                String accessTokenValue = refreshSessionService.issueAccessToken(validated.user());
                OAuth2AccessToken accessToken = new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER, accessTokenValue, now,
                        now.plusSeconds(RefreshSessionService.ACCESS_TOKEN_TTL_SECONDS));
                OAuth2RefreshToken sameRefresh = new OAuth2RefreshToken(presentedRefreshToken, now);
                return new OAuth2AccessTokenAuthenticationToken(
                        clientAuth.getRegisteredClient(), clientAuth, accessToken, sameRefresh);
            }
            catch (Exception e)
            {
                if (e instanceof OAuth2AuthenticationException oae) throw oae;
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.SERVER_ERROR,
                        "Failed to issue tokens on refresh: " + e.getMessage(), null));
            }
        }

        @Override
        public boolean supports(Class<?> authentication)
        {
            return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }

    /**
     * Recognises the OAuth2 Resource Owner Password Credentials grant request
     * (RFC 6749 §4.3) and produces an unauthenticated {@link PasswordGrantAuthenticationToken}.
     * Spring AS 1.0+ removed this grant from the default chain (OAuth 2.1 BCP
     * deprecates it); rapla re-enables it so the legacy direct-password
     * integration path keeps working without rapla-custom endpoints. Will be
     * fully removed when rapla migrates to Keycloak (per PRD 031).
     */
    private static final class PasswordGrantAuthenticationConverter implements AuthenticationConverter
    {
        @Override
        public Authentication convert(HttpServletRequest request)
        {
            String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
            if (!"password".equals(grantType)) return null;
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            if (!StringUtils.hasText(username) || password == null)
            {
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.INVALID_REQUEST,
                        "username and password are required for grant_type=password", null));
            }
            String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
            return new PasswordGrantAuthenticationToken(username, password, scope);
        }
    }

    /**
     * Authenticates a {@link PasswordGrantAuthenticationToken}: delegates to
     * {@link RaplaAuthentificationService} for username/password verification,
     * then issues access + refresh JWTs via {@link RefreshSessionService}
     * (sharing storage + format with {@code authorization_code} flows).
     */
    private static final class PasswordGrantAuthenticationProvider implements AuthenticationProvider
    {
        private final RefreshSessionService refreshSessionService;
        private final RaplaAuthentificationService raplaAuthService;

        PasswordGrantAuthenticationProvider(RefreshSessionService refreshSessionService,
                                            RaplaAuthentificationService raplaAuthService)
        {
            this.refreshSessionService = refreshSessionService;
            this.raplaAuthService = raplaAuthService;
        }

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException
        {
            PasswordGrantAuthenticationToken auth = (PasswordGrantAuthenticationToken) authentication;
            // The OAuth2ClientAuthenticationFilter has already validated the client_id
            // and bound the registered client into the security context as the principal
            // of an OAuth2ClientAuthenticationToken. Pull it out of the context.
            OAuth2ClientAuthenticationToken clientAuth = (OAuth2ClientAuthenticationToken)
                    org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication();
            org.springframework.security.oauth2.server.authorization.client.RegisteredClient client =
                    clientAuth != null ? clientAuth.getRegisteredClient() : null;
            if (client == null)
            {
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.INVALID_CLIENT, "client must authenticate before password grant", null));
            }
            User user;
            try
            {
                user = raplaAuthService.getUserFromCredentials(
                        new org.rapla.storage.dbrm.LoginCredentials(auth.username,
                                auth.password == null ? null : auth.password.toCharArray()));
            }
            catch (Exception e)
            {
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.INVALID_GRANT, "Bad credentials", null));
            }
            if (user == null)
            {
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.INVALID_GRANT, "Bad credentials", null));
            }
            try
            {
                Instant now = Instant.now();
                RefreshSessionService.IssuedTokens issued = refreshSessionService.issueAndPersist(user);
                OAuth2AccessToken accessToken = new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER, issued.accessToken(), now,
                        now.plusSeconds(issued.expiresIn()));
                OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(issued.refreshToken(), now);
                return new OAuth2AccessTokenAuthenticationToken(client, clientAuth, accessToken, refreshToken);
            }
            catch (Exception e)
            {
                throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        OAuth2ErrorCodes.SERVER_ERROR, "Failed to issue tokens: " + e.getMessage(), null));
            }
        }

        @Override
        public boolean supports(Class<?> authentication)
        {
            return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }

    /**
     * Custom Authentication token for the password grant — Spring AS doesn't
     * ship one (the grant was removed from the default chain).
     */
    static final class PasswordGrantAuthenticationToken extends org.springframework.security.authentication.AbstractAuthenticationToken
    {
        final String username;
        final String password;
        final String scope;

        PasswordGrantAuthenticationToken(String username, String password, String scope)
        {
            super(java.util.Collections.emptyList());
            this.username = username;
            this.password = password;
            this.scope = scope;
            setAuthenticated(false);
        }

        @Override public Object getCredentials() { return password; }
        @Override public Object getPrincipal() { return username; }
    }

    /**
     * PRD 041 — replaces Spring AS's stock {@code OAuth2TokenRevocationAuthenticationProvider}
     * to also clear the user's session entry in
     * {@link RefreshSessionService#SESSION} on revocation.
     *
     * <p>RFC 7009 says the revoke endpoint MUST return 200 regardless of
     * whether the token was known. So this provider always returns success;
     * the side effect (clear session) only fires if we can decode the token
     * and resolve the user.
     *
     * <p>Single-token-per-user model: revoking ANY token (access or refresh)
     * for a user clears the user's only session, so all tokens for that user
     * become invalid. Same semantics as the legacy {@code /api/auth/logout}.
     */
    private static final class RaplaTokenRevocationAuthenticationProvider implements AuthenticationProvider
    {
        private final RefreshSessionService refreshSessionService;
        private final RaplaFacade facade;
        private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

        RaplaTokenRevocationAuthenticationProvider(RefreshSessionService refreshSessionService,
                                                   RaplaFacade facade,
                                                   org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder)
        {
            this.refreshSessionService = refreshSessionService;
            this.facade = facade;
            this.jwtDecoder = jwtDecoder;
        }

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException
        {
            org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken revocationAuth =
                    (org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken) authentication;
            String token = revocationAuth.getToken();
            try
            {
                org.springframework.security.oauth2.jwt.Jwt jwt = jwtDecoder.decode(token);
                String userId = jwt.getSubject();
                if (userId != null)
                {
                    User user = facade.getOperator().tryResolve(userId, User.class);
                    if (user != null) refreshSessionService.clearSession(user);
                }
            }
            catch (Exception ignore)
            {
                // RFC 7009: respond OK even for unknown / invalid tokens. No side effect to clear.
            }
            // Mark authenticated so the endpoint returns 200 OK with empty body.
            org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken result =
                    new org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken(
                            token,
                            (OAuth2ClientAuthenticationToken) revocationAuth.getPrincipal(),
                            revocationAuth.getTokenTypeHint());
            result.setAuthenticated(true);
            return result;
        }

        @Override
        public boolean supports(Class<?> authentication)
        {
            return org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken.class
                    .isAssignableFrom(authentication);
        }
    }
}
