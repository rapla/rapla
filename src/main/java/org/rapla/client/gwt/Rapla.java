package org.rapla.client.gwt;

import javax.inject.Singleton;

import com.google.gwt.core.client.GWT;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.dagger.DaggerGwtModule;

import com.google.gwt.core.client.EntryPoint;

import dagger.Component;
import org.rapla.framework.RaplaException;
import org.rapla.gwtjsonrpc.client.ExceptionDeserializer;
import org.rapla.gwtjsonrpc.client.impl.AbstractJsonProxy;
import org.rapla.gwtjsonrpc.client.impl.EntryPointFactory;
import org.rapla.storage.dbrm.RaplaExceptionDeserializer;

import java.util.List;

public class Rapla implements EntryPoint
{

    @Component(modules = {DaggerGwtModule.class/*, DaggerStaticModule.class*/})
    @Singleton
    public interface RaplaGwtInjectionStart
    {
        RaplaGwtStarter getStarter();
    }

    private void setProxy()
    {
        AbstractJsonProxy.setServiceEntryPointFactory(new EntryPointFactory()
        {
            @Override public String getEntryPoint(String interfaceName, String relativePath)
            {
                String url = GWT.getModuleBaseURL() + "../rapla/json/" + (relativePath != null ? relativePath : interfaceName);
                return url;
            }
        });
        AbstractJsonProxy.setExceptionDeserializer(new ExceptionDeserializer()
        {
            @Override
            public Exception deserialize(String exception, String message, List<String> parameter)
            {
                final RaplaExceptionDeserializer raplaExceptionDeserializer = new RaplaExceptionDeserializer();
                final RaplaException deserializedException = raplaExceptionDeserializer.deserializeException(exception, message, parameter);
                return deserializedException;
            }
        });
    }

    public void onModuleLoad()
    {
        GWT.log("starting rapla");
        setProxy();

        RaplaPopups.getProgressBar().setPercent(10);
        //        final MainInjector injector = GWT.create(MainInjector.class);
        //        new RaplaGwtStarter(injector).startApplication();
        final RaplaGwtStarter starter = DaggerRapla_RaplaGwtInjectionStart.create().getStarter();
        starter.startApplication();
    }

}