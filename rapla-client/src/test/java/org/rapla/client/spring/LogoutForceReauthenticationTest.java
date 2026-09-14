package org.rapla.client.spring;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression for the Swing SSO logout bug: {@code RaplaClientServiceImpl.logout()}
 * used to set a force-{@code prompt=login} flag on a field of its own bean, then
 * immediately trigger the PRD 052 context teardown — the fresh context's bean
 * started with the flag reset, so the next SSO login never sent
 * {@code prompt=login} and the browser's live server session silently
 * re-authenticated the old user. The intent must instead travel through
 * {@link NextSession} (the only carrier that survives the rebuild) into the new
 * context's {@link LogoutSignal}, where the next OAuth flow consumes it once.
 */
public class LogoutForceReauthenticationTest
{
    @Test
    public void showLoginDialogAfterLogoutCarriesForceOauthLogin()
    {
        NextSession afterLogout = NextSession.showLoginDialogAfterLogout();
        assertTrue("logout must carry the force-reauthentication intent across the context rebuild",
                afterLogout.isForceOauthLogin());
        assertNull(afterLogout.info());
        assertFalse(afterLogout.isExit());
        assertFalse(afterLogout.isSwitchBack());
    }

    @Test
    public void plainShowLoginDialogDoesNotForceReauthentication()
    {
        assertFalse("initial JVM launch must keep silent SSO",
                NextSession.showLoginDialog().isForceOauthLogin());
    }

    @Test
    public void logoutSignalFlagIsOneShot()
    {
        LogoutSignal signal = new LogoutSignal();
        assertFalse("fresh context defaults to silent SSO", signal.consumeForceOauthLoginNext());
        signal.setForceOauthLoginNext(true);
        assertTrue("first OAuth flow after logout must force prompt=login",
                signal.consumeForceOauthLoginNext());
        assertFalse("the force flag is one-shot — later flows go back to silent SSO",
                signal.consumeForceOauthLoginNext());
    }
}
