package org.rapla.client.gwt;

import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.logger.internal.RaplaJDKLoggingAdapterForGwt;

import javax.inject.Inject;

@DefaultImplementation(of= Logger.class,context = InjectionContext.gwt)
public class RaplaGwtLogger extends RaplaJDKLoggingAdapterForGwt
{
    @Inject
    public RaplaGwtLogger()
    {
        super("rapla");
    }
}
