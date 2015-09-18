package org.rapla.client.gwt;

import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.inject.client.RaplaGinModules;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;

public class Rapla implements EntryPoint {

    public void onModuleLoad() {
        RaplaPopups.getProgressBar().setPercent(10);
        GWT.create(RaplaGinModules.class);
        final MainInjector injector = GWT.create(MainInjector.class);
        new RaplaGwtStarter(injector).startApplication();
    }

}