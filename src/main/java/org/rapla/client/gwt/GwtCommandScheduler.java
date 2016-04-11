package org.rapla.client.gwt;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.framework.Disposable;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.CommandScheduler;

@DefaultImplementation( of = CommandScheduler.class, context = InjectionContext.gwt)
@Singleton
public final class GwtCommandScheduler extends org.rapla.scheduler.client.gwt.GwtCommandScheduler implements Disposable
{
    private final Logger gwtLogger;

    @Inject
    public GwtCommandScheduler(Logger gwtLogger)
    {
        this.gwtLogger = gwtLogger;
    }


    public void dispose()
    {
        // FIXME remove tasks that are canceled
    }


    @Override
    protected void warn(String message, Exception e)
    {
        gwtLogger.warn(message, e);
    }
}