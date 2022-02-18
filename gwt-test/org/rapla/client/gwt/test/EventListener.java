package org.rapla.client.gwt.test;

import jsinterop.annotations.JsType;

/**
 * Created by Christopher on 26.08.2015.
 */
@JsType(isNative = true)
public interface EventListener {
    void handleEvent(Event event);
}
