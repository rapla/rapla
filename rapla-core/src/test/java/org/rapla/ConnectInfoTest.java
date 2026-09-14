package org.rapla;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PRD 072 Phase 5 (Swing A+Y): rapla is the only token endpoint Swing ever
 * talks to, so {@link ConnectInfo} no longer carries provider routing
 * ({@code refreshUrl} / {@code oauthClientId}). It collapses to the two-token
 * shape; the impersonation switch-back site captures admin's session with the
 * same two-arg constructor.
 */
class ConnectInfoTest
{
    @Test
    void twoArgConstructorCarriesTokensOnly()
    {
        ConnectInfo info = new ConnectInfo("access-1", "refresh-1");
        assertEquals("access-1", info.getAccessToken());
        assertEquals("refresh-1", info.getRefreshToken());
    }

    @Test
    void withAccessTokenFactoryEqualsTwoArgConstructor()
    {
        ConnectInfo info = ConnectInfo.withAccessToken("a", "r");
        assertEquals("a", info.getAccessToken());
        assertEquals("r", info.getRefreshToken());
    }

    @Test
    void nullRefreshTokenIsAllowedForApiKeyBootstrap()
    {
        ConnectInfo info = new ConnectInfo("api-key-jwt", null);
        assertEquals("api-key-jwt", info.getAccessToken());
        assertNull(info.getRefreshToken());
    }

    @Test
    void toStringMasksTokens()
    {
        ConnectInfo info = new ConnectInfo("secret-access", "secret-refresh");
        String s = info.toString();
        org.junit.jupiter.api.Assertions.assertEquals(false, s.contains("secret-access"));
        org.junit.jupiter.api.Assertions.assertEquals(false, s.contains("secret-refresh"));
    }
}
