package org.rapla.rest.server;

import io.jsonwebtoken.*;
import io.jsonwebtoken.impl.crypto.MacProvider;
import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.DateTools;
import org.rapla.rest.server.token.SignedToken;
import org.rapla.rest.server.token.TokenInvalidException;

import java.security.*;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(JUnit4.class)
public class SignedTokenTest {

    Key privateKey;
    Key publicKey;
    String seed;
    Key signingKey;
    Key unsigningKey;
    JwtParser jwtParser;
    private static final String ASYMMETRIC_ALGO = "RSA";
    final SignatureAlgorithm signatureAlgo = SignatureAlgorithm.HS256;

    @Before
    public void setUp() throws NoSuchAlgorithmException {
        byte[] linebreake = {};
        Base64 base64 = new Base64(64, linebreake, true);
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance( ASYMMETRIC_ALGO );
        keyPairGen.initialize( 2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        PrivateKey privateKeyObj = keyPair.getPrivate();
        seed = base64.encodeAsString(privateKeyObj.getEncoded());
        privateKey = privateKeyObj;
        //this.privateKey = base64.encodeAsString(privateKeyObj.getEncoded());
        PublicKey publicKeyObj = keyPair.getPublic();
        publicKey = publicKeyObj;

        signingKey = MacProvider.generateKey(signatureAlgo);
        unsigningKey = signingKey;
        //jwtParser = Jwts.parser().setSigningKey(publicKey);
        jwtParser = Jwts.parser().setSigningKey(signingKey);
        //this.publicKey =base64.encodeAsString(publicKeyObj.getEncoded());

    }

    @Test
    public void testPerformance() throws TokenInvalidException {
        SignedToken tokenGenerator = new SignedToken(-1, seed);
        final int tokenCount = 100000;
        final Date now = new Date();
        final List<String> users = IntStream.range(1, tokenCount).mapToObj((i) -> "user" + i).collect(Collectors.toList());
        Map<String,String> tokens = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        for ( String user:users)
        {
            tokens.put( user, tokenGenerator.newToken(user, calculateExpirationTime()));
        }
        final long timeStampAfterGenerate = System.currentTimeMillis();
        long timeGenerate = timeStampAfterGenerate - start;
        System.out.println("Time to generate " + tokenCount + " tokens " + timeGenerate + " ms");
        for ( Map.Entry<String,String> entry: tokens.entrySet())
        {
            String text = entry.getKey();
            String token = entry.getValue();
            tokenGenerator.checkToken( token,text, now );
        }
        long timeCheck = System.currentTimeMillis() - timeStampAfterGenerate;
        System.out.println("Time to check " + tokenCount + " tokens " + timeCheck + " ms");
    }

    @Test
    public void testJWSToken()
    {
        final int tokenCount = 1000;
        final List<String> users = IntStream.range(1, tokenCount).mapToObj((i) -> "user" + i).collect(Collectors.toList());
        Map<String,String> tokens = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        for ( String user:users)
        {
            String compactJws = createJwt( user, user);
            tokens.put( user, compactJws);
        }
        final long timeStampAfterGenerate = System.currentTimeMillis();
        long timeGenerate = timeStampAfterGenerate - start;
        System.out.println("Time to generate " + tokenCount + " tokens " + timeGenerate + " ms");
        for ( Map.Entry<String,String> entry: tokens.entrySet())
        {
            String id = entry.getKey();
            String token = entry.getValue();
            final String s = checkToken(token);
            Assert.assertEquals( id, s);
        }
        long timeCheck = System.currentTimeMillis() - timeStampAfterGenerate;
        System.out.println("Time to check " + tokenCount + " tokens " + timeCheck + " ms");


    }


    public String createJwt(String username, String userId) {
        JwtBuilder builder = Jwts.builder()
                //.claim("payload", loggedInUser.getPayload())
                .setId(userId)
                .setExpiration(calculateExpirationTime());
        return builder.signWith(
                signatureAlgo, signingKey
        ).compact();
    }

    String checkToken(String accesTokenString) {

        Claims claims = jwtParser
                .parseClaimsJws(accesTokenString)
                .getBody();
        final String id = claims.getId();
        return id;
    }

    private Date calculateExpirationTime() {
        return DateTools.addDay( new Date());
    }
}
