package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

import java.util.ArrayList;
import java.util.List;

@JsType
public class VueDrilldown implements VueComponent {

  private List<VueDrilldownItem> items = new ArrayList<>();

  @JsIgnore
  public VueDrilldown() {
  }

  @JsIgnore
  public VueDrilldown addChild(VueDrilldownItem child) {
    this.items.add(child);
    return this;
  }

  @Override
  public String name() {
    return "BDrilldown";
  }

  @Override
  public VueComponent[] children() {
    return new VueComponent[0];
  }
}
