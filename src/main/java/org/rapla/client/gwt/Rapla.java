package org.rapla.client.gwt;

import java.util.List;

import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.framework.RaplaException;
import org.rapla.rest.client.EntryPointFactory;
import org.rapla.rest.client.ExceptionDeserializer;
import org.rapla.rest.client.gwt.AbstractJsonProxy;
import org.rapla.storage.dbrm.RaplaExceptionDeserializer;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

public class Rapla implements EntryPoint
{

    private void setProxy()
    {
        AbstractJsonProxy.setServiceEntryPointFactory(new EntryPointFactory()
        {
            @Override public String getEntryPoint(String interfaceName, String relativePath)
            {
                String url = GWT.getModuleBaseURL() + "../rapla/" + (relativePath != null ? relativePath : interfaceName);
                return url;
            }
        });
        AbstractJsonProxy.setExceptionDeserializer(new ExceptionDeserializer()
        {
            @Override
            public Exception deserializeException(String exception, String message, List<String> parameter)
            {
                final RaplaExceptionDeserializer raplaExceptionDeserializer = new RaplaExceptionDeserializer();
                final RaplaException deserializedException = raplaExceptionDeserializer.deserializeException(exception, message, parameter);
                return deserializedException;
            }
        });
    }

    public void onModuleLoad()
    {
        setProxy();

        RaplaPopups.getProgressBar().setPercent(10);
        GwtStarter starter = GWT.create(GwtStarter.class);
        starter.startApplication();
    }

}