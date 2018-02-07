package org.rapla.client.gwt;

import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsType;

@JsType
public class GwtConsumerWrapper implements Consumer
{
    @JsFunction
    @FunctionalInterface
    interface Consumer
    {
        void accept(Object accept);
    }

    public Consumer getRunnable()
    {
        return runnable;
    }

    public void setRunnable(Consumer runnable)
    {
        this.runnable = runnable;
    }

    private Consumer runnable;

    @Override
    public void accept(Object t) throws Exception
    {
        if ( runnable != null)
        {
            runnable.accept( t);
        }
    }


}
