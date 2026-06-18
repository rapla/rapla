package org.rapla.server.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ExternalUserResolver;
import org.rapla.test.util.FacadeTestSupport;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The shared JWT → rapla User resolution used by BOTH transports. The
 * GraphQL-facing {@link JwtUserResolver#resolveCurrentUserOrNull()} must
 * resolve an external-IdP (Keycloak) token the same way the REST path does —
 * via {@link ExternalUserResolver} (upn → preferred_username → email) — not by
 * the naive {@code operator.getUser(preferred_username)} the GraphQL resolvers
 * used to do, which silently treated every Keycloak-authenticated caller as
 * anonymous.
 */
class JwtUserResolverTest extends FacadeTestSupport
{
    private static final String KEYCLOAK_ISSUER = "https://keycloak.example.com/realms/dhbw";

    private JwtUserResolver resolver;

    @BeforeEach
    void setUp()
    {
        ExternalProvidersProperties props = new ExternalProvidersProperties();
        ExternalProvidersProperties.Keycloak kc = props.getKeycloak();
        kc.setEnabled(true);
        kc.setBaseUrl("https://keycloak.example.com");
        kc.setRealm("dhbw");
        kc.setClientId("rapla-client");
        resolver = new JwtUserResolver(operator, props, new ExternalUserResolver(operator));
    }

    @AfterEach
    void clearAuth()
    {
        SecurityContextHolder.clearContext();
    }

    /**
     * The bug this fix targets: a Keycloak token whose {@code preferred_username}
     * differs from the rapla username, but whose {@code email} claim matches the
     * stored username (the dhbw shape — rapla username == the email string). The
     * old naive lookup on preferred_username missed it → caller resolved to null
     * → GraphQL reported the authenticated user as anonymous.
     */
    @Test
    void keycloakJwtResolvesWhenPreferredUsernameDiffersButEmailMatches() throws Exception
    {
        User existing = pickAnyExistingUser();

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", KEYCLOAK_ISSUER)
                .claim("preferred_username", "short.handle.that.is.not.the.rapla.username")
                .claim("email", existing.getUsername())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));

        User resolved = resolver.resolveCurrentUserOrNull();
        assertNotNull(resolved, "Keycloak caller must resolve, not be treated as anonymous");
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    /** A rapla-locally-issued token (issuer matches no external provider) resolves its sub as a User UUID. */
    @Test
    void localRaplaJwtResolvesBySubject() throws Exception
    {
        User existing = pickAnyExistingUser();

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("iss", "https://rapla.local")
                .claim("sub", existing.getId())
                .claim("preferred_username", existing.getUsername())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));

        User resolved = resolver.resolveCurrentUserOrNull();
        assertNotNull(resolved);
        assertEquals(existing.getId(), resolved.getId());
    }

    /** Non-JWT authentication (form login, test {@code @WithMockUser}) still resolves by name. */
    @Test
    void usernamePasswordAuthenticationResolvesByName() throws Exception
    {
        User existing = pickAnyExistingUser();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(existing.getUsername(), null, List.of()));

        User resolved = resolver.resolveCurrentUserOrNull();
        assertNotNull(resolved);
        assertEquals(existing.getUsername(), resolved.getUsername());
    }

    @Test
    void anonymousResolvesToNull()
    {
        SecurityContextHolder.clearContext();
        assertNull(resolver.resolveCurrentUserOrNull());
    }

    private User pickAnyExistingUser() throws Exception
    {
        for (User u : facade.getUsers())
        {
            return u;
        }
        throw new IllegalStateException("fixture has no users");
    }
}
