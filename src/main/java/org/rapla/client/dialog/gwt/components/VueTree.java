package org.rapla.client.dialog.gwt.components;

import jsinterop.annotations.JsType;

@JsType
public class VueTree implements VueComponent {

  public final VueTreeNode rootNode;

  public VueTree(VueTreeNode root) {
    this.rootNode = root;
  }

  @Override
  public String name() {
    return "vue-tree";
  }

  @Override
  public VueComponent[] children() {
    return new VueComponent[0];
  }
}
