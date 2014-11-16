// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.rapla.rest.server.token;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.rapla.rest.gwtjsonrpc.server.JsonServlet;

/**
 * Utility function to compute and verify XSRF tokens.
 * <p>
 * {@link JsonServlet} uses this class to verify tokens appearing in the custom
 * <code>xsrfKey</code> JSON request property. The tokens protect against
 * cross-site request forgery by depending upon the browser's security model.
 * The classic browser security model prohibits a script from site A from
 * reading any data received from site B. By sending unforgeable tokens from the
 * server and asking the client to return them to us, the client script must
 * have had read access to the token at some point and is therefore also from
 * our server.
 */
public class SignedToken {
  private static final int INT_SZ = 4;
  private static final String MAC_ALG = "HmacSHA1";

  /**
   * Generate a random key for use with the XSRF library.
   *
   * @return a new private key, base 64 encoded.
   */
  public static String generateRandomKey() {
    final byte[] r = new byte[26];
    new SecureRandom().nextBytes(r);
    return encodeBase64(r);
  }

  private final int maxAge;
  private final SecretKeySpec key;
  private final SecureRandom rng;
  private final int tokenLength;

  /**
   * Create a new utility, using a randomly generated key.
   *
   * @param age the number of seconds a token may remain valid.
   * @throws TokenInvalidException the JVM doesn't support the necessary algorithms.
   */
  public SignedToken(final int age) throws TokenInvalidException {
    this(age, generateRandomKey());
  }

  /**
   * Create a new utility, using the specific key.
   *
   * @param age the number of seconds a token may remain valid.
   * @param keyBase64 base 64 encoded representation of the key.
   * @throws TokenInvalidException the JVM doesn't support the necessary algorithms.
   */
  public SignedToken(final int age, final String keyBase64)
      throws TokenInvalidException {
    maxAge = age > 5 ? age / 5 : age;
    key = new SecretKeySpec(decodeBase64(keyBase64), MAC_ALG);
    rng = new SecureRandom();
    tokenLength = 2 * INT_SZ + newMac().getMacLength();
  }

  /** @return maximum age of a signed token, in seconds. */
  public int getMaxAge() {
    return maxAge > 0 ? maxAge * 5 : maxAge;
  }

//  /**
//   * Get the text of a signed token which is stored in a cookie.
//   *
//   * @param cookieName the name of the cookie to get the text from.
//   * @return the signed text; null if the cookie is not set or the cookie's
//   *         token was forged.
//   */
//  public String getCookieText(final String cookieName) {
//    final String val = ServletCookieAccess.get(cookieName);
//    boolean ok;
//    try {
//      ok = checkToken(val, null) != null;
//    } catch (XsrfException e) {
//      ok = false;
//    }
//    return ok ? ServletCookieAccess.getTokenText(cookieName) : null;
//  }

  /**
   * Generate a new signed token.
   *
   * @param text the text string to sign. Typically this should be some
   *        user-specific string, to prevent replay attacks. The text must be
   *        safe to appear in whatever context the token itself will appear, as
   *        the text is included on the end of the token.
   * @return the signed token. The text passed in <code>text</code> will appear
   *         after the first ',' in the returned token string.
   * @throws TokenInvalidException the JVM doesn't support the necessary algorithms.
   */
  public String newToken(final String text, Date now) throws TokenInvalidException {
    final int q = rng.nextInt();
    final byte[] buf = new byte[tokenLength];
    encodeInt(buf, 0, q);
    encodeInt(buf, INT_SZ, now(now) ^ q);
    computeToken(buf, text);
    return encodeBase64(buf) + '$' + text;
  }

  /**
   * Validate a returned token.
   *
   * @param tokenString a token string previously created by this class.
   * @param text text that must have been used during {@link #newToken(String)}
   *        in order for the token to be valid. If null the text will be taken
   *        from the token string itself.
   * @return true if the token is valid; false if the token is null, the empty
   *         string, has expired, does not match the text supplied, or is a
   *         forged token.
   * @throws TokenInvalidException the JVM doesn't support the necessary algorithms to
   *         generate a token. XSRF services are simply not available.
   */
  public ValidToken checkToken(final String tokenString, final String text,Date now)
      throws TokenInvalidException {
    if (tokenString == null || tokenString.length() == 0) {
      return null;
    }

    final int s = tokenString.indexOf('$');
    if (s <= 0) {
      return null;
    }

    final String recvText = tokenString.substring(s + 1);
    final byte[] in;
    try {
      String substring = tokenString.substring(0, s);
      in = decodeBase64(substring);
    } catch (RuntimeException e) {
      return null;
    }
    if (in.length != tokenLength) {
      return null;
    }

    final int q = decodeInt(in, 0);
    final int c = decodeInt(in, INT_SZ) ^ q;
    final int n = now( now);
    if (maxAge > 0 && Math.abs(c - n) > maxAge) {
      return null;
    }

    final byte[] gen = new byte[tokenLength];
    System.arraycopy(in, 0, gen, 0, 2 * INT_SZ);
    computeToken(gen, text != null ? text : recvText);
    if (!Arrays.equals(gen, in)) {
      return null;
    }

    return new ValidToken(maxAge > 0 && c + (maxAge >> 1) <= n, recvText);
  }

  private void computeToken(final byte[] buf, final String text)
      throws TokenInvalidException {
    final Mac m = newMac();
    m.update(buf, 0, 2 * INT_SZ);
    m.update(toBytes(text));
    try {
      m.doFinal(buf, 2 * INT_SZ);
    } catch (ShortBufferException e) {
      throw new TokenInvalidException("Unexpected token overflow", e);
    }
  }

  private Mac newMac() throws TokenInvalidException {
    try {
      final Mac m = Mac.getInstance(MAC_ALG);
      m.init(key);
      return m;
    } catch (NoSuchAlgorithmException e) {
      throw new TokenInvalidException(MAC_ALG + " not supported", e);
    } catch (InvalidKeyException e) {
      throw new TokenInvalidException("Invalid private key", e);
    }
  }

  private static int now(Date now) {
    return (int) (now.getTime() / 5000L);
  }

  private static byte[] decodeBase64(final String s) {
    return Base64.decodeBase64(toBytes(s));
  }

  private static String encodeBase64(final byte[] buf) {
    return toString(Base64.encodeBase64(buf));
  }

  private static void encodeInt(final byte[] buf, final int o, int v) {
    buf[o + 3] = (byte) v;
    v >>>= 8;

    buf[o + 2] = (byte) v;
    v >>>= 8;

    buf[o + 1] = (byte) v;
    v >>>= 8;

    buf[o] = (byte) v;
  }

  private static int decodeInt(final byte[] buf, final int o) {
    int r = buf[o] << 8;

    r |= buf[o + 1] & 0xff;
    r <<= 8;

    r |= buf[o + 2] & 0xff;
    return (r << 8) | (buf[o + 3] & 0xff);
  }

  private static byte[] toBytes(final String s) {
    final byte[] r = new byte[s.length()];
    for (int k = r.length - 1; k >= 0; k--) {
      r[k] = (byte) s.charAt(k);
    }
    return r;
  }

  private static String toString(final byte[] b) {
    final StringBuilder r = new StringBuilder(b.length);
    for (int i = 0; i < b.length; i++) {
      r.append((char) b[i]);
    }
    return r.toString();
  }
}
