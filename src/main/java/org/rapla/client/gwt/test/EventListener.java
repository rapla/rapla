package org.rapla.client.gwt.test;

import com.google.gwt.core.client.js.JsType;

/**
 * Created by Christopher on 26.08.2015.
 */
@JsType(prototype = "EventListener")
public interface EventListener {
    void handleEvent(Event event);
}
