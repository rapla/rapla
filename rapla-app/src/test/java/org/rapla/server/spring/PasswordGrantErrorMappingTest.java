package org.rapla.server.spring;

import org.junit.jupiter.api.Test;
import org.rapla.framework.RaplaException;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 071 / security-audit A1: the OAuth2 {@code grant_type=password} provider must
 * distinguish a genuine credential failure ({@code invalid_grant}) from a server-side
 * fault after credential lookup ({@code server_error}) — mirroring the form-login
 * provider. Before the fix, every exception collapsed into {@code invalid_grant "Bad
 * credentials"}, silently swallowing real server faults on the token endpoint.
 */
class PasswordGrantErrorMappingTest
{
    @Test
    void raplaSecurityException_mapsToInvalidGrant()
    {
        OAuth2AuthenticationException ex = AuthorizationServerConfig.PasswordGrantAuthenticationProvider
                .mapCredentialLookupFailure("homer", new RaplaSecurityException("wrong password"));
        assertEquals(OAuth2ErrorCodes.INVALID_GRANT, ex.getError().getErrorCode());
    }

    @Test
    void serverSideRaplaException_mapsToServerError()
    {
        // A non-security RaplaException (e.g. store/provisioner failure) is NOT a
        // credentials problem — it must surface as server_error, not invalid_grant.
        OAuth2AuthenticationException ex = AuthorizationServerConfig.PasswordGrantAuthenticationProvider
                .mapCredentialLookupFailure("homer", new RaplaException("database unavailable"));
        assertEquals(OAuth2ErrorCodes.SERVER_ERROR, ex.getError().getErrorCode());
    }

    @Test
    void arbitraryRuntimeException_mapsToServerError()
    {
        OAuth2AuthenticationException ex = AuthorizationServerConfig.PasswordGrantAuthenticationProvider
                .mapCredentialLookupFailure("homer", new IllegalStateException("boom"));
        assertEquals(OAuth2ErrorCodes.SERVER_ERROR, ex.getError().getErrorCode());
    }

    /**
     * security-audit A0c / PRD 029 — when local accounts are disabled the password grant
     * is refused with {@code unsupported_grant_type}, before any credential lookup runs.
     */
    @Test
    void localAccountsDisabled_passwordGrantRefused()
    {
        var provider = new AuthorizationServerConfig.PasswordGrantAuthenticationProvider(null, null, false);
        OAuth2AuthenticationException ex = org.junit.jupiter.api.Assertions.assertThrows(
                OAuth2AuthenticationException.class, () -> provider.authenticate(null));
        assertEquals(OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE, ex.getError().getErrorCode());
    }
}
