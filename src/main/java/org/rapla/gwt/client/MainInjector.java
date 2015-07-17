package org.rapla.gwt.client;

import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;

@GinModules(value= { RaplaGWTModule.class},properties="extra.ginModules")
public interface MainInjector extends Ginjector {
//    public Application getApplication();
    public Bootstrap getBootstrap();
}
