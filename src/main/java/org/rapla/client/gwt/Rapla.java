package org.rapla.client.gwt;

import javax.inject.Singleton;

import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.dagger.DaggerGwtModule;

import com.google.gwt.core.client.EntryPoint;

import dagger.Component;

public class Rapla implements EntryPoint
{

    @Component(modules = {DaggerGwtModule.class, DaggerStaticModule.class})
    @Singleton
    public interface RaplaGwtInjectionStart
    {
        RaplaGwtStarter getStarter();
    }

    public void onModuleLoad()
    {
        RaplaPopups.getProgressBar().setPercent(10);
        //        final MainInjector injector = GWT.create(MainInjector.class);
        //        new RaplaGwtStarter(injector).startApplication();
        final RaplaGwtStarter starter = DaggerRapla_RaplaGwtInjectionStart.create().getStarter();
        starter.startApplication();
    }

}