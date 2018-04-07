package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsType;

@JsType
public interface VueComponent {
  
  String name();
  
  VueComponent[] children();
  
}
