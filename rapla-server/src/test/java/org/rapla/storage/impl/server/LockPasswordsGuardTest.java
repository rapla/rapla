package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 118 D8-2: {@code rapla.lock-passwords} locks the password of EVERY user (public demo) — no
 * change through any caller, no "please set a password" nag, and the capability reports it.
 */
public class LockPasswordsGuardTest extends FacadeTestSupport
{
    private User passwordless(String username) throws Exception
    {
        User user = facade.newUser();
        user.setUsername(username);
        facade.store(user);
        operator.changePassword(facade.getUser(username), new char[0], new char[0]);
        return facade.getUser(username);
    }

    @Test
    public void blocksEveryUsersPasswordChangeWhenFlagSet() throws Exception
    {
        User student = passwordless("student");
        operator.setLockPasswords(true);
        assertThrows(RaplaSecurityException.class, () -> operator.changePassword(student, new char[0], "newpw".toCharArray()));
        assertThrows(RaplaSecurityException.class,
                () -> operator.changePassword(facade.getUser("homer"), "duffs".toCharArray(), "newpw".toCharArray()));
        assertFalse(operator.canChangePassword());
        assertFalse(operator.isPasswordChangeRequired(student));
    }

    @Test
    public void leavesPasswordsChangeableWhenFlagOff() throws Exception
    {
        User student = passwordless("student");
        operator.setLockPasswords(false);
        assertTrue(operator.canChangePassword());
        assertTrue(operator.isPasswordChangeRequired(student));
        assertDoesNotThrow(() -> operator.changePassword(student, new char[0], "newpw".toCharArray()));
    }
}
