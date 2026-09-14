/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbrm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for {@link ImpersonationService#impersonate}. Mirrors the
 * subset of the OAuth2 token response that clients actually need —
 * {@code access_token}, {@code token_type}, {@code expires_in} — and
 * deliberately omits {@code refresh_token}: impersonation tokens are not
 * refreshable, renewal happens by calling the endpoint again. See PRD 051
 * § "Token renewal model" for the rationale.
 */
public final class ImpersonationResponse
{
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private long expiresIn;

    public ImpersonationResponse() {}

    public ImpersonationResponse(String accessToken, String tokenType, long expiresIn)
    {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
}
