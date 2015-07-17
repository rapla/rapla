package org.rapla.gwt.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class Rapla implements EntryPoint {
    public Rapla()
    {
    }
  
    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        final MainInjector injector = GWT.create(MainInjector.class);
        Bootstrap bootstrap = injector.getBootstrap();
        bootstrap.start();
    }

}