package org.rapla.framework.internal;

import org.rapla.framework.Disposable;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.impl.UtilConcurrentCommandScheduler;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

@DefaultImplementation(of=CommandScheduler.class,context = {InjectionContext.server})
@Singleton
public class DefaultScheduler extends UtilConcurrentCommandScheduler implements Disposable
{
	private final ScheduledExecutorService executor;
	Logger logger;
	
	protected Logger getLogger() 
	{
        return logger;
    }

	@Inject
	public DefaultScheduler(Logger logger) {
	    this(logger, 6);
	}

	public DefaultScheduler(Logger logger, int poolSize) {
	    this.logger = logger;
		final ScheduledExecutorService executor = Executors.newScheduledThreadPool(poolSize,new ThreadFactory() {
			
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r);
				String name = thread.getName();
				if ( name == null)
				{
					name = "";
				}
				thread.setName("raplascheduler-" + name.toLowerCase().replaceAll("thread", "").replaceAll("-|\\[|\\]", ""));
				thread.setDaemon(true);
				return thread;
			}
		});
		this.executor = executor;
	}

	public void execute(Runnable task)
	{
	   schedule(task, 0 );
	}

	@Override protected void error(String message, Exception ex)
	{
		logger.error( message,ex);
	}

	@Override protected void debug(String message)
	{
		logger.debug( message);
	}

	@Override protected void info(String message)
	{
		logger.info( message);
	}

	@Override protected void warn(String message)
	{
		logger.warn( message);
	}

	@Override public void dispose()
	{
		cancel();
	}
}