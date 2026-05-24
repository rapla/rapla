package org.rapla.framework.internal;

import org.rapla.framework.Disposable;
import org.rapla.framework.TimeZoneConverter;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.sync.UtilConcurrentCommandScheduler;
import org.springframework.beans.factory.annotation.Autowired;

public class DefaultScheduler extends UtilConcurrentCommandScheduler implements Disposable
{
	final private TimeZoneConverter converter;

	@Autowired
	public DefaultScheduler(TimeZoneConverter converter) {
	    this(converter, 6);
	}

	public DefaultScheduler() {
		this(new TimeZoneConverterImpl());
	}

	public DefaultScheduler(TimeZoneConverter converter, int poolSize) {
	    super(poolSize);
	    this.converter = converter;
	}

	@Override public void dispose()
	{
		cancel();
	}
}
