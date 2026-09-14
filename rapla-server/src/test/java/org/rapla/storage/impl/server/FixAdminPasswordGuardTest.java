package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.UpdateEvent;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * B3: {@code rapla.fix-admin-password} locks the built-in {@code admin} account —
 * its password cannot be changed and it cannot be deleted (managed/demo deployments).
 */
public class FixAdminPasswordGuardTest extends FacadeTestSupport
{
    private User createAdmin() throws Exception
    {
        User admin = facade.newUser();
        admin.setUsername("admin");
        admin.setName("Administrator");
        facade.store(admin);
        // mirror the real seed admin: a literal empty ("" ) password, which can log in
        operator.changePassword(facade.getUser("admin"), new char[0], new char[0]);
        return facade.getUser("admin");
    }

    @Test
    public void blocksAdminPasswordChangeWhenFlagSet() throws Exception
    {
        User admin = createAdmin();
        operator.setFixAdminPassword(true);
        assertThrows(RaplaSecurityException.class,
                () -> operator.changePassword(admin, new char[0], "newpw".toCharArray()));
    }

    @Test
    public void blocksAdminDeletionWhenFlagSet() throws Exception
    {
        User admin = createAdmin();
        operator.setFixAdminPassword(true);
        assertThrows(RaplaSecurityException.class, () -> facade.remove(admin));
    }

    @Test
    public void blocksAdminDeletionViaDispatchWhenFlagSet() throws Exception
    {
        // REST/SPA/GraphQL deletes go through operator.dispatch(UpdateEvent), NOT
        // facade.remove → storeAndRemove. The guard must sit on the dispatch path too.
        User admin = createAdmin();
        operator.setFixAdminPassword(true);
        UpdateEvent evt = new UpdateEvent();
        evt.putRemoveId(admin.getReference());
        // userId left null so the "can't delete himself" guard doesn't mask the fix-admin one
        assertThrows(RaplaSecurityException.class, () -> operator.dispatch(evt));
    }

    @Test
    public void blocksAdminModificationWhenFlagSet() throws Exception
    {
        // a fixed admin must be immutable, not just undeletable — no rename / re-permission / etc.
        User admin = createAdmin();
        operator.setFixAdminPassword(true);
        User editable = facade.edit(admin);
        editable.setName("hacked");
        assertThrows(RaplaSecurityException.class, () -> facade.store(editable));
    }

    @Test
    public void allowsAdminMutationWhenFlagOff() throws Exception
    {
        User admin = createAdmin();
        operator.setFixAdminPassword(false);
        assertDoesNotThrow(() -> operator.changePassword(admin, new char[0], "newpw".toCharArray()));
    }

    @Test
    public void doesNotAffectOtherUsersWhenFlagSet() throws Exception
    {
        createAdmin();
        operator.setFixAdminPassword(true);
        User homer = facade.getUser("homer");
        // a non-admin user is unaffected by the lock
        assertDoesNotThrow(() -> operator.changePassword(homer, new char[0], "newpw".toCharArray()));
    }

    @Test
    public void passwordChangeRequiredOnlyWhenEmptyAndNotFixed() throws Exception
    {
        User homer = facade.getUser("homer");
        // homer has a real password ("duffs") → no nag
        org.junit.jupiter.api.Assertions.assertFalse(operator.isPasswordChangeRequired(homer));

        // set it empty → nag required
        operator.changePassword(homer, new char[0], new char[0]);
        org.junit.jupiter.api.Assertions.assertTrue(operator.isPasswordChangeRequired(facade.getUser("homer")));

        // the fix-admin-password-locked admin is never nagged...
        User admin = createAdmin();
        operator.setFixAdminPassword(true);
        org.junit.jupiter.api.Assertions.assertFalse(operator.isPasswordChangeRequired(admin));
        // ...but an unlocked empty admin is
        operator.setFixAdminPassword(false);
        org.junit.jupiter.api.Assertions.assertTrue(operator.isPasswordChangeRequired(facade.getUser("admin")));
    }
}
