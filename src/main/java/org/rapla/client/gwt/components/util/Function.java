package org.rapla.client.gwt.components.util;

import com.google.gwt.core.client.js.JsFunction;

@JsFunction
public interface Function {
    
    Object call( JqEvent event, Object... params );

}