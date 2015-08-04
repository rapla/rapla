package org.rapla.client.gwt;

import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.internal.RaplaJDKLoggingAdapterWithoutClassnameSupport;

import com.google.gwt.inject.client.GinModule;
import com.google.gwt.inject.client.binder.GinBinder;

public class RaplaGWTModule implements GinModule{
    @Override
    public void configure(GinBinder binder) {
        binder.bind(Logger.class).toProvider(RaplaJDKLoggingAdapterWithoutClassnameSupport.class);
    }
    
}

