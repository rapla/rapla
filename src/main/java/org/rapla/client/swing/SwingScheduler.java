package org.rapla.client.swing;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Command;
import org.rapla.scheduler.CommandScheduler;

@DefaultImplementation(of = CommandScheduler.class,context = InjectionContext.swing)
@Singleton
public class SwingScheduler extends DefaultScheduler
{
    @Inject
    public SwingScheduler(Logger logger)
    {
        super(logger, 3);
    }

    @Override protected Runnable createTask(final Command command)
    {
        Runnable timerTask = new Runnable()
        {
            public void run()
            {
                Runnable runnable = SwingScheduler.super.createTask(command);
                javax.swing.SwingUtilities.invokeLater(runnable);
            }

            public String toString()
            {
                return command.toString();
            }
        };
        return timerTask;
    }
}
