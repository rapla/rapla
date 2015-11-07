package org.rapla.client.gwt.components.util;

import jsinterop.annotations.JsFunction;

@JsFunction
public interface Function {
    
    Object call( JqEvent event, Object... params );

}