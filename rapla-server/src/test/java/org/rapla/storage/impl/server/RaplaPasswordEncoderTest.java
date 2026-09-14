package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RaplaPasswordEncoderTest
{
    private final RaplaPasswordEncoder enc = new RaplaPasswordEncoder();

    @Test
    public void hashesToBcryptPrefix()
    {
        String hashed = enc.hash("secret");
        assertTrue(hashed.startsWith("bcrypt:"), hashed);
        assertNotEquals("secret", hashed);
    }

    @Test
    public void bcryptRoundTrips() throws Exception
    {
        String hashed = enc.hash("secret");
        assertTrue(enc.matches("secret", hashed));
        assertFalse(enc.matches("wrong", hashed));
    }

    @Test
    public void legacySha1StillVerifies() throws Exception
    {
        String stored = LocalAbstractCachableOperator.encrypt("sha-1", "secret");
        assertTrue(stored.startsWith("sha-1:"), stored);
        assertTrue(enc.matches("secret", stored));
        assertFalse(enc.matches("wrong", stored));
    }

    @Test
    public void legacyMd5StillVerifies() throws Exception
    {
        String stored = LocalAbstractCachableOperator.encrypt("md5", "secret");
        assertTrue(stored.startsWith("md5:"), stored);
        assertTrue(enc.matches("secret", stored));
    }

    @Test
    public void plaintextStillAccepted() throws Exception
    {
        // admin hand-edits a plaintext reset value into the store — must authenticate
        assertTrue(enc.matches("resetme", "resetme"));
        assertFalse(enc.matches("other", "resetme"));
    }

    @Test
    public void needsUpgradeForEveryNonBcryptFormat() throws Exception
    {
        assertTrue(enc.needsUpgrade(LocalAbstractCachableOperator.encrypt("sha-1", "x")));
        assertTrue(enc.needsUpgrade(LocalAbstractCachableOperator.encrypt("md5", "x")));
        assertTrue(enc.needsUpgrade("a-bare-plaintext-password"));
        assertFalse(enc.needsUpgrade(enc.hash("x")));
    }

    @Test
    public void isUnsetDetectsEmptyAcrossEveryStorageFormat() throws Exception
    {
        // B3: "no real password" detectable across every format that can actually occur.
        // null is NOT unset: a null password entry cannot authenticate at all, so such a
        // user is never in the empty-but-loginnable state this flags.
        assertFalse(enc.isUnset(null), "null = no entry = cannot log in, not 'empty password'");
        assertTrue(enc.isUnset(""), "literal empty (seed admin default)");
        assertTrue(enc.isUnset("   "), "blank");
        // legacy rapla stored an empty password as sha-1/md5 of "" — a real existing-store case
        assertTrue(enc.isUnset(LocalAbstractCachableOperator.encrypt("sha-1", "")), "legacy sha-1 of empty");
        assertTrue(enc.isUnset(LocalAbstractCachableOperator.encrypt("md5", "")), "legacy md5 of empty");

        // a real password is NOT unset, in any format
        assertFalse(enc.isUnset(enc.hash("secret")));
        assertFalse(enc.isUnset(LocalAbstractCachableOperator.encrypt("sha-1", "secret")));
        assertFalse(enc.isUnset("some-plaintext-reset-value"));

        // bcrypt("") is deliberately NOT asserted: Spring 7's BCrypt.matches refuses an empty
        // raw password (matches("", anyBcryptHash) is always false), so it can't be detected —
        // which is exactly why the "never hash an empty password" invariant is mandatory, not
        // optional. changePassword keeps "" literal; see changePasswordKeepsEmptyLiteral.
    }
}
