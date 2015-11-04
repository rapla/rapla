package org.rapla.client.gwt;

import java.util.List;

import javax.inject.Singleton;

import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.client.EntryPointFactory;
import org.rapla.jsonrpc.client.gwt.AbstractJsonProxy;
import org.rapla.jsonrpc.client.gwt.internal.ExceptionDeserializer;
import org.rapla.storage.dbrm.RaplaExceptionDeserializer;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

import dagger.Component;

public class Rapla implements EntryPoint
{

    @Component(modules = {org.rapla.dagger.DaggerGwtModule.class/*, DaggerStaticModule.class*/})
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