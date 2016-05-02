package org.rapla.framework.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.framework.Disposable;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.impl.UtilConcurrentCommandScheduler;

@DefaultImplementation(of=CommandScheduler.class,context = {InjectionContext.server})
@Singleton
public class DefaultScheduler extends UtilConcurrentCommandScheduler implements Disposable
{
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
	    super(poolSize);
		this.logger = logger;
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