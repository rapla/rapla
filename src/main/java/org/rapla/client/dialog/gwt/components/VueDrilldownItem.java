package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

import java.util.ArrayList;
import java.util.List;

@JsType
public class VueDrilldownItem {

  private String id;
  private String label;
  private List<VueDrilldownItem> children = new ArrayList<>();

  @JsIgnore
  public VueDrilldownItem(final String id, final String label) {
    this.id = id;
    this.label = label;
  }

  @JsIgnore
  public VueDrilldownItem addChild(VueDrilldownItem child) {
    this.children.add(child);
    return this;
  }

  public String getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public VueDrilldownItem[] getChildren() {
    return children.toArray(new VueDrilldownItem[] {});
  }
}
