package org.rapla.server.spring.web;

import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.RaplaAuthentificationService;
import org.rapla.server.spring.JwtConfig;
import org.rapla.storage.dbrm.LoginCredentials;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.file-datasources", name = "raplafile")
@RequestMapping("/auth")
public class AuthController
{
    private static final long ACCESS_TOKEN_TTL_SECONDS = 3600;
    private static final long REFRESH_TOKEN_TTL_SECONDS = 30L * 24 * 3600;

    private final RaplaAuthentificationService authService;
    private final JwtConfig.JwtIssuer jwtIssuer;
    private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    public AuthController(RaplaAuthentificationService authService,
                          JwtConfig.JwtIssuer jwtIssuer,
                          org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder)
    {
        this.authService = authService;
        this.jwtIssuer = jwtIssuer;
        this.jwtDecoder = jwtDecoder;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginCredentials credentials, HttpServletRequest request) throws RaplaException, JOSEException
    {
        User user = authService.getUserFromCredentials(credentials);
        return tokens(user.getId());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest body) throws JOSEException
    {
        org.springframework.security.oauth2.jwt.Jwt parsed = jwtDecoder.decode(body.refreshToken);
        String typ = parsed.getClaimAsString("typ");
        if (!"refresh".equals(typ))
        {
            throw new IllegalArgumentException("not a refresh token");
        }
        return tokens(parsed.getSubject());
    }

    private TokenResponse tokens(String subject) throws JOSEException
    {
        String accessToken = jwtIssuer.issueAccessToken(subject, ACCESS_TOKEN_TTL_SECONDS);
        String refreshToken = jwtIssuer.issueRefreshToken(subject, REFRESH_TOKEN_TTL_SECONDS);
        return new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL_SECONDS);
    }

    public static class RefreshRequest
    {
        public String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class TokenResponse
    {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresIn;

        public TokenResponse(String accessToken, String refreshToken, long expiresIn)
        {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public long getExpiresIn() { return expiresIn; }
    }
}
