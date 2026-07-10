package org.rapla.server.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.server.IdentityClaims;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * security-audit A0c / PRD 036 — the {@code auto-provision} gate. When a provider has
 * auto-provisioning disabled, an unknown external identity must be rejected rather than
 * silently materialised into a new rapla user. An existing user is still resolved.
 *
 * <p>Before the fix the {@code auto-provision=false} property was bound but never read, so
 * every verified external account still got a rapla user regardless of the setting.
 */
class AutoProvisionGateTest extends FacadeTestSupport
{
    private DefaultUserProvisioner provisioner;

    @BeforeEach
    void setUp()
    {
        provisioner = new DefaultUserProvisioner(operator);
    }

    private static IdentityClaims claimsFor(String username)
    {
        return new IdentityClaims(username, "Some One", username + "@example.com", "test-idp", List.of());
    }

    @Test
    void unknownUser_withAutoProvisionOff_isRejected()
    {
        assertThrows(RaplaSecurityException.class,
                () -> provisioner.provision(claimsFor("nobody@example.com"), false));
    }

    @Test
    void unknownUser_withAutoProvisionOn_isCreated() throws Exception
    {
        User created = provisioner.provision(claimsFor("newhire@example.com"), true);
        assertNotNull(created, "auto-provision on must still create the user");
    }

    @Test
    void existingUser_withAutoProvisionOff_isStillResolved()
    {
        // 'homer' exists in the testdefault fixture — disabling auto-provision must not
        // block an already-known identity from resolving/updating.
        assertDoesNotThrow(() -> provisioner.provision(claimsFor("homer"), false));
    }
}
