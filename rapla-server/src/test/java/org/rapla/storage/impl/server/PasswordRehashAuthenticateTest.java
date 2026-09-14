package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.test.util.FacadeTestSupport;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A7: a legacy/plaintext password authenticates and is rehashed to bcrypt on that login.
 * {@code testdefault.xml} seeds {@code homer} with the plaintext password {@code duffs}.
 */
public class PasswordRehashAuthenticateTest extends FacadeTestSupport
{
    @Test
    public void plaintextLoginRehashesToBcryptOnDisk() throws Exception
    {
        // plaintext acceptance (admin reset hatch) — login must succeed
        assertNotNull(operator.authenticate("homer", "duffs"));

        String xml = Files.readString(tempDir.resolve("rapla-data.xml"));
        // rapla never persists plaintext: the value was rehashed on this login
        assertFalse(xml.contains("password=\"duffs\""), "plaintext password must not remain on disk");
        assertTrue(xml.contains("password=\"bcrypt:"), "password must be rehashed to bcrypt");

        // still authenticates with the same secret afterwards (now via bcrypt)
        assertNotNull(operator.authenticate("homer", "duffs"));
        // a wrong password is still rejected
        try
        {
            operator.authenticate("homer", "wrong");
            org.junit.jupiter.api.Assertions.fail("wrong password must not authenticate");
        }
        catch (Exception expected)
        {
            // expected
        }
    }

    @Test
    public void changePasswordKeepsEmptyLiteral() throws Exception
    {
        // B3 invariant: an empty new password is stored as literal "", never hashed —
        // otherwise isUnset() could not detect it (Spring 7 BCrypt can't verify empty).
        User homer = facade.getUser("homer");
        operator.changePassword(homer, "duffs".toCharArray(), "".toCharArray());

        String xml = Files.readString(tempDir.resolve("rapla-data.xml"));
        String homerLine = xml.lines().filter(l -> l.contains("username=\"homer\"")).findFirst().orElse("");
        assertTrue(homerLine.contains("password=\"\""), "empty password must stay literal: " + homerLine);
        assertFalse(homerLine.contains("bcrypt"), "empty must not be hashed: " + homerLine);

        // empty login is allowed
        assertNotNull(operator.authenticate("homer", ""));
    }
}
