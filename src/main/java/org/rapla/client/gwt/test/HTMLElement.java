package org.rapla.client.gwt.test;

import com.google.gwt.core.client.js.JsProperty;
import com.google.gwt.core.client.js.JsType;


/**
 * Created by Christopher on 26.08.2015.
 */
@JsType(prototype = "HTMLElement")
public interface HTMLElement {

    public void setAttribute(String id, String value);

    public String getAttribute(String id);

    public void appendChild(HTMLElement element);

    @JsProperty
    public void setInnerHTML(String text);

    @JsProperty
    public void setInnerText(String text);

    @JsProperty
    public void setOnclick(EventListener func);

    @JsProperty
    public String getValue();

    void addEventListener(String event, EventListener func, boolean useCapture);
}
