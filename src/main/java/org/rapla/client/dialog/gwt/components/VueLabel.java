package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

@JsType
public class VueLabel implements VueComponent {

  public final String text;
  public String color;

  @JsIgnore
  public VueLabel(final String text) {
    this.text = text;
  }

  @JsIgnore
  public VueLabel color(BulmaTextColor color) {
    this.color = color.css();
    return this;
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
