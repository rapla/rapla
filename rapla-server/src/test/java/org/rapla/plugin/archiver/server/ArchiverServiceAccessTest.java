package org.rapla.plugin.archiver.server;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * B2 (security): the archiver's destructive operations (delete old events,
 * backup, restore) must only run for admin users. They are gated by
 * {@link ArchiverServiceImpl#checkAccess()}. This test locks the gate — in
 * particular it pins the fail-closed behaviour for a {@code null} session user:
 * before the fix, {@code checkAccess} used {@code if (user != null &&
 * !user.isAdmin())}, so an unauthenticated/null user slipped through (fail-open).
 */
class ArchiverServiceAccessTest extends FacadeTestSupport
{
    private ArchiverServiceImpl serviceWithSessionUser(User user)
    {
        ArchiverServiceImpl service = new ArchiverServiceImpl(null);
        service.session = new RemoteSession()
        {
            @Override
            public User checkAndGetUser(HttpServletRequest request)
            {
                return user;
            }

            @Override
            public boolean isAuthentified(HttpServletRequest request)
            {
                return user != null;
            }

            @Override
            public void logout()
            {
            }
        };
        return service;
    }

    private User user(String username) throws RaplaException
    {
        for (User u : facade.getUsers())
        {
            if (username.equals(u.getUsername()))
            {
                return u;
            }
        }
        throw new IllegalStateException("fixture lacks user " + username);
    }

    @Test
    void anonymousNullUserIsRejected()
    {
        assertThrows(RaplaSecurityException.class,
                () -> serviceWithSessionUser(null).checkAccess(),
                "fail-closed: a null session user must not be allowed to trigger archiver ops");
    }

    @Test
    void nonAdminUserIsRejected() throws Exception
    {
        assertThrows(RaplaSecurityException.class,
                () -> serviceWithSessionUser(user("monty")).checkAccess());
    }

    @Test
    void adminUserIsAllowed() throws Exception
    {
        assertDoesNotThrow(() -> serviceWithSessionUser(user("homer")).checkAccess());
    }
}
