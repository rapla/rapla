package org.rapla.gwt.client;

import java.util.Date;

import org.rapla.components.util.DateTools;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

public class Rapla implements EntryPoint
{

    @Override
    public void onModuleLoad()
    {
        //        GWT.log("da: " + new TimeInterval(new Date(), new Date(System.currentTimeMillis() + 1000)));
        GWT.log("Hallo" + DateTools.formatDate(new Date()));
    }

}
