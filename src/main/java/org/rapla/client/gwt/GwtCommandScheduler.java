package org.rapla.client.gwt;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation( of = CommandScheduler.class, context = InjectionContext.gwt)
final class GwtCommandScheduler implements CommandScheduler
{
    private final Logger gwtLogger;

    @Inject GwtCommandScheduler(Logger gwtLogger)
    {
        this.gwtLogger = gwtLogger;
    }

    @Override
    public Cancelable schedule(final Command command, long delay, final long period)
    {
        if (period > 0)
        {
            RepeatingCommand cmd = new RepeatingCommand()
            {

                @Override
                public boolean execute()
                {
                    try
                    {
                        //gwtLogger.info("Refreshing client with period " + period);
                        command.execute();
                    }
                    catch (Exception e)
                    {
                        gwtLogger.warn(e.getMessage(), e);
                    }
                    return true;
                }
            };
            Scheduler.get().scheduleFixedPeriod(cmd, (int) period);
        }
        else
        {
            ScheduledCommand entry = new ScheduledCommand()
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
                        gwtLogger.warn(e.getMessage(), e);
                    }

                }
            };
            Scheduler.get().scheduleEntry(entry);
        }

        return new Cancelable()
        {

            public void cancel()
            {
            }
        };
    }

    @Override
    public Cancelable schedule(Command command, long delay)
    {
        return schedule(command, delay, -1);
    }

    @Override
    public Cancelable scheduleSynchronized(Object synchronizationObject, Command command, long delay)
    {
        return schedule(command, delay);
    }
}