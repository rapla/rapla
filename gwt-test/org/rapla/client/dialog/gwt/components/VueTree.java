package org.rapla.client.dialog.gwt.components;


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
