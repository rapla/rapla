package org.rapla.client.gwt;

import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;

import javax.inject.Provider;

@GinModules(value= { RaplaGwtExternalInjectionsModule.class },properties="extra.ginModules")
public interface MainInjector extends Ginjector, Provider<Bootstrap>
{
    public Bootstrap get();
}
