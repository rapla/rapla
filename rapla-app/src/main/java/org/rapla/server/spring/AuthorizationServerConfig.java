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
import org.rapla.server.util.SameOriginUriCheck;
import org.rapla.server.util.WslBridgeUriCheck;
import org.rapla.storage.dbrm.LoginCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
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
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
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
public class AuthorizationServerConfig
{
    @Value("${rapla.oauth.allow-wsl-bridge-redirects:true}")
    private boolean allowWslBridgeRedirects;

    @Value("${rapla.oauth.allow-same-origin-redirects:true}")
    private boolean allowSameOriginRedirects;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception
    {
        OAuth2AuthorizationServerConfigurer authServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = authServerConfigurer.getEndpointsMatcher();

        authServerConfigurer.authorizationEndpoint(authorizationEndpoint ->
                authorizationEndpoint.authenticationProviders(providers -> providers.forEach(provider -> {
                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeRequestProvider)
                    {
                        codeRequestProvider.setAuthenticationValidator(redirectUriAndScopeValidator(
                                allowWslBridgeRedirects, allowSameOriginRedirects));
                    }
                })));

        http
                .securityMatcher(endpointsMatcher)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .with(authServerConfigurer, c -> c.oidc(Customizer.withDefaults()))
                .exceptionHandling(exc -> exc.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    /**
     * Wraps Spring's default redirect-URI + scope validation with two extra
     * allowances:
     * <ol>
     *   <li><b>WSL bridge</b> — accept any port for hosts in 172.16.0.0/12
     *       (Hyper-V WSL2 bridge), provided the path matches a registered
     *       URI. Lets a developer run the Swing client in WSL2 without
     *       enabling mirrored networking. Toggle via
     *       {@code rapla.oauth.allow-wsl-bridge-redirects=false}.</li>
     *   <li><b>Same-origin</b> — accept any redirect URI whose scheme/host/port
     *       match the auth-server request's public origin (honoring
     *       X-Forwarded-*), provided the path matches a registered URI.
     *       Zero-config Angular SPA deployment: the SPA registers
     *       {@code /auth/callback} as its path and the validator accepts
     *       whatever public origin rapla is serving on. Toggle via
     *       {@code rapla.oauth.allow-same-origin-redirects=false}.</li>
     * </ol>
     */
    private static Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> redirectUriAndScopeValidator(
            boolean allowWslBridge, boolean allowSameOrigin)
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
                // are narrow (host range + path-must-be-registered) so a request
                // that doesn't match WSL bridge / same-origin still re-throws.
                if (allowWslBridge && isWslBridgeRedirect(ctx)) return;
                if (allowSameOrigin && isSameOriginRedirect(ctx)) return;
                throw ex;
            }
        };
        return withAllowances.andThen(scopeValidator);
    }

    private static boolean isWslBridgeRedirect(OAuth2AuthorizationCodeRequestAuthenticationContext ctx)
    {
        OAuth2AuthorizationCodeRequestAuthenticationToken auth = ctx.getAuthentication();
        String redirectUri = auth == null ? null : auth.getRedirectUri();
        RegisteredClient client = ctx.getRegisteredClient();
        if (client == null) return false;
        Set<String> registered = client.getRedirectUris().stream().collect(Collectors.toUnmodifiableSet());
        return WslBridgeUriCheck.isWslBridgeRedirect(redirectUri, registered);
    }

    private static boolean isSameOriginRedirect(OAuth2AuthorizationCodeRequestAuthenticationContext ctx)
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
        Set<String> registered = client.getRedirectUris().stream().collect(Collectors.toUnmodifiableSet());
        return SameOriginUriCheck.isSameOriginRedirect(redirectUri, scheme, host, port, registered);
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
     * Adds a {@code typ=refresh} claim to refresh tokens issued by Spring
     * Authorization Server. The stateless rapla {@code /auth/refresh}
     * endpoint validates this claim before re-issuing tokens; with the
     * customizer in place, OAuth-issued refresh tokens are accepted by the
     * same endpoint as legacy {@code /auth/login} refresh tokens. Net:
     * one refresh endpoint, two issuance paths, refresh tokens survive
     * server restart for both (since {@code /auth/refresh} doesn't
     * consult SAS's in-memory authorization state).
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtRefreshTokenCustomizer()
    {
        return ctx -> {
            if (OAuth2TokenType.REFRESH_TOKEN.equals(ctx.getTokenType()))
            {
                ctx.getClaims().claim("typ", "refresh");
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
                LoginCredentials credentials = new LoginCredentials(username, password, null);
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
                catch (Exception e)
                {
                    throw new BadCredentialsException("authentication failed", e);
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
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getId())
                    .password("N/A")
                    .authorities(role)
                    .build();
        };
    }

}
