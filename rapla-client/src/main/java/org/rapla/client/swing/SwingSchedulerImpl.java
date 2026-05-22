package org.rapla.client.swing;

import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.sync.UtilConcurrentCommandScheduler;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Primary
public class SwingSchedulerImpl extends UtilConcurrentCommandScheduler implements DisposableBean
{
    @Autowired
    public SwingSchedulerImpl(Logger logger)
    {
        super(logger);
    }

    /**
     * PRD 052 Phase 2 — when SpringRaplaClient.main()'s loop closes this
     * context, shut down the scheduler's executor so queued tasks don't
     * keep running with stale references to the (now-destroyed) beans of
     * the old context. Without this, the next iteration sees harmless but
     * noisy "BeanCreationException: no user loged in" errors fired from
     * lingering scheduler threads against the dying facade.
     */
    @Override
    public void destroy()
    {
        cancel();
    }
}
