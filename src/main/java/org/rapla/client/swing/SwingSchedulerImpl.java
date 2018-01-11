package org.rapla.client.swing;

import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.sync.UtilConcurrentCommandScheduler;

import javax.inject.Inject;
import javax.inject.Singleton;

@DefaultImplementation(of = CommandScheduler.class, context = InjectionContext.swing)
@Singleton
public class SwingSchedulerImpl extends UtilConcurrentCommandScheduler
{
    @Inject
    public SwingSchedulerImpl(Logger logger)
    {
        super(logger);
    }
}
