package org.rapla.storage.dbrm;


import java.util.Date;

/**
 * Wire-format DTO mirroring the server's {@code AuthController.TokenResponse}.
 * Fields {@code accessToken}, {@code refreshToken}, {@code expiresIn} are
 * populated by Jackson when {@code POST /auth/login} or {@code POST /auth/refresh}
 * returns. {@code validUntil} is derived (computed at construction time relative
 * to "now") so existing callers that read {@link #getValidUntil()} keep working.
 */
public class LoginTokens {
    String accessToken;
    String refreshToken;
    long expiresIn;
    java.time.LocalDateTime validUntil;

    public LoginTokens() {
        this("", (java.time.LocalDateTime) null);
    }

    /** Modern constructor matching the server's TokenResponse shape. */
    public LoginTokens(String accessToken, String refreshToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresInSeconds;
        this.validUntil = expiresInSeconds > 0
                ? java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(expiresInSeconds)
                : null;
    }

    /** Legacy constructor — kept for callers that compute validUntil themselves. */
    public LoginTokens(String accessToken, Date validUntil) {
        this(accessToken, validUntil == null ? null : org.rapla.components.util.DateTools.toLocalDateTime(validUntil));
    }

    /** {@code LocalDateTime} ctor. UTC. */
    public LoginTokens(String accessToken, java.time.LocalDateTime validUntil) {
        this.accessToken = accessToken;
        this.validUntil = validUntil;
        this.expiresIn = validUntil == null ? 0
                : Math.max(0L, (org.rapla.components.util.DateTools.toMilli(validUntil) - System.currentTimeMillis()) / 1000L);
    }

    /** {@code LocalDateTime} factory paralleling {@link #LoginTokens(String, Date)}. UTC. */
    public static LoginTokens ofLocalDateTime(String accessToken, java.time.LocalDateTime validUntil) {
        return new LoginTokens(accessToken, validUntil);
    }

    /**
     * Jackson-friendly setter for {@code expiresIn}. Also recomputes {@link #validUntil}
     * relative to "now" so the legacy getter stays accurate.
     */
    public void setExpiresIn(long expiresInSeconds) {
        this.expiresIn = expiresInSeconds;
        this.validUntil = expiresInSeconds > 0
                ? java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(expiresInSeconds)
                : null;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getAccessToken()
    {
        return accessToken;
    }

    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Date getValidUntil()
    {
        return validUntil == null ? null : org.rapla.components.util.DateTools.toDate(validUntil);
    }

    /** {@code LocalDateTime} variant. UTC. */
    public java.time.LocalDateTime getValidUntilAsLocalDateTime()
    {
        return validUntil;
    }

    public String toString()
    {
        return accessToken + "#" + (validUntil == null ? "0" : org.rapla.components.util.DateTools.toMilli(validUntil));
    }

    public static LoginTokens fromString(String s){
    	if(s.startsWith("\""))s=s.replaceFirst("\"", "");
    	if(s.endsWith("\""))s=s.substring(0, s.length()-1);
        String[] split = s.split("#");
        String accessToken2 = split[0];
        long parseLong = Long.parseLong(split[1]);
        java.time.LocalDateTime validUntil2 = org.rapla.components.util.DateTools.toLocalDateTime(parseLong);
        return       new LoginTokens(accessToken2, validUntil2);
    }

    public boolean isValid() {
        if (validUntil == null) return false;
        return System.currentTimeMillis() < org.rapla.components.util.DateTools.toMilli(validUntil);
    }
}
