package org.rapla.client.gwt;

import org.rapla.framework.Disposable;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.client.gwt.SchedulerImpl;

import javax.inject.Inject;
import javax.inject.Singleton;

@DefaultImplementation( of = CommandScheduler.class, context = InjectionContext.gwt)
@Singleton
public final class GwtCommandScheduler extends org.rapla.scheduler.client.gwt.GwtCommandScheduler implements Disposable
{

    protected  Logger logger;
    @Inject
    public GwtCommandScheduler(Logger logger)
    {
        super(logger);
        this.logger = logger;
    }


    public void dispose()
    {
        // FIXME remove tasks that are canceled
    }

    private void scheduleDeferred(SchedulerImpl.ScheduledCommand cmd)
    {
        SchedulerImpl.get(logger).scheduleDeferred(cmd);
    }


}