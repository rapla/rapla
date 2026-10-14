package org.rapla.client.internal;

public final class OAuthTokens
{
    private final String accessToken;
    private final String refreshToken;
    private final String idToken;
    private final long expiresIn;
    private final boolean remember;

    public OAuthTokens(String accessToken, String refreshToken, String idToken, long expiresIn, boolean remember)
    {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.idToken = idToken;
        this.expiresIn = expiresIn;
        this.remember = remember;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    /** OIDC id_token from the token-endpoint response; needed as {@code id_token_hint}
     *  for OIDC RP-initiated logout at {@code /connect/logout}. Null when the scope
     *  doesn't include {@code openid} (rare for this codebase). */
    public String getIdToken() { return idToken; }
    public long getExpiresIn() { return expiresIn; }
    /** The server's stay-signed-in decision of this login (PRD 131 R4); false when the callback carried none. */
    public boolean isRemember() { return remember; }
}
