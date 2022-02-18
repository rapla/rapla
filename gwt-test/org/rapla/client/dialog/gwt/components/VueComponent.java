package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsType;

@JsType
public interface VueComponent {

  /**
   * name of the vue component in kebap-case<br>
   * vue component must be available in src/components/DynamicDialog/DialogDynamic.js
   */
  String name();

  VueComponent[] children();

}
