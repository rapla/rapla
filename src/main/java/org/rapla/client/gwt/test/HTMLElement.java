package org.rapla.client.gwt.test;

import com.google.gwt.core.client.js.JsProperty;
import com.google.gwt.core.client.js.JsType;


/**
 * Created by Christopher on 26.08.2015.
 */
@JsType(prototype = "HTMLElement")
public interface HTMLElement {

    void setAttribute(String id, String value);

    String getAttribute(String id);

    void appendChild(HTMLElement element);

    @JsProperty void setInnerHTML(String text);

    @JsProperty void setInnerText(String text);

    @JsProperty void setOnclick(EventListener func);

    @JsProperty String getValue();

    void addEventListener(String event, EventListener func, boolean useCapture);
}
