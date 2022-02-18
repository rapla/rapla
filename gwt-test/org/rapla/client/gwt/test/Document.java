package org.rapla.client.gwt.test;

import jsinterop.annotations.JsType;

/**
 * Created by Christopher on 26.08.2015.
 */
@JsType(isNative = true)
public interface Document {
    <T> T getElementById(String id);

    class Util{
        static public native <T> T getDocument() /*-{
            return $doc;
        }-*/;

        static public native void js(String code)
            /*-{
                //alert(mylib.Reservation.Meaning)
            }-*/;
    }


}
