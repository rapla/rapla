package org.rapla.client.internal;

public final class OAuthTokens
{
    private final String accessToken;
    private final String refreshToken;
    private final long expiresIn;

    public OAuthTokens(String accessToken, String refreshToken, long expiresIn)
    {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiresIn() { return expiresIn; }
}
