package org.rapla.client.gwt;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import org.rapla.logger.Logger;

public class Rapla implements EntryPoint
{
    @Override
    public void onModuleLoad()
    {
        GwtStarter starter = GWT.create(GwtStarter.class);
        Logger logger = new RaplaGwtLogger();
        logger.info("GWT Started. Calling gwtLoaded");
        new RaplaCallback().gwtLoaded(starter);
    }

}