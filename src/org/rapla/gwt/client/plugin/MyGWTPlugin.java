package org.rapla.gwt.client.plugin;

import org.rapla.gwt.client.Greeter;

public class MyGWTPlugin implements Greeter
{

    @Override
    public String greet()
    {
        return "Hello Rapla";
    }

}
