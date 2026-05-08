package org.rapla.framework.internal;

import org.rapla.framework.Disposable;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.sync.UtilConcurrentCommandScheduler;
import org.rapla.framework.TimeZoneConverter;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@DefaultImplementation(of=CommandScheduler.class,context = {InjectionContext.server})
@Singleton
public class DefaultScheduler extends UtilConcurrentCommandScheduler implements Disposable
{
	final private TimeZoneConverter converter;

	@Inject
	public DefaultScheduler(Logger logger, TimeZoneConverter converter) {
	    this(logger, converter,6);
	}

	public DefaultScheduler(Logger logger) {
		this(logger,new TimeZoneConverterImpl());
	}

	public DefaultScheduler(Logger logger, TimeZoneConverter converter,int poolSize) {
	    super(logger,poolSize);
	    this.converter = converter;
	}

	@Override public void dispose()
	{
		cancel();
	}
}
