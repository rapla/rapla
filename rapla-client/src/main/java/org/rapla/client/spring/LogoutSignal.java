package org.rapla.client.spring;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.springframework.stereotype.Service;

/**
 * Cross-boundary signal from a context-internal bean (typically
 * {@code RaplaClientServiceImpl.logout()} / {@code switchTo()}) to the
 * launcher loop in {@link SpringRaplaClient#main(String[])}. Single-slot
 * blocking queue: the bean publishes a {@link NextSession}, the launcher
 * takes it, closes the current context, and either builds the next one or
 * exits the JVM.
 *
 * <p>One {@code LogoutSignal} per Spring context (singleton). A fresh
 * instance exists in each rebuilt context, so the queue's "consumed" state
 * doesn't persist across iterations.
 */
@Service
public class LogoutSignal
{
    private final BlockingQueue<NextSession> queue = new ArrayBlockingQueue<>(1);
    private volatile boolean impersonationSession;

    /** Non-blocking: drop the signal if one is already queued (logout is idempotent). */
    public void next(NextSession ns)
    {
        queue.offer(ns);
    }

    public NextSession take() throws InterruptedException
    {
        return queue.take();
    }

    /**
     * Set by {@link SpringRaplaClient#main(String[])} after building a context
     * that represents an impersonation session (admin previously switched to a
     * non-admin via PRD 051). Reset to {@code false} for fresh logins and
     * switch-back contexts. Read by {@code RaplaClientServiceImpl.canSwitchBack()}
     * so the "Switch back" admin-menu entry only shows in impersonation sessions.
     */
    public void setImpersonationSession(boolean impersonationSession)
    {
        this.impersonationSession = impersonationSession;
    }

    public boolean isImpersonationSession()
    {
        return impersonationSession;
    }
}
