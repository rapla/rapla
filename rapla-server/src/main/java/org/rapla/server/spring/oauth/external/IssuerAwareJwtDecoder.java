package org.rapla.server.spring.oauth.external;

import com.nimbusds.jwt.JWTParser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Routes JWT validation by the unverified {@code iss} claim. Two route shapes:
 * <ul>
 *   <li>Exact: {@code Map<issuer-string, JwtDecoder>}. Used for fixed-issuer
 *       providers (rapla embedded SAS, single-tenant Entra, Google).</li>
 *   <li>Pattern: ordered list of {@code (predicate, decoder)} pairs. Used for
 *       multi-tenant providers (Entra {@code tenant=common}) where the
 *       {@code iss} claim varies per user's home tenant.</li>
 * </ul>
 *
 * <p>Tokens with an unknown or missing issuer are rejected without consulting
 * any decoder. See PRD 036.
 */
public class IssuerAwareJwtDecoder implements JwtDecoder
{
    private final Map<String, JwtDecoder> byIssuer;
    private final List<Route> patternRoutes;

    public IssuerAwareJwtDecoder(Map<String, JwtDecoder> byIssuer)
    {
        this(byIssuer, List.of());
    }

    public IssuerAwareJwtDecoder(Map<String, JwtDecoder> byIssuer, List<Route> patternRoutes)
    {
        if ((byIssuer == null || byIssuer.isEmpty()) && (patternRoutes == null || patternRoutes.isEmpty()))
        {
            throw new IllegalArgumentException("at least one issuer route required (exact or pattern)");
        }
        this.byIssuer = byIssuer == null ? Map.of() : Map.copyOf(byIssuer);
        this.patternRoutes = patternRoutes == null ? List.of() : List.copyOf(patternRoutes);
    }

    @Override
    public Jwt decode(String token) throws JwtException
    {
        String issuer = peekIssuer(token);
        if (issuer != null)
        {
            JwtDecoder exact = byIssuer.get(issuer);
            if (exact != null) return exact.decode(token);
            for (Route r : patternRoutes)
            {
                if (r.matcher.test(issuer)) return r.decoder.decode(token);
            }
        }
        throw new BadJwtException("Unknown or missing JWT issuer: " + issuer);
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

    /** A pattern-based route: when {@code matcher} accepts the iss, dispatch to {@code decoder}. */
    public static final class Route
    {
        public final Predicate<String> matcher;
        public final JwtDecoder decoder;

        public Route(Predicate<String> matcher, JwtDecoder decoder)
        {
            this.matcher = matcher;
            this.decoder = decoder;
        }
    }
}
