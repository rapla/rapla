package org.rapla.client.gwt;

import io.reactivex.functions.Action;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsType;

@JsType
public class GwtActionWrapper implements Action
{
    @JsFunction
    @FunctionalInterface
    interface Runnable
    {
        void run();
    }

    public Runnable getRunnable()
    {
        return runnable;
    }

    public void setRunnable(Runnable runnable)
    {
        this.runnable = runnable;
    }

    private Runnable runnable;

    @Override
    public void run() throws Exception
    {
        nativeConsoleLog("Callback activated!");
        if ( runnable != null)
        {
            nativeConsoleLog("calling Callback!");
            runnable.run();
            nativeConsoleLog("Callback called!");
        }
    }

    private static native void nativeConsoleLog( String s )
        /*-{ console.log( s ); }-*/;
}
