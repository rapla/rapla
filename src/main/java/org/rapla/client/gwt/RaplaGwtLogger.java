package org.rapla.client.gwt;

import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;

@DefaultImplementation(of= Logger.class,context = InjectionContext.gwt)
public class RaplaGwtLogger extends org.rapla.logger.internal.JavaUtilLoggerForGwt
{
    @Inject
    public RaplaGwtLogger()
    {
        super("rapla");
    }
};
