package org.rapla.client.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.rapla.ConnectInfo;

class LogoutSignalTest
{
    @Test
    void nextThenTake_returnsTheSameSession() throws InterruptedException
    {
        LogoutSignal signal = new LogoutSignal();
        NextSession sent = NextSession.showLoginDialog();
        signal.next(sent);

        NextSession received = signal.take();
        assertSame(sent, received);
    }

    @Test
    void take_blocksUntilNext() throws InterruptedException
    {
        LogoutSignal signal = new LogoutSignal();
        NextSession sent = NextSession.exit();

        // Spawn a worker that signals after a small delay.
        Thread t = new Thread(() -> {
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            signal.next(sent);
        });
        t.start();

        long before = System.nanoTime();
        NextSession received = signal.take();
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        assertSame(sent, received);
        assertTrue(elapsedMs >= 15, "take() should have blocked until next() fired (elapsed=" + elapsedMs + "ms)");
        t.join(1000);
    }

    @Test
    void next_isIdempotentWhenSlotAlreadyFilled()
    {
        LogoutSignal signal = new LogoutSignal();
        signal.next(NextSession.showLoginDialog());
        // A second signal must not block / throw — single-slot queue drops it.
        signal.next(NextSession.exit());
    }

    @Test
    void impersonationSession_flagSetAndRead()
    {
        LogoutSignal signal = new LogoutSignal();
        assertFalse(signal.isImpersonationSession(), "default should be false");
        signal.setImpersonationSession(true);
        assertTrue(signal.isImpersonationSession());
        signal.setImpersonationSession(false);
        assertFalse(signal.isImpersonationSession());
    }

    @Test
    void nextSession_factories_setExpectedShape()
    {
        NextSession dialog = NextSession.showLoginDialog();
        assertNull(dialog.info());
        assertNull(dialog.restoreInfo());
        assertFalse(dialog.isExit());
        assertFalse(dialog.isSwitchBack());

        NextSession exit = NextSession.exit();
        assertTrue(exit.isExit());
        assertFalse(exit.isSwitchBack());
        assertNull(exit.info());
        assertNull(exit.restoreInfo());

        NextSession back = NextSession.switchBack();
        assertTrue(back.isSwitchBack());
        assertFalse(back.isExit());
        assertNull(back.info());
        assertNull(back.restoreInfo());

        ConnectInfo target = ConnectInfo.withAccessToken("imp-token", null);
        ConnectInfo admin = ConnectInfo.withAccessToken("admin-access", "admin-refresh");
        NextSession switchTo = NextSession.switchTo(target, admin);
        assertSame(target, switchTo.info());
        assertSame(admin, switchTo.restoreInfo());
        assertFalse(switchTo.isExit());
        assertFalse(switchTo.isSwitchBack());

        ConnectInfo plain = ConnectInfo.withAccessToken("token", "refresh");
        NextSession reconnect = NextSession.reconnectAs(plain);
        assertSame(plain, reconnect.info());
        assertNull(reconnect.restoreInfo());
        assertFalse(reconnect.isExit());
        assertFalse(reconnect.isSwitchBack());
    }
}
