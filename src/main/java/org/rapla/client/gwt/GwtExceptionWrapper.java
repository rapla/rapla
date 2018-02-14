package org.rapla.client.gwt;

import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsType;

@JsType(namespace = "rapla",name = "Catch")
public class GwtExceptionWrapper implements Function<Throwable, Object>
{
    private Consumer consumer;

    public GwtExceptionWrapper(Consumer consumer)
    {
        this.consumer = consumer;
    }

    @Override
    public Object apply(Throwable throwable) throws Exception
    {
         consumer.accept( throwable);
         return null;
    }

    @JsFunction
    @FunctionalInterface
    interface Consumer
    {
        void accept(Object accept);
    }






}
