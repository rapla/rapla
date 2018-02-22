package org.rapla.client.gwt;

import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;

@DefaultImplementation(of= Logger.class,context = InjectionContext.gwt)
public class RaplaGwtLogger extends org.rapla.logger.ConsoleLogger
{
    @Inject
    public RaplaGwtLogger()
    {
        super("rapla", LEVEL_INFO);
    }

    @Override
    protected void writeln(String message)
    {
        nativeConsoleLog(message);
    }

    private static native void nativeConsoleLog( String s )
        /*-{
        var d = new Date();
        console.log(d.getHours() +":"+d.getMinutes()+":" + d.getSeconds() + "." + d.getMilliseconds() +" "+ s );
        }-*/;
}
