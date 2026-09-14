package org.rapla.server.spring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * B4 (security): when {@code rapla.password-check-disabled=true} the server must
 * refuse to boot unless it binds a loopback address — otherwise a credential-free
 * admin login is exposed to the network.
 */
class PasswordCheckBindingGuardTest
{
    @Test
    void disabledWithUnsetAddressIsRejected()
    {
        // unset server.address ⇒ binds all interfaces (0.0.0.0) ⇒ exposed
        assertThrows(IllegalStateException.class,
                () -> PasswordCheckBindingGuard.validate(true, null));
        assertThrows(IllegalStateException.class,
                () -> PasswordCheckBindingGuard.validate(true, ""));
    }

    @Test
    void disabledWithAllInterfacesIsRejected()
    {
        assertThrows(IllegalStateException.class,
                () -> PasswordCheckBindingGuard.validate(true, "0.0.0.0"));
        assertThrows(IllegalStateException.class,
                () -> PasswordCheckBindingGuard.validate(true, "::"));
    }

    @Test
    void disabledWithNonLoopbackHostIsRejected()
    {
        assertThrows(IllegalStateException.class,
                () -> PasswordCheckBindingGuard.validate(true, "192.168.1.50"));
    }

    @Test
    void disabledWithLoopbackIsAllowed()
    {
        assertDoesNotThrow(() -> PasswordCheckBindingGuard.validate(true, "127.0.0.1"));
        assertDoesNotThrow(() -> PasswordCheckBindingGuard.validate(true, "localhost"));
        assertDoesNotThrow(() -> PasswordCheckBindingGuard.validate(true, "::1"));
    }

    @Test
    void enabledPasswordCheckIsAlwaysAllowed()
    {
        // guard is inert when password verification is on — bind address irrelevant
        assertDoesNotThrow(() -> PasswordCheckBindingGuard.validate(false, null));
        assertDoesNotThrow(() -> PasswordCheckBindingGuard.validate(false, "0.0.0.0"));
    }
}
