package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.storage.RaplaSecurityException;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * security-audit A0c / PRD 051 — the {@code rapla.auth.impersonation.enabled=false} kill
 * switch. When impersonation is disabled, both the Bearer endpoint
 * ({@link ImpersonationController#impersonate}) and the SPA cookie endpoint
 * ({@link AuthCookieController#impersonateSwitch}) must refuse before doing any work.
 *
 * <p>The guard is the first statement in each method, so the collaborators are never
 * touched — passing {@code null} for them exercises only the kill-switch branch.
 */
class ImpersonationKillSwitchTest
{
    @Test
    void bearerImpersonate_disabled_throws()
    {
        ImpersonationController controller =
                new ImpersonationController(null, null, null, null, false);
        assertThrows(RaplaSecurityException.class, () -> controller.impersonate("monty"));
    }

    @Test
    void cookieImpersonateSwitch_disabled_throws()
    {
        AuthCookieController controller =
                new AuthCookieController(null, null, null, null, null, null, null, false);
        assertThrows(RaplaSecurityException.class, () -> controller.impersonateSwitch("monty"));
    }
}
