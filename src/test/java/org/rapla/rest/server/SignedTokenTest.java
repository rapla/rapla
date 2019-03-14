package org.rapla.rest.server;

//import io.jsonwebtoken.*;
//import io.jsonwebtoken.impl.crypto.MacProvider;
import io.reactivex.functions.Function;
import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.rest.server.token.SignedToken;

import java.security.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(JUnit4.class)
public class SignedTokenTest {

    Key privateKey;
    Key publicKey;
    String seed;

    private static final String ASYMMETRIC_ALGO = "RSA";

    @Test
    public void parseDate() throws ParseException
    {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        df.setTimeZone( TimeZone.getTimeZone("UTC"));
        Date date = df.parse("2017-08-03T13:10:49.472Z");

    }

    public void testDate() throws ParseException {
        
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final Date date = new Date();
        final String format4 = SerializableDateTimeFormat.INSTANCE.formatTimestamp(date);
        final String format = df.format(date);
        Date parsed = df.parse( format);
        Assert.assertEquals(date, parsed);
        Assert.assertEquals(format4, format);
    }

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

        //signingKey = MacProvider.generateKey(signatureAlgo);
        //unsigningKey = signingKey;
        //jwtParser = Jwts.parser().setSigningKey(publicKey);

        //this.publicKey =base64.encodeAsString(publicKeyObj.getEncoded());

    }

    @Test
    public void testSignedToken() throws Exception {
        SignedToken tokenGenerator = new SignedToken(-1, seed);
        final int tokenCount = 100000;
        final Date now = new Date();

        Function<String,String> sign = (id)->tokenGenerator.newToken(id, calculateExpirationTime());
        Function<String,String> check = (id)->tokenGenerator.checkToken(id, null,now).getData();
        testSigning(sign, check, tokenCount);
    }

//    @Test
//    public void testJWSTokenAsymetric() throws Exception {
//        Key signingKey = privateKey;
//        Key unsigningKey = publicKey;
//        final SignatureAlgorithm signatureAlgo = SignatureAlgorithm.RS256;
//        Function<String,String> sign = (id)->createJwt(signatureAlgo, signingKey, id);
//        JwtParser jwtParser = Jwts.parser().setSigningKey(unsigningKey);
//        Function<String,String> check = (id)->checkToken (jwtParser,id);
//        final int tokenCount = 1000;
//        testSigning(sign, check, tokenCount);
//    }
//
//
//    @Test
//    public void testJWSTokenSymetric() throws Exception {
//        final SignatureAlgorithm signatureAlgo = SignatureAlgorithm.HS512;
//        Key signingKey = MacProvider.generateKey(signatureAlgo);
//        Key unsigningKey = signingKey;
//        Function<String,String> sign = (id)->createJwt(signatureAlgo, signingKey, id);
//        JwtParser jwtParser = Jwts.parser().setSigningKey(unsigningKey);
//        Function<String,String> check = (id)->checkToken (jwtParser,id);
//        final int tokenCount = 100000;
//        testSigning(sign, check, tokenCount);
//    }
//
//    public String createJwt( SignatureAlgorithm signatureAlgo, Key signingKey,String userId) {
//        JwtBuilder builder = Jwts.builder()
//                //.claim("payload", loggedInUser.getPayload())
//                .setId(userId)
//                .setExpiration(calculateExpirationTime());
//        return builder.signWith(
//                signatureAlgo, signingKey
//        ).compact();
//    }
//
//    String checkToken(JwtParser jwtParser,String accesTokenString) {
//
//        Claims claims = jwtParser
//                .parseClaimsJws(accesTokenString)
//                .getBody();
//        final String id = claims.getId();
//        return id;
//    }

    public void testSigning(Function<String, String> sign, Function<String, String> check, int tokenCount) throws Exception {
        final List<String> users = IntStream.range(1, tokenCount).mapToObj((i) -> "user" + i).collect(Collectors.toList());
        Map<String,String> tokens = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        for ( String user:users)
        {
            String compactJws = sign.apply(  user);
            tokens.put( user, compactJws);
        }
        final long timeStampAfterGenerate = System.currentTimeMillis();
        long timeGenerate = timeStampAfterGenerate - start;
        System.out.println("Time to generate " + tokenCount + " tokens " + timeGenerate + " ms");
        for ( Map.Entry<String,String> entry: tokens.entrySet())
        {
            String id = entry.getKey();
            String token = entry.getValue();
            final String s = check.apply(token);
            Assert.assertEquals( id, s);
        }
        long timeCheck = System.currentTimeMillis() - timeStampAfterGenerate;
        System.out.println("Time to check " + tokenCount + " tokens " + timeCheck + " ms");
    }

    private Date calculateExpirationTime() {
        return DateTools.addDay( new Date());
    }
}
