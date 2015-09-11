package org.rapla.client.gwt;

import org.rapla.inject.client.RaplaGinModules;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

public class Rapla implements EntryPoint {

    public void onModuleLoad() {
        GWT.create(RaplaGinModules.class);
        final MainInjector injector = GWT.create(MainInjector.class);
        new RaplaGwtStarter(injector).startApplication();
    }

}