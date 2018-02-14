package org.rapla.client.gwt;

import io.reactivex.functions.Consumer;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsType;

@JsType(namespace = "rapla",name = "Consumer")
public class GwtConsumerWrapper implements Consumer
{

    public GwtConsumerWrapper(Consumer consumer)
    {
        this.consumer = consumer;
    }

    @JsFunction
    @FunctionalInterface
    interface Consumer
    {
        void accept(Object accept);
    }

    public Consumer getConsumer()
    {
        return consumer;
    }

    public void setConsumer(Consumer consumer)
    {
        this.consumer = consumer;
    }

    private Consumer consumer;

    @Override
    public void accept(Object t) throws Exception
    {
        if ( consumer != null)
        {
            consumer.accept( t);
        }
    }


}
