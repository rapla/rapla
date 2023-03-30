package org.rapla.client.gwt;


@JsType(isNative = true,namespace = "rapla",name = "RaplaCallback")
public class RaplaCallback
{
    public native void gwtLoaded(GwtStarter starter);
}
