package org.rapla.client.gwt.components.util;

import com.google.gwt.core.client.js.JsType;
import com.google.gwt.dom.client.Element;
import jsinterop.annotations.JsPackage;

@jsinterop.annotations.JsType(isNative = true,name = "jQuery",namespace = JsPackage.GLOBAL)
public interface JQueryElement {
    
    public JQueryElement append(JQueryElement... element);

    public String html();

    public String prop(String prop);

    public JQueryElement data(String key, String value);

    public JQueryElement text(String text);

    public JQueryElement[] children(String selector);

    public void remove();

    public JQueryElement addClass(String clazz);

    public JQueryElement removeClass(String clazz);

    public Object val();

    public void on(String event, Function fn);

    public void click(Function fn);

    public JQueryElement attr(String attr, Object value);

    public JQueryElement before(JQueryElement element);

    public JQueryElement prepend(JQueryElement element);

    public void trigger(String select, Object... params);
    
    public static class Static {

        public static native JQueryElement $(String selector) /*-{
         return $wnd.$(selector);
         }-*/;

        public static native JQueryElement $(JQueryElement element) /*-{
         return $wnd.$(element);
         }-*/;

        public static native JQueryElement $(Element element) /*-{
         return $wnd.$(element);
         }-*/;
    }
}
