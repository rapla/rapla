package org.rapla.client.gwt;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.framework.Disposable;
import org.rapla.function.Command;
import org.rapla.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Cancelable;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.client.gwt.SchedulerImpl;

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


    @Override
    public Cancelable schedule(Command command, long delay)
    {
        SchedulerImpl.ScheduledCommand entry = new SchedulerImpl.ScheduledCommand()
        {

            @Override
            public void execute()
            {
                try
                {
                    //gwtLogger.info("Refreshing client without period ");
                    command.execute();
                }
                catch (Exception e)
                {
                    warn(e.getMessage(), e);
                }

            }
        };
        scheduleDeferred(entry);
        return new Cancelable()
        {

            public void cancel()
            {
            }
        };
    }


}