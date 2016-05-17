package org.rapla.framework.internal;

import org.rapla.framework.Disposable;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.impl.UtilConcurrentCommandScheduler;

import javax.inject.Inject;
import javax.inject.Singleton;

@DefaultImplementation(of=CommandScheduler.class,context = {InjectionContext.server})
@Singleton
public class DefaultScheduler extends UtilConcurrentCommandScheduler implements Disposable
{
	@Inject
	public DefaultScheduler(Logger logger) {
	    this(logger, 6);
	}

	public DefaultScheduler(Logger logger, int poolSize) {
	    super(logger,poolSize);
	}

	@Override public void dispose()
	{
		cancel();
	}
}