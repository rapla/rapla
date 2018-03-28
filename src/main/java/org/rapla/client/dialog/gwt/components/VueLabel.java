package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsType;

@JsType
public class VueLabel implements VueComponent {
  
  private final String text;
  
  public VueLabel(final String text) {
    this.text = text;
  }
  
  public String getText() {
    return text;
  }
  
  @Override
  public String name() {
    return "BLabel";
  }
}
