package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

@JsType
public class VueLabel implements VueComponent {
  
  private final String text;
  private BulmaTextColor color;
  
  public VueLabel(final String text) {
    this.text = text;
  }
  
  @JsIgnore
  public VueLabel color(BulmaTextColor color) {
    this.color = color;
    return this;
  }
  
  public String color() {
    if (color == null)
      return null;
    return color.css();
  }
  
  public String text() {
    return text;
  }
  
  @Override
  public String name() {
    return "BLabel";
  }
  
  @Override
  public VueComponent[] children() {
    return new VueComponent[0];
  }
}
