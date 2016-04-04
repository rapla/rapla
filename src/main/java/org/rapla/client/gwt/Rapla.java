package org.rapla.client.gwt;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.client.EntryPointFactory;
import org.rapla.jsonrpc.client.gwt.AbstractJsonProxy;
import org.rapla.jsonrpc.common.ExceptionDeserializer;
import org.rapla.storage.dbrm.RaplaExceptionDeserializer;

import java.util.List;

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