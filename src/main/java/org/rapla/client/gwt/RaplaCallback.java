package org.rapla.client.gwt;

import jsinterop.annotations.JsType;

@JsType(isNative = true,namespace = "rapla",name = "RaplaCallback")
public class RaplaCallback
{
    public native void callback(Object apiObject);
}
