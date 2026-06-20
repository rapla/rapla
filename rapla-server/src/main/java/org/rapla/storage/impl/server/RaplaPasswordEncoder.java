package org.rapla.storage.impl.server;

import org.rapla.framework.RaplaException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Password hashing for the local user store.
 *
 * <p>rapla <em>writes</em> only BCrypt ({@code bcrypt:} prefix — salted, slow,
 * constant-time verify). It still <em>verifies</em> the historical formats so existing
 * stores keep working: legacy {@code sha-1:}/{@code md5:} hashes, and bare plaintext —
 * the latter is the deliberate "admin hand-edits a reset password into the DB" hatch and
 * is kept on purpose. Any non-bcrypt value that authenticates is rehashed to bcrypt on
 * its next usage (see {@code LocalAbstractCachableOperator#authenticate}).
 */
public class RaplaPasswordEncoder
{
    public static final String BCRYPT_PREFIX = "bcrypt:";

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /** The only format rapla ever persists. */
    public String hash(String plain)
    {
        return BCRYPT_PREFIX + bcrypt.encode(plain);
    }

    /** Verify a candidate against a stored value of any supported format. */
    public boolean matches(String candidate, String stored) throws RaplaException
    {
        if (stored == null || candidate == null)
        {
            return false;
        }
        if (stored.startsWith(BCRYPT_PREFIX))
        {
            return bcrypt.matches(candidate, stored.substring(BCRYPT_PREFIX.length()));
        }
        final int colon = stored.indexOf(':');
        if (colon > 0 && stored.length() > 20)
        {
            final String algo = stored.substring(0, colon);
            if (algo.contains("sha") || algo.contains("md5"))
            {
                return constantTimeEquals(stored, LocalAbstractCachableOperator.encrypt(algo, candidate));
            }
        }
        // Plaintext fallback — the manual admin password-reset hatch. Kept on purpose.
        return constantTimeEquals(stored, candidate);
    }

    /** True for any non-bcrypt stored value: it just authenticated and should self-heal to bcrypt. */
    public boolean needsUpgrade(String stored)
    {
        return stored != null && !stored.startsWith(BCRYPT_PREFIX);
    }

    /**
     * True when the stored value is an <em>empty-but-loginnable</em> password (B3): the
     * seed admin default. Detected by <em>verifying</em> the empty string against the
     * stored value, so it holds regardless of format — literal {@code ""}/blank or a
     * legacy hash of {@code ""} read as unset, while a real password does not.
     *
     * <p>{@code null} is deliberately <b>not</b> unset: a {@code null} stored password
     * means "no password entry", which {@code checkPassword} rejects outright — such a
     * user cannot authenticate at all, so they are never in the empty-password state this
     * flags (no nag, no "log in with empty password" hint).
     */
    public boolean isUnset(String stored) throws RaplaException
    {
        if (stored == null)
        {
            return false;
        }
        if (stored.isBlank())
        {
            return true;
        }
        // A bcrypt hash is always a real password: bcrypt("") can neither be verified
        // (Spring 7 rejects an empty raw password) nor produced (changePassword never
        // hashes ""). Short-circuit so the per-/login-render hint check pays no bcrypt cost.
        if (stored.startsWith(BCRYPT_PREFIX))
        {
            return false;
        }
        return matches("", stored);
    }

    private static boolean constantTimeEquals(String a, String b)
    {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
