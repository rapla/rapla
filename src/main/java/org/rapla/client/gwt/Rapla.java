package org.rapla.client.gwt;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
/*
import com.axellience.vuegwt.core.client.Vue;
import com.axellience.vuegwt.core.client.VueGWT;
import org.rapla.client.menu.gwt.SimpleLinkComponent;
import org.rapla.client.menu.gwt.SimpleLinkComponentFactory;
*/
import org.rapla.logger.Logger;

public class Rapla implements EntryPoint
{
    @Override
    public void onModuleLoad()
    {
        GwtStarter starter = GWT.create(GwtStarter.class);
        //VueGWT.initWithoutVueLib();
        Logger logger = new RaplaGwtLogger();
        logger.info("GWT Started. Calling gwtLoaded");
        final RaplaCallback raplaCallback = new RaplaCallback();
        raplaCallback.gwtLoaded(starter);
        //Vue.component(SimpleLinkComponentFactory.get());
        //SimpleLinkComponent simpleLinkComponent = Vue.attach("#simpleLinkComponentContainer", SimpleLinkComponentFactory.get());

    }

}