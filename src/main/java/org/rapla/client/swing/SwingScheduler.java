package org.rapla.client.swing;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

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
