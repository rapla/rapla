package org.rapla.client.menu;

import org.junit.jupiter.api.Test;
import org.rapla.client.menu.PasswordChangePolicy.ValidationResult;
import org.rapla.entities.User;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of {@link PasswordChangePolicy} (PRD 023 carve-out
 * extending the pattern from reservation-edit to the menu / password
 * subsystem). Stubs {@link User} via {@code Proxy} — the policy only
 * touches {@code isAdmin()} / {@code getReference()} / {@code equals},
 * so the stub stays small.
 */
class PasswordChangePolicyTest
{
    // ---------- canChangePassword ----------

    @Test
    void userCanChangeOwnPassword()
    {
        User u = stubUser("u1", false);
        assertTrue(PasswordChangePolicy.canChangePassword(u, u));
    }

    @Test
    void adminCanChangeAnyUserPassword()
    {
        User admin = stubUser("admin", true);
        User other = stubUser("other", false);
        assertTrue(PasswordChangePolicy.canChangePassword(admin, other));
    }

    @Test
    void nonAdminCannotChangeOthersPassword()
    {
        User a = stubUser("a", false);
        User b = stubUser("b", false);
        assertFalse(PasswordChangePolicy.canChangePassword(a, b));
    }

    @Test
    void canChangePasswordRejectsNullUsers()
    {
        User u = stubUser("u", false);
        assertFalse(PasswordChangePolicy.canChangePassword(null, u));
        assertFalse(PasswordChangePolicy.canChangePassword(u, null));
        assertFalse(PasswordChangePolicy.canChangePassword(null, null));
    }

    // ---------- requiresOldPassword ----------

    @Test
    void userChangingOwnPasswordMustEnterOld()
    {
        User u = stubUser("u", false);
        assertTrue(PasswordChangePolicy.requiresOldPassword(u, u));
    }

    @Test
    void adminChangingOwnPasswordMustEnterOld()
    {
        User admin = stubUser("admin", true);
        assertTrue(PasswordChangePolicy.requiresOldPassword(admin, admin));
    }

    @Test
    void adminChangingSomeoneElsesDoesNotNeedOld()
    {
        User admin = stubUser("admin", true);
        User other = stubUser("other", false);
        assertFalse(PasswordChangePolicy.requiresOldPassword(admin, other));
    }

    // ---------- validate ----------

    @Test
    void validFormPassesNoError()
    {
        ValidationResult r = PasswordChangePolicy.validate(
                false, null, "newpw".toCharArray(), "newpw".toCharArray());
        assertTrue(r.valid());
        assertNull(r.errorKey());
    }

    @Test
    void requiredOldPasswordRejectsEmpty()
    {
        ValidationResult r = PasswordChangePolicy.validate(
                true, new char[0], "newpw".toCharArray(), "newpw".toCharArray());
        assertFalse(r.valid());
        assertEquals("error.password_required", r.errorKey());
    }

    @Test
    void requiredOldPasswordRejectsNull()
    {
        ValidationResult r = PasswordChangePolicy.validate(
                true, null, "newpw".toCharArray(), "newpw".toCharArray());
        assertFalse(r.valid());
        assertEquals("error.password_required", r.errorKey());
    }

    @Test
    void emptyNewPasswordIsRejected()
    {
        ValidationResult r = PasswordChangePolicy.validate(
                false, null, new char[0], new char[0]);
        assertFalse(r.valid());
        assertEquals("error.password_required", r.errorKey());
    }

    @Test
    void mismatchedNewAndVerificationRejected()
    {
        ValidationResult r = PasswordChangePolicy.validate(
                false, null, "newpw".toCharArray(), "different".toCharArray());
        assertFalse(r.valid());
        assertEquals("error.passwords_dont_match", r.errorKey());
    }

    @Test
    void adminPathDoesNotRequireOld()
    {
        // requireOld=false → old can be empty; verifies that admin-changing-other
        // doesn't get blocked by an empty old-password field.
        ValidationResult r = PasswordChangePolicy.validate(
                false, new char[0], "newpw".toCharArray(), "newpw".toCharArray());
        assertTrue(r.valid());
    }

    // ---------- helpers ----------

    private static User stubUser(String id, boolean isAdmin)
    {
        org.rapla.entities.storage.ReferenceInfo<User> ref =
                new org.rapla.entities.storage.ReferenceInfo<>(id, User.class);
        return (User) Proxy.newProxyInstance(
                User.class.getClassLoader(),
                new Class[] { User.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "isAdmin":      return isAdmin;
                        case "getReference": return ref;
                        case "getId":        return id;
                        case "getGroupList": return java.util.Collections.emptyList();
                        case "equals":       return args[0] == proxy
                                || (args[0] instanceof User other && id.equals(other.getId()));
                        case "hashCode":     return id.hashCode();
                        case "toString":     return "StubUser[" + id + ", admin=" + isAdmin + "]";
                        default: throw new UnsupportedOperationException(
                                "stub does not implement " + method.getName());
                    }
                });
    }
}
