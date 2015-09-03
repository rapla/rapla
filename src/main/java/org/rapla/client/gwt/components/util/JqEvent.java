package org.rapla.client.gwt.components.util;

import com.google.gwt.core.client.js.JsProperty;
import com.google.gwt.core.client.js.JsType;

@JsType(prototype = "jQuery.Event")
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