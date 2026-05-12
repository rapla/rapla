package org.rapla.client.menu;

import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.test.util.HeadlessPresenterTestSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 validation of {@link PasswordChangePolicy} via the
 * {@link HeadlessPresenterTestSupport} harness (PRD 025). This is the
 * first production test against the harness — it confirms that:
 * <ul>
 *   <li>{@code facade} actually loads {@code testdefault.xml} fixture data
 *       and gives a working {@link User} lookup,</li>
 *   <li>The harness lifecycle (per-test fresh facade) works for tests
 *       that don't need a presenter+view (the harness is useful as a
 *       facade-backed test base, not only for MVP wiring).</li>
 * </ul>
 * Tier-1 coverage of the policy itself lives in
 * {@code PasswordChangePolicyTest} (rapla-core, plain JUnit).
 */
class PasswordChangePolicyHarnessTest extends HeadlessPresenterTestSupport
{
    @Test
    void facadeLoadsFixtureUsers() throws Exception
    {
        User homer = lookupUser("homer");
        User monty = lookupUser("monty");
        assertNotNull(homer, "homer must exist in testdefault.xml");
        assertNotNull(monty, "monty must exist in testdefault.xml");
        assertTrue(homer.isAdmin(),  "homer is the admin in the fixture");
        assertFalse(monty.isAdmin(), "monty is the non-admin in the fixture");
    }

    @Test
    void adminCanChangeAnyUsersPasswordUsingRealEntities()
    {
        User homer = lookupUser("homer");
        User monty = lookupUser("monty");
        assertTrue(PasswordChangePolicy.canChangePassword(homer, monty));
        assertTrue(PasswordChangePolicy.canChangePassword(homer, homer));
        // Admin doesn't need to enter the old password when changing someone else's.
        assertFalse(PasswordChangePolicy.requiresOldPassword(homer, monty));
        // But does when changing own.
        assertTrue(PasswordChangePolicy.requiresOldPassword(homer, homer));
    }

    @Test
    void nonAdminCannotChangeOthersPasswordUsingRealEntities()
    {
        User homer = lookupUser("homer");
        User monty = lookupUser("monty");
        assertFalse(PasswordChangePolicy.canChangePassword(monty, homer));
        // …but can change own.
        assertTrue(PasswordChangePolicy.canChangePassword(monty, monty));
    }

    @Test
    void clockIsAvailableAndMutable()
    {
        // Smoke: the harness gives us a deterministic clock. The policy
        // doesn't currently need it; the test verifies the wiring so that
        // future date-sensitive presenter tests can rely on it.
        java.time.LocalDate before = clock.today();
        clock.setToday(java.time.LocalDate.of(2027, 1, 1));
        assertTrue(clock.today().isAfter(before));
    }

    private User lookupUser(String username)
    {
        try
        {
            for (User u : facade.getUsers())
            {
                if (username.equals(u.getUsername())) return u;
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return null;
    }
}
