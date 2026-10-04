package org.rapla.client.swing.internal;

import org.junit.Test;
import org.rapla.client.internal.OAuthConfig;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** PRD 126: when a Swing start may skip the password dialog (silent reauth, then browser SSO). */
public class RaplaClientServiceImplAutoSsoTest
{
    private static OAuthConfig cfg(boolean enabled, boolean legacy, boolean ssoButton)
    {
        return new OAuthConfig(enabled, "rapla-client", "http://x/authorize", "http://x/token", null, List.of(), legacy, ssoButton);
    }

    @Test
    public void legacyWithSsoButtonAndLastLoginSso()
    {
        assertTrue(RaplaClientServiceImpl.autoSso(cfg(true, true, true), "sso", false));
    }

    @Test
    public void legacyWithLastLoginPasswordOrNone()
    {
        assertFalse(RaplaClientServiceImpl.autoSso(cfg(true, true, true), "password", false));
        assertFalse(RaplaClientServiceImpl.autoSso(cfg(true, true, true), "", false));
    }

    @Test
    public void legacyAfterExplicitLogoutShowsDialog()
    {
        assertFalse(RaplaClientServiceImpl.autoSso(cfg(true, true, true), "sso", true));
        assertTrue(RaplaClientServiceImpl.autoSso(cfg(true, false, false), "sso", true));
    }

    @Test
    public void legacyWithSsoButtonHidden()
    {
        assertFalse(RaplaClientServiceImpl.autoSso(cfg(true, true, false), "sso", false));
    }

    @Test
    public void nonLegacyIgnoresPreference()
    {
        assertTrue(RaplaClientServiceImpl.autoSso(cfg(true, false, false), "password", false));
        assertTrue(RaplaClientServiceImpl.autoSso(cfg(true, false, false), "", false));
    }

    @Test
    public void oauthDisabledOrNoDiscovery()
    {
        assertFalse(RaplaClientServiceImpl.autoSso(cfg(false, false, true), "sso", false));
        assertFalse(RaplaClientServiceImpl.autoSso(null, "sso", false));
    }

    /** Exit while the browser wait runs interrupts the worker — that is a cancellation, not an error dialog. */
    @Test
    public void exitDuringBrowserWaitIsACancellation()
    {
        assertTrue(RaplaClientServiceImpl.isLoginCancelled(new java.util.concurrent.ExecutionException(new InterruptedException())));
        assertTrue(RaplaClientServiceImpl.isLoginCancelled(new java.util.concurrent.CancellationException()));
        assertTrue(RaplaClientServiceImpl.isLoginCancelled(new java.util.concurrent.CompletionException(new java.util.concurrent.CancellationException())));
        assertFalse(RaplaClientServiceImpl.isLoginCancelled(new java.util.concurrent.ExecutionException(new java.io.IOException("Token exchange failed"))));
    }
}
