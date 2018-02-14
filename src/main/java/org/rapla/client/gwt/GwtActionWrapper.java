package org.rapla.client.gwt;

import io.reactivex.functions.Action;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsType;

@JsType(namespace = "rapla",name = "Action")
public class GwtActionWrapper implements Action
{
    public GwtActionWrapper(Runnable runnable)
    {
        this.runnable = runnable;
    }
    @JsFunction
    @FunctionalInterface
    interface Runnable
    {
        void run();
    }

    private Runnable runnable;

    @Override
    public void run() throws Exception
    {
        if ( runnable != null)
        {
            runnable.run();
        }
    }
}
