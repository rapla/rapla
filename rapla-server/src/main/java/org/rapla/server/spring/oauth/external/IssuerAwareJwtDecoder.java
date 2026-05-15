package org.rapla.server.spring.oauth.external;

import com.nimbusds.jwt.JWTParser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.text.ParseException;
import java.util.Map;

/**
 * Routes JWT validation by the unverified {@code iss} claim. Each registered
 * issuer maps to a fully-configured {@link JwtDecoder} that validates signature
 * + standard claims for that issuer. Tokens with an unknown or missing
 * issuer are rejected without consulting any decoder.
 *
 * <p>This is the foundation for multi-IdP support — rapla's embedded Spring
 * Authorization Server, Microsoft Entra, and Google can all be valid token
 * sources simultaneously. See PRD 036.
 */
public class IssuerAwareJwtDecoder implements JwtDecoder
{
    private final Map<String, JwtDecoder> byIssuer;

    public IssuerAwareJwtDecoder(Map<String, JwtDecoder> byIssuer)
    {
        if (byIssuer == null || byIssuer.isEmpty())
        {
            throw new IllegalArgumentException("byIssuer must contain at least one entry");
        }
        this.byIssuer = Map.copyOf(byIssuer);
    }

    @Override
    public Jwt decode(String token) throws JwtException
    {
        String issuer = peekIssuer(token);
        JwtDecoder decoder = issuer == null ? null : byIssuer.get(issuer);
        if (decoder == null)
        {
            throw new BadJwtException("Unknown or missing JWT issuer: " + issuer);
        }
        return decoder.decode(token);
    }

    private static String peekIssuer(String token)
    {
        if (token == null) return null;
        try
        {
            return JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        }
        catch (ParseException e)
        {
            return null;
        }
    }
}
