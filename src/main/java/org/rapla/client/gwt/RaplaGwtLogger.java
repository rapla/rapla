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
    protected void writeln(int logLevel,StringBuffer buffer)
    {
        String message = buffer.toString();
        nativeConsoleLog(logLevel,message);
    }

    private static native void nativeConsoleLog( int logLevel,String string )
        /*-{
var d = new Date();
var message = d.getHours() +":"+d.getMinutes()+":" + d.getSeconds() + "." + d.getMilliseconds() +" "+ string ;
switch ( logLevel) {
case -1:
    console.log( message ); break;
case 0:
    console.log( message ); break;
case 1:
    console.info( message ); break;
case 2:
    console.warn( message ); break;
case 3:
case 4:
    console.error( message ); break;
default:
     console.log( message);
   }
        }-*/;
}
