package org.rapla.client.gwt.components.util;

import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

@JsType(name="Event",namespace = "jQuery")
public interface JqEvent
{

    @JsProperty
    Object getData();

    @JsProperty
    Element getCurrentTarget();

    @JsProperty
    Element getDelegateTarget();

    @JsProperty
    Object getResult();

    @JsProperty
    String getType();

    @JsProperty
    String getMetaKey();

    @JsProperty
    long getTimeStamp();

    @JsProperty
    JQueryElement getRelatedTarget();

    @JsProperty
    String getNamespace();

    void stopPropagation();

    void stopImmediatePropagation();

    void preventDefault();

    boolean isPropagationStopped();

    boolean isDefaultPrevented();

    boolean isImmediatePropagationStopped();

}